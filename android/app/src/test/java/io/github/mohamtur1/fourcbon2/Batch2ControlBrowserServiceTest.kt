package io.github.mohamtur1.fourcbon2

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Batch2ControlBrowserServiceTest {
    private fun tempDir(prefix: String): File = Files.createTempDirectory(prefix).toFile()

    // Divisions 12 & 13: Selected-App Capability Registry & On-Device Scope Contract
    @Test
    fun capabilityRegistryStartsOffBlocksProtectedAppsAndEnforcesScopeLimits() {
        val dir = tempDir("4cbon2_scope_test")
        try {
            val db = FourCBOn2Database(DiskDirectoryBackend(dir))
            val registry = SelectedAppCapabilityRegistry(db).also { it.ensureDefaultCatalog(100L) }
            val broker = OnDeviceScopeBroker(db, registry)

            // All apps start unselected (Off)
            assertTrue(registry.listApps().all { !it.ownerSelected })

            // Protected system settings cannot be turned On for automation
            val attemptedSettings = registry.setOwnerSelected("com.android.settings", true, 200L)!!
            assertFalse(attemptedSettings.ownerSelected)

            // Create scope allowing Gmail draft share (max 1 action, no cloud transfer)
            val contract = broker.createContract(
                taskTitle = "Share One Draft",
                allowedPackages = setOf("com.google.android.gm"),
                allowedActions = setOf(ScopedActionType.SHARE_TEXT_DRAFT),
                allowedAccounts = setOf("owner@example.com"),
                allowedDataRefs = setOf("file_1"),
                allowCloudTransfer = false,
                maxActionCount = 1,
                ttlMillis = 10_000L,
                nowEpochMs = 1_000L,
            )

            // Denied while Gmail is still unselected in registry
            val beforeSelect = broker.authorizeAndConsume(
                ProposedScopedAction(
                    contractId = contract.id,
                    targetPackage = "com.google.android.gm",
                    accountId = "owner@example.com",
                    actionType = ScopedActionType.SHARE_TEXT_DRAFT,
                    dataRef = "file_1",
                ),
                nowEpochMs = 1_500L,
            )
            assertTrue(beforeSelect is ScopeDecision.Denied)

            // Owner selects Gmail -> permitted once, then capped on second call
            registry.setOwnerSelected("com.google.android.gm", true, 1_600L)

            // Unauthorized cloud transfer denied
            val cloudAttempt = broker.authorizeAndConsume(
                ProposedScopedAction(
                    contractId = contract.id,
                    targetPackage = "com.google.android.gm",
                    accountId = "owner@example.com",
                    actionType = ScopedActionType.SHARE_TEXT_DRAFT,
                    dataRef = "file_1",
                    transfersDataToCloud = true,
                ),
                nowEpochMs = 1_700L,
            )
            assertTrue(cloudAttempt is ScopeDecision.Denied)

            // Valid scoped action succeeds
            val allowed = broker.authorizeAndConsume(
                ProposedScopedAction(
                    contractId = contract.id,
                    targetPackage = "com.google.android.gm",
                    accountId = "owner@example.com",
                    actionType = ScopedActionType.SHARE_TEXT_DRAFT,
                    dataRef = "file_1",
                    transfersDataToCloud = false,
                ),
                nowEpochMs = 1_800L,
            )
            assertTrue(allowed is ScopeDecision.Permitted)

            // Second attempt exceeds maxActionCount = 1
            val capped = broker.authorizeAndConsume(
                ProposedScopedAction(
                    contractId = contract.id,
                    targetPackage = "com.google.android.gm",
                    accountId = "owner@example.com",
                    actionType = ScopedActionType.SHARE_TEXT_DRAFT,
                    dataRef = "file_1",
                ),
                nowEpochMs = 1_900L,
            )
            assertTrue(capped is ScopeDecision.Denied)
        } finally {
            dir.deleteRecursively()
        }
    }

    // Divisions 14, 21, 27: Takeover Lease, Harmless App Intent, and Actual Action Timeline
    @Test
    fun takeoverLeaseImmediatelyRejectsStaleCommandsAndRecordsHonestTimeline() {
        val dir = tempDir("4cbon2_lease_intent_test")
        try {
            val container = FourCBOn2WorkspaceContainer.createForTest(dir)
            container.capabilityRegistry.setOwnerSelected("com.google.android.gm", true, 100L)

            val activeLease = container.takeoverLease.startOrResumeAutomation(
                sessionId = "sess_alpha",
                explicitOwnerResume = true,
                nowEpochMs = 200L,
            )
            val contract = container.scopeBroker.createContract(
                taskTitle = "Draft Handoff",
                allowedPackages = setOf("com.google.android.gm"),
                allowedActions = setOf(ScopedActionType.SHARE_TEXT_DRAFT),
                maxActionCount = 5,
                nowEpochMs = 200L,
            )

            // Valid intent handoff succeeds and logs MANUAL_HANDOFF
            val ok = container.intentExecutor.execute(
                sessionId = "sess_alpha",
                commandEpoch = activeLease.leaseEpoch,
                scopeContractId = contract.id,
                spec = HarmlessIntentSpec.SharePlainTextDraft(
                    targetPackage = "com.google.android.gm",
                    subject = "Report",
                    draftBody = "Hello",
                ),
                nowEpochMs = 300L,
            )
            assertTrue(ok is IntentDispatchResult.HandedOffToApp)

            // Owner triggers Take Over -> stale epoch is immediately rejected
            container.takeoverLease.takeOver(nowEpochMs = 400L)
            val staleAttempt = container.intentExecutor.execute(
                sessionId = "sess_alpha",
                commandEpoch = activeLease.leaseEpoch,
                scopeContractId = contract.id,
                spec = HarmlessIntentSpec.SharePlainTextDraft(
                    targetPackage = "com.google.android.gm",
                    subject = "Late Remote Step",
                    draftBody = "Should not run",
                ),
                nowEpochMs = 450L,
            )
            assertTrue(staleAttempt is IntentDispatchResult.BlockedByLease)

            // Non-owner resume is rejected
            var resumeBlocked = false
            try {
                container.takeoverLease.startOrResumeAutomation(
                    sessionId = "sess_beta",
                    explicitOwnerResume = false,
                )
            } catch (_: IllegalStateException) {
                resumeBlocked = true
            }
            assertTrue(resumeBlocked)

            val events = container.actionTimeline.listEvents()
            assertTrue(events.any { it.outcome == TimelineOutcomeState.MANUAL_HANDOFF })
            assertTrue(events.any { it.outcome == TimelineOutcomeState.BLOCKED })
        } finally {
            dir.deleteRecursively()
        }
    }

    // Divisions 20, 26, 32: Approval Gate, Observation Boundary, and Critical Phone-Action Guard
    @Test
    fun criticalGuardObservationBoundaryAndApprovalGateEnforceSafetyRules() {
        val dir = tempDir("4cbon2_guards_test")
        try {
            val db = FourCBOn2Database(DiskDirectoryBackend(dir))
            val gate = SensitiveActionApprovalGate(db)
            val guard = CriticalPhoneActionGuard(db, gate)
            val obs = ConsentedObservationBoundary(db)

            // Division 26: Screenshot blocked by default; secrets redacted in structured text; sensitive screens blocked
            assertTrue(
                obs.observe(ObservationChannel.OPT_IN_SCREEN_CAPTURE, "screen text") is ObservationOutcome.Blocked,
            )
            assertTrue(
                obs.observe(
                    ObservationChannel.STRUCTURED_API_OR_INTENT_RESULT,
                    "otp 123456",
                    sensitivity = ScreenSensitivity.AUTHENTICATION_OR_OTP,
                ) is ObservationOutcome.Blocked,
            )
            assertTrue(
                obs.observe(
                    ObservationChannel.STRUCTURED_API_OR_INTENT_RESULT,
                    "safe data",
                    requestCloudUpload = true,
                ) is ObservationOutcome.Blocked,
            )
            val captured = obs.observe(
                ObservationChannel.STRUCTURED_API_OR_INTENT_RESULT,
                "Token github_pat_1234567890abcdef and KGAT_secret123456789",
            ) as ObservationOutcome.Captured
            assertFalse(captured.redactedContent.contains("github_pat_"))
            assertFalse(captured.redactedContent.contains("KGAT_"))

            // Division 32: Banking/auth/permissions ALWAYS require manual handoff
            val bankCheck = guard.evaluate(
                CriticalActionRequest(
                    idempotencyKey = "bank_1",
                    riskContext = ActionRiskContext.BANKING_OR_PAYMENT,
                    targetAppOrService = "com.example.bank",
                    activeAccount = "owner",
                    actionSummary = "Pay",
                    payloadSha256 = "abc",
                    previewShownToOwner = null,
                ),
            )
            assertTrue(bankCheck is CriticalGuardDecision.ManualHandoffRequired)

            // Consequential external write requires matching preview + confirmed single-use grant + blocks blind retry
            val grant = gate.requestApproval(
                actionType = "Submit Predictions",
                destinationIdentity = "kaggle:tabular-playground",
                artifactSha256 = "deadbeef01",
                maxUses = 1,
                ttlMillis = 60_000L,
                nowEpochMs = 1_000L,
            )
            gate.confirmByOwner(grant.id, nowEpochMs = 1_100L)

            val preview = ActionPreview(
                targetAppOrService = "kaggle:tabular-playground",
                expectedAccount = "mohamtur1",
                actionSummary = "Submit Predictions",
                payloadSha256 = "deadbeef01",
            )

            // Wrong account is blocked without consuming the grant
            val wrongAccount = guard.evaluate(
                CriticalActionRequest(
                    idempotencyKey = "sub_1",
                    riskContext = ActionRiskContext.CONSEQUENTIAL_EXTERNAL_WRITE_OR_SUBMIT,
                    targetAppOrService = "kaggle:tabular-playground",
                    activeAccount = "other_account",
                    actionSummary = "Submit Predictions",
                    payloadSha256 = "deadbeef01",
                    previewShownToOwner = preview,
                    approvalGrantId = grant.id,
                ),
                nowEpochMs = 1_200L,
            )
            assertTrue(wrongAccount is CriticalGuardDecision.Blocked)

            // Exact matching call succeeds once
            val permitted = guard.evaluate(
                CriticalActionRequest(
                    idempotencyKey = "sub_1",
                    riskContext = ActionRiskContext.CONSEQUENTIAL_EXTERNAL_WRITE_OR_SUBMIT,
                    targetAppOrService = "kaggle:tabular-playground",
                    activeAccount = "mohamtur1",
                    actionSummary = "Submit Predictions",
                    payloadSha256 = "deadbeef01",
                    previewShownToOwner = preview,
                    approvalGrantId = grant.id,
                ),
                nowEpochMs = 1_300L,
            )
            assertEquals(CriticalGuardDecision.Permitted, permitted)

            // Blind duplicate retry with same idempotencyKey is blocked
            val duplicateRetry = guard.evaluate(
                CriticalActionRequest(
                    idempotencyKey = "sub_1",
                    riskContext = ActionRiskContext.CONSEQUENTIAL_EXTERNAL_WRITE_OR_SUBMIT,
                    targetAppOrService = "kaggle:tabular-playground",
                    activeAccount = "mohamtur1",
                    actionSummary = "Submit Predictions",
                    payloadSha256 = "deadbeef01",
                    previewShownToOwner = preview,
                    approvalGrantId = grant.id,
                ),
                nowEpochMs = 1_400L,
            )
            assertTrue(duplicateRetry is CriticalGuardDecision.Blocked)
        } finally {
            dir.deleteRecursively()
        }
    }

    // Divisions 22 & 23: GitHub Cache/Diff and Kaggle Metadata/Stale Status
    @Test
    fun githubDiffAndKaggleStatusBehaveDeterministicallyWithoutUnapprovedWrites() {
        val dir = tempDir("4cbon2_service_cache_test")
        try {
            val db = FourCBOn2Database(DiskDirectoryBackend(dir))
            val store = InMemoryCredentialStore()
            val github = GitHubCacheAndDiffManager(
                database = db,
                credentialStore = store,
                connectionFactory = { url ->
                    FakeTextHttpConnection(url, 200, "line 1\nline 2\nline 3")
                },
            )

            // Reject unsafe repo paths
            assertTrue(github.refreshPermittedFile("../secret") is GitHubSnapshotRefreshOutcome.RejectedPath)

            // Refresh, edit locally, and inspect unified diff + exportable patch
            val refreshed = github.refreshPermittedFile("4CBOn2_Build_Divisions.md", nowEpochMs = 500L)
            assertTrue(refreshed is GitHubSnapshotRefreshOutcome.Refreshed)
            github.updateLocalWorkingCopy("4CBOn2_Build_Divisions.md", "line 1\nline 2 updated\nline 3\nline 4", 600L)
            val patch = github.prepareExportableChange("4CBOn2_Build_Divisions.md", 700L)!!
            assertEquals(2, patch.addedLines)
            assertEquals(1, patch.removedLines)
            assertTrue(patch.unifiedDiff.contains("+line 2 updated"))
            assertTrue(patch.toExportJson().contains("\"remoteWritePerformed\":false"))

            // Division 23: Kaggle competition staleness & missing-token status
            val kaggleClient = KaggleCompetitionReadClient(
                credentialStore = store,
                connectionFactory = { url -> FakeTextHttpConnection(url, 200, "[]") },
            )
            val kaggleStatus = KaggleStatusRepository(db, kaggleClient)
            kaggleStatus.trackCompetition("tabular-playground", "Tabular Playground", "ROC-AUC", 5, 1_000L)

            val missingCredSync = kaggleStatus.syncAuthorizedStatus("tabular-playground", 1_100L)!!
            assertEquals(KaggleServiceSyncState.CREDENTIAL_MISSING, missingCredSync.lastSyncOutcome)
            assertTrue(missingCredSync.isStale(1_200L))

            store.save(CredentialSlot.KAGGLE_BEARER_ACCESS_TOKEN, "KGAT_validtoken123456789".toCharArray())
            val okSync = kaggleStatus.syncAuthorizedStatus("tabular-playground", 2_000L)!!
            assertEquals(KaggleServiceSyncState.VERIFIED_ONLINE, okSync.lastSyncOutcome)
            assertFalse(okSync.isStale(2_000L + 60_000L))
            assertTrue(okSync.isStale(2_000L + 45L * 60_000L))
        } finally {
            dir.deleteRecursively()
        }
    }

    // Divisions 24 & 25: Chrome Custom Tabs & Controlled Browser Workspace
    @Test
    fun browserSurfacesEnforceUrlBoundariesRedirectAuthToCustomTabsAndExtractCleanEvidence() {
        val dir = tempDir("4cbon2_browser_test")
        try {
            val db = FourCBOn2Database(DiskDirectoryBackend(dir))
            val timeline = ActionTimelineRepository(db)
            val obs = ConsentedObservationBoundary(db)
            val kb = OfflineKnowledgeLibrary(db)

            val customTabLauncher = ChromeCustomTabLauncher(
                driver = object : CustomTabPlatformDriver {
                    override fun openHttpsCustomTab(url: String, preferChromePackage: Boolean): BrowserOpenMode =
                        BrowserOpenMode.CHROME_CUSTOM_TAB
                },
                timeline = timeline,
            )

            // Reject insecure, javascript, and metadata URLs
            assertTrue(customTabLauncher.openServiceLink("http://github.com") is ChromeOpenOutcome.RejectedUrl)
            assertTrue(customTabLauncher.openServiceLink("javascript:alert(1)") is ChromeOpenOutcome.RejectedUrl)
            assertTrue(customTabLauncher.openServiceLink("https://169.254.169.254/latest") is ChromeOpenOutcome.RejectedUrl)

            // Valid Colab handoff opens Custom Tab as manual session
            val colabOpen = customTabLauncher.openColabNotebook(nowEpochMs = 500L)
            assertTrue(colabOpen is ChromeOpenOutcome.OpenedManualSession)

            // Controlled Browser Workspace policy & public reader
            val workspace = ControlledBrowserWorkspace(
                timeline = timeline,
                observationBoundary = obs,
                knowledgeLibrary = kb,
                connectionFactory = { url ->
                    val html = """
                        <html>
                        <head><title>Evaluation Metric</title><script>stealCookies()</script></head>
                        <body><p>Submissions are evaluated on area under the ROC curve.</p></body>
                        </html>
                    """.trimIndent()
                    FakeTextHttpConnection(url, 200, html)
                },
            )

            // Sign-in and Colab URLs must redirect to Custom Tabs instead of embedded WebView
            assertTrue(
                workspace.readPublicPage(ColabNotebookReference.colabOpenUrl) is
                    ControlledReaderOutcome.RedirectToCustomTabRequired,
            )
            assertTrue(
                workspace.readPublicPage("https://github.com/login") is
                    ControlledReaderOutcome.RedirectToCustomTabRequired,
            )

            // Unlisted domain is blocked
            assertTrue(
                workspace.readPublicPage("https://evil.example.org/page") is
                    ControlledReaderOutcome.BlockedUrlOrDomain,
            )

            // Allowed public GitHub/Kaggle page extracts clean text without script tags and saves to Knowledge
            val readOk = workspace.readPublicPage(
                "https://www.kaggle.com/competitions/tabular-playground/overview",
                saveToKnowledge = true,
                nowEpochMs = 900L,
            )
            assertTrue(readOk is ControlledReaderOutcome.ReadSuccess)
            val snap = (readOk as ControlledReaderOutcome.ReadSuccess).snapshot
            assertEquals("Evaluation Metric", snap.title)
            assertFalse(snap.extractedText.contains("stealCookies"))
            assertTrue(snap.extractedText.contains("ROC curve"))
            assertNotNull(readOk.savedKnowledgeEntry)
        } finally {
            dir.deleteRecursively()
        }
    }

    private class FakeTextHttpConnection(
        url: URL,
        private val code: Int,
        private val body: String,
    ) : HttpURLConnection(url) {
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun connect() = Unit
        override fun getResponseCode(): Int = code
        override fun getInputStream(): InputStream = ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
    }
}

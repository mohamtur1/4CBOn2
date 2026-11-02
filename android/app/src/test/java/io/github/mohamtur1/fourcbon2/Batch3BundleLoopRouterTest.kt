package io.github.mohamtur1.fourcbon2

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Batch3BundleLoopRouterTest {
    private fun tempDir(prefix: String): File = Files.createTempDirectory(prefix).toFile()

    // Divisions 28 & 30: Shared Job Bundle + Colab Built-in AI Receipt Bridge
    @Test
    fun sharedJobBundleVerifiesIntegrityStripsAuthorityAndValidatesColabReceipts() {
        val dir = tempDir("4cbon2_bundle_test")
        try {
            val container = FourCBOn2WorkspaceContainer.createForTest(dir)
            val importedFile = (container.workspaceFiles.importUtf8Text(
                displayName = "features.csv",
                text = "f1,f2\n1,2\n",
                mimeType = "text/csv",
                nowEpochMs = 100L,
            ) as FileImportOutcome.Imported).record

            val draft = container.agentDrafts.createDraft(
                title = "Colab Tabular Prep",
                goal = "Check schema and return summary",
                profile = JobProfile.COMPETITION_SOLVER,
                caps = ExecutionCaps(maxSteps = 12, maxRuntimeMinutes = 20, allowNetwork = true, allowCloudAi = true),
                nowEpochMs = 200L,
            )

            val bundle = container.jobBundleManager.createBundleFromDraft(
                draft = draft,
                targetRole = BundleExecutionRole.COLAB_CONTROLLER,
                requestedActions = listOf(ScopedActionType.READ_KAGGLE_STATUS, ScopedActionType.SHARE_TEXT_DRAFT),
                attachedFileIds = listOf(importedFile.id),
                nowEpochMs = 300L,
            )
            val serialized = container.jobBundleManager.serializeBundle(bundle)

            // Local scope allows only READ_KAGGLE_STATUS -> SHARE_TEXT_DRAFT must be denied on revalidation!
            val localScope = container.scopeBroker.createContract(
                taskTitle = "Narrow Receiver Scope",
                allowedPackages = emptySet(),
                allowedActions = setOf(ScopedActionType.READ_KAGGLE_STATUS),
                nowEpochMs = 350L,
            )

            val revalidated = container.jobBundleManager.importAndRevalidate(
                rawJson = serialized,
                suppliedArtifactHashesByName = mapOf("features.csv" to importedFile.sha256Hex),
                localScopeContract = localScope,
            )
            assertTrue(revalidated is BundleImportResult.VerifiedAndRevalidated)
            val verified = (revalidated as BundleImportResult.VerifiedAndRevalidated).bundle
            assertEquals(setOf(ScopedActionType.READ_KAGGLE_STATUS), verified.locallyPermittedActions)
            assertEquals(setOf(ScopedActionType.SHARE_TEXT_DRAFT), verified.deniedRequestedActions)
            assertTrue(verified.requiresFreshOwnerApproval)

            // Tampered manifest is rejected
            val tampered = serialized.replace("Check schema", "Run arbitrary shell")
            assertTrue(container.jobBundleManager.importAndRevalidate(tampered) is BundleImportResult.Rejected)

            // Division 30: Colab AI receipt rejects alternate providers & accepts valid google.colab.ai receipt
            val badProviderJson = """{"provider":"openai","selectedModelId":"gpt","runtimeRole":"COLAB_CONTROLLER","modelCallsUsed":1,"maxModelCalls":8,"boundedResponseText":"ok","receiptSha256":"00"}"""
            assertTrue(
                container.colabAiBridge.importVerifiedColabReceipt(badProviderJson) is ColabReceiptImportOutcome.Rejected,
            )

            val canonical = "google.colab.ai|google/gemini-2.5-flash|COLAB_CONTROLLER|1|8|Verified probe output"
            val receiptHash = ScopedWorkspaceFileManager.sha256Hex(canonical.toByteArray(Charsets.UTF_8))
            val validReceipt = JsonCodec.stringify(
                JsonValue.Obj(
                    linkedMapOf(
                        "provider" to JsonValue.Str("google.colab.ai"),
                        "selectedModelId" to JsonValue.Str("google/gemini-2.5-flash"),
                        "runtimeRole" to JsonValue.Str("COLAB_CONTROLLER"),
                        "modelCallsUsed" to JsonValue.Num(1.0, "1"),
                        "maxModelCalls" to JsonValue.Num(8.0, "8"),
                        "boundedResponseText" to JsonValue.Str("Verified probe output"),
                        "receiptSha256" to JsonValue.Str(receiptHash),
                    ),
                ),
            )
            assertTrue(
                container.colabAiBridge.importVerifiedColabReceipt(validReceipt, 500L) is ColabReceiptImportOutcome.Accepted,
            )
            assertEquals(ColabBuiltInAiState.VERIFIED_VIA_COLAB_RECEIPT, container.colabAiBridge.currentState())
        } finally {
            dir.deleteRecursively()
        }
    }

    // Division 31: Bounded Agent Loop (success, mid-run takeover stop, and error path)
    @Test
    fun boundedAgentLoopCompletesVerifiedWorkflowAndStopsImmediatelyOnTakeover() {
        val dir = tempDir("4cbon2_loop_test")
        try {
            val container = FourCBOn2WorkspaceContainer.createForTest(dir)
            val lease = container.takeoverLease.startOrResumeAutomation("sess_loop", explicitOwnerResume = true, nowEpochMs = 100L)

            val ok = container.boundedAgentLoop.runBoundedWorkflow(
                jobId = "job_loop_1",
                goal = "Check tutorials and CSV",
                sessionId = "sess_loop",
                leaseEpoch = lease.leaseEpoch,
                steps = listOf(
                    BoundedLoopStepSpec.SearchLocalKnowledge("credentials"),
                    BoundedLoopStepSpec.AnalyzeCsvFixture("mini.csv", "a,b\n1,10\n2,20"),
                    BoundedLoopStepSpec.SaveVerifiedSummary("Loop Output"),
                ),
                maxSteps = 5,
                nowEpochMs = 200L,
            )
            assertTrue(ok is BoundedLoopOutcome.CompletedVerified)

            // Trigger owner Take Over before step 2 -> loop must stop cleanly at step 1!
            val lease2 = container.takeoverLease.startOrResumeAutomation("sess_loop_2", explicitOwnerResume = true, nowEpochMs = 300L)
            val stopped = container.boundedAgentLoop.runBoundedWorkflow(
                jobId = "job_loop_2",
                goal = "Interrupted workflow",
                sessionId = "sess_loop_2",
                leaseEpoch = lease2.leaseEpoch,
                steps = listOf(
                    BoundedLoopStepSpec.SearchLocalKnowledge("credentials"),
                    BoundedLoopStepSpec.SaveVerifiedSummary("Should Not Save"),
                ),
                maxSteps = 5,
                onStepHook = { stepIdx ->
                    if (stepIdx == 1) container.takeoverLease.takeOver(nowEpochMs = 350L)
                },
                nowEpochMs = 310L,
            )
            assertTrue(stopped is BoundedLoopOutcome.StoppedByTakeoverOrCap)
            assertEquals(1, (stopped as BoundedLoopOutcome.StoppedByTakeoverOrCap).stepsExecuted)

            // Malformed CSV triggers clean FailedVerificationOrError path
            val lease3 = container.takeoverLease.startOrResumeAutomation("sess_loop_3", explicitOwnerResume = true, nowEpochMs = 400L)
            val failed = container.boundedAgentLoop.runBoundedWorkflow(
                jobId = "job_loop_3",
                goal = "Error path test",
                sessionId = "sess_loop_3",
                leaseEpoch = lease3.leaseEpoch,
                steps = listOf(BoundedLoopStepSpec.AnalyzeCsvFixture("bad.csv", "only_header")),
                nowEpochMs = 410L,
            )
            assertTrue(failed is BoundedLoopOutcome.FailedVerificationOrError)
        } finally {
            dir.deleteRecursively()
        }
    }

    // Divisions 33, 34, 36: Routine Engine, Conditional Personal-Lab Runner, and Online/Offline Router
    @Test
    fun routineEnginePersonalLabRunnerAndActionRouterEnforceStrictPolicyBoundaries() {
        val dir = tempDir("4cbon2_router_test")
        try {
            val container = FourCBOn2WorkspaceContainer.createForTest(dir)
            container.capabilityRegistry.setOwnerSelected("com.google.android.gm", true, 100L)
            val lease = container.takeoverLease.startOrResumeAutomation("sess_r", explicitOwnerResume = true, nowEpochMs = 110L)
            val scope = container.scopeBroker.createContract(
                taskTitle = "Routine & Lab Scope",
                allowedPackages = setOf("com.google.android.gm", PersonalLabConfiguration.DEFAULT_FIXTURE_PACKAGE),
                allowedActions = setOf(ScopedActionType.SHARE_TEXT_DRAFT, ScopedActionType.EXPERIMENTAL_FIXTURE_UI),
                nowEpochMs = 120L,
            )

            val routine = HumanDefinedRoutine(
                id = "r1",
                title = "Owner Routine",
                preconditions = RoutinePreconditions(
                    requireUnlockedDevice = true,
                    requiredSelectedPackages = setOf("com.google.android.gm"),
                ),
                steps = listOf(
                    RoutineActionStep.VerifyKnowledgeExists("tutorial"),
                    RoutineActionStep.PrepareShareDraftIntent("com.google.android.gm", "Subj", "Body"),
                ),
            )

            // AI-initiated routine trigger MUST be rejected (not an Accessibility policy loophole!)
            assertTrue(
                container.routineEngine.runRoutine(
                    routine = routine,
                    triggerSource = RoutineTriggerSource.AI_PLANNER_ATTEMPT,
                    isDeviceUnlocked = true,
                    sessionId = "sess_r",
                    leaseEpoch = lease.leaseEpoch,
                    scopeContractId = scope.id,
                ) is RoutineExecutionOutcome.RejectedPolicyOrTrigger,
            )

            // Locked device fails precondition
            assertTrue(
                container.routineEngine.runRoutine(
                    routine = routine,
                    triggerSource = RoutineTriggerSource.EXPLICIT_HUMAN_TAP,
                    isDeviceUnlocked = false,
                    sessionId = "sess_r",
                    leaseEpoch = lease.leaseEpoch,
                    scopeContractId = scope.id,
                ) is RoutineExecutionOutcome.PreconditionFailed,
            )

            // Explicit human tap on unlocked device succeeds
            assertEquals(
                RoutineExecutionOutcome.Completed(2),
                container.routineEngine.runRoutine(
                    routine = routine,
                    triggerSource = RoutineTriggerSource.EXPLICIT_HUMAN_TAP,
                    isDeviceUnlocked = true,
                    sessionId = "sess_r",
                    leaseEpoch = lease.leaseEpoch,
                    scopeContractId = scope.id,
                    nowEpochMs = 200L,
                ),
            )

            // Division 34: Personal-Lab UI runner is unavailable in Standard profile
            val fixtureStep = FixtureUiStep(
                targetPackage = PersonalLabConfiguration.DEFAULT_FIXTURE_PACKAGE,
                elementTag = "btn_submit_fixture",
                actionName = "click",
            )
            assertTrue(
                container.personalLabRunner.runFixtureStep(
                    config = PersonalLabConfiguration(distributionProfile = DistributionProfile.STANDARD_PROTOTYPE),
                    sessionId = "sess_r",
                    leaseEpoch = lease.leaseEpoch,
                    scopeContractId = scope.id,
                    step = fixtureStep,
                    nowEpochMs = 210L,
                ) is PersonalLabRunnerOutcome.UnavailableInStandardProfile,
            )

            // Even in Personal-Device Lab mode, non-fixture apps are rejected
            val labConfig = PersonalLabConfiguration(
                distributionProfile = DistributionProfile.PERSONAL_DEVICE_LAB,
                ownerDisclosureAccepted = true,
                deviceEligibilityVerified = true,
            )
            assertFalse(labConfig.isPlayStoreCompliant)
            assertTrue(
                container.personalLabRunner.runFixtureStep(
                    config = labConfig,
                    sessionId = "sess_r",
                    leaseEpoch = lease.leaseEpoch,
                    scopeContractId = scope.id,
                    step = fixtureStep.copy(targetPackage = "com.google.android.gm"),
                    nowEpochMs = 220L,
                ) is PersonalLabRunnerOutcome.RejectedNonFixtureOrProtectedTarget,
            )

            // Register owner-controlled fixture in capability registry -> Lab fixture step succeeds
            container.capabilityRegistry.registerOrUpdateApp(
                packageName = PersonalLabConfiguration.DEFAULT_FIXTURE_PACKAGE,
                displayName = "Owner Test Fixture App",
                coverageState = AppCoverageState.EXPERIMENTAL_UI_TEST,
                supportedActions = setOf(ScopedActionType.EXPERIMENTAL_FIXTURE_UI),
                ownerSelected = true,
                nowEpochMs = 230L,
            )
            assertTrue(
                container.personalLabRunner.runFixtureStep(
                    config = labConfig,
                    sessionId = "sess_r",
                    leaseEpoch = lease.leaseEpoch,
                    scopeContractId = scope.id,
                    step = fixtureStep,
                    nowEpochMs = 240L,
                ) is PersonalLabRunnerOutcome.ExecutedAgainstFixture,
            )

            // Division 36: Online/Offline Phone-Control Router invariants
            val router = container.actionRouter

            // 1. Offline cloud planning defers cleanly (never fakes cloud AI)
            val offlineCloud = router.selectRoute(
                request = ActionRoutingRequest(goalSummary = "Plan ML", targetPackage = null, requiresCloudColabPlanning = true),
                env = RoutingEnvironment(
                    isOnline = false,
                    isDeviceUnlocked = true,
                    configuredCredentials = emptySet(),
                    colabAiState = ColabBuiltInAiState.UNPROBED,
                ),
            )
            assertEquals(PhoneControlRouteKind.DEFERRED_OFFLINE_OR_LOCKED, offlineCloud.selectedRoute)

            // 2. Locked device blocks interactive intent bypass
            val lockedIntent = router.selectRoute(
                request = ActionRoutingRequest(goalSummary = "Share", targetPackage = "com.google.android.gm", hasSupportedIntentAdapter = true),
                env = RoutingEnvironment(
                    isOnline = true,
                    isDeviceUnlocked = false,
                    configuredCredentials = emptySet(),
                    colabAiState = ColabBuiltInAiState.UNPROBED,
                ),
            )
            assertEquals(PhoneControlRouteKind.DEFERRED_OFFLINE_OR_LOCKED, lockedIntent.selectedRoute)

            // 3. Silent alternate provider override is blocked
            val altProvider = router.selectRoute(
                request = ActionRoutingRequest(goalSummary = "Ask", targetPackage = null, canRunPurelyOfflineLocally = true),
                env = RoutingEnvironment(
                    isOnline = true,
                    isDeviceUnlocked = true,
                    configuredCredentials = emptySet(),
                    colabAiState = ColabBuiltInAiState.UNPROBED,
                    requestedProviderOverride = "anthropic.claude",
                ),
            )
            assertEquals(PhoneControlRouteKind.EXPLICIT_MANUAL_HANDOFF, altProvider.selectedRoute)
        } finally {
            dir.deleteRecursively()
        }
    }
}

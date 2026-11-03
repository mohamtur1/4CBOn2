package io.github.mohamtur1.fourcbon2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val workspaceContainer: FourCBOn2WorkspaceContainer by lazy {
        FourCBOn2WorkspaceContainer.createForAndroid(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FourCBOn2PrototypeApp(container = workspaceContainer)
        }
    }
}

@Composable
private fun FourCBOn2PrototypeApp(container: FourCBOn2WorkspaceContainer) {
    var destination by remember { mutableStateOf(AppDestination.HOME) }
    var refreshTick by remember { mutableIntStateOf(0) }
    val lease = remember(refreshTick) { container.takeoverLease.currentSnapshot() }

    MaterialTheme {
        Scaffold(
            topBar = {
                GlobalTakeoverLeaseBar(
                    lease = lease,
                    onTakeOver = {
                        val snap = container.takeoverLease.takeOver()
                        container.actionTimeline.recordEvent(
                            stage = TimelineStage.TAKEOVER_OR_STOP,
                            actor = TimelineActor.OWNER,
                            summary = "Owner activated Take Over (epoch ${snap.leaseEpoch}).",
                            outcome = TimelineOutcomeState.VERIFIED,
                        )
                        refreshTick++
                    },
                    onStop = {
                        val snap = container.takeoverLease.stopAll()
                        container.actionTimeline.recordEvent(
                            stage = TimelineStage.TAKEOVER_OR_STOP,
                            actor = TimelineActor.OWNER,
                            summary = "Owner triggered Stop (epoch ${snap.leaseEpoch}).",
                            outcome = TimelineOutcomeState.VERIFIED,
                        )
                        refreshTick++
                    },
                    onExplicitResume = {
                        val snap = container.takeoverLease.startOrResumeAutomation(explicitOwnerResume = true)
                        container.actionTimeline.recordEvent(
                            stage = TimelineStage.TAKEOVER_OR_STOP,
                            actor = TimelineActor.OWNER,
                            summary = "Owner explicitly started/resumed session ${snap.activeSessionId} (epoch ${snap.leaseEpoch}).",
                            outcome = TimelineOutcomeState.VERIFIED,
                        )
                        refreshTick++
                    },
                )
            },
            bottomBar = {
                NavigationBar {
                    AppDestination.entries.forEach { item ->
                        NavigationBarItem(
                            selected = destination == item,
                            onClick = {
                                refreshTick++
                                destination = item
                            },
                            icon = { Text(item.marker) },
                            label = { Text(item.label) },
                        )
                    }
                }
            },
        ) { innerPadding ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                when (destination) {
                    AppDestination.HOME -> HomeScreen(
                        container = container,
                        refreshTick = refreshTick,
                        onMutated = { refreshTick++ },
                    )
                    AppDestination.ASK -> AskKnowledgeScreen(
                        container = container,
                        refreshTick = refreshTick,
                        onMutated = { refreshTick++ },
                    )
                    AppDestination.AGENTS -> AgentsBuilderScreen(
                        container = container,
                        refreshTick = refreshTick,
                        onMutated = { refreshTick++ },
                    )
                    AppDestination.BROWSER -> BrowserWorkspaceScreen(
                        container = container,
                        refreshTick = refreshTick,
                        onMutated = { refreshTick++ },
                    )
                    AppDestination.ACTIVITY -> QueueAndActivityScreen(
                        container = container,
                        refreshTick = refreshTick,
                        onMutated = { refreshTick++ },
                    )
                }
            }
        }
    }
}

@Composable
private fun GlobalTakeoverLeaseBar(
    lease: ControlLeaseSnapshot,
    onTakeOver: () -> Unit,
    onStop: () -> Unit,
    onExplicitResume: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Control: ${lease.mode.label} (epoch ${lease.leaseEpoch})",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = lease.statusNote,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onTakeOver) { Text("Take Over") }
                TextButton(onClick = onStop) { Text("Stop") }
                if (lease.mode != ControlLeaseMode.AUTOMATION_ACTIVE) {
                    TextButton(onClick = onExplicitResume) { Text("Enable") }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    container: FourCBOn2WorkspaceContainer,
    refreshTick: Int,
    onMutated: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val draftsCount = remember(refreshTick) { container.agentDrafts.listAll().size }
    val kbCount = remember(refreshTick) { container.knowledgeLibrary.listEntries().size }
    val queueCount = remember(refreshTick) { container.waitingQueue.listJobs().size }
    val filesList = remember(refreshTick) { container.workspaceFiles.listFiles() }
    val env = remember(refreshTick) { container.currentQueueEnvironment() }
    val appEntries = remember(refreshTick) { container.capabilityRegistry.listApps() }
    val competitions = remember(refreshTick) { container.kaggleStatus.listCompetitions() }
    val obsPolicy = remember(refreshTick) { container.observationBoundary.currentPolicy() }

    var selectedSlot by remember { mutableStateOf(CredentialSlot.KAGGLE_BEARER_ACCESS_TOKEN) }
    var secretInput by remember { mutableStateOf("") }
    var credentialBanner by remember { mutableStateOf<String?>(null) }
    var probeBanner by remember { mutableStateOf<String?>(null) }
    var scopeIntentBanner by remember { mutableStateOf<String?>(null) }
    var gitDiffBanner by remember { mutableStateOf<String?>(null) }

    var analysisInput by remember {
        mutableStateOf("id,score,passed\n1,0.84,true\n2,0.91,true\n3,,false")
    }
    var analysisFormatCsv by remember { mutableStateOf(true) }
    var analysisReport by remember { mutableStateOf<DatasetAnalysisReport?>(null) }
    var fileStatusBanner by remember { mutableStateOf<String?>(null) }
    var pendingExportFileId by remember { mutableStateOf<String?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val outcome = withContext(Dispatchers.IO) {
                    container.workspaceFiles.importFromSafUri(context, uri)
                }
                fileStatusBanner = when (outcome) {
                    is FileImportOutcome.Imported ->
                        "Imported '${outcome.record.displayName}' (${outcome.record.sizeBytes} B, SHA-256 ${outcome.record.sha256Hex.take(12)}…)"
                    FileImportOutcome.EmptyFileRejected -> "Rejected empty document."
                    is FileImportOutcome.ExceededSizeLimit -> "Rejected: exceeds ${outcome.maxBytes} B limit."
                    is FileImportOutcome.IoFailure -> "Import failed: ${outcome.reason}"
                }
                onMutated()
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val targetId = pendingExportFileId
        if (uri != null && targetId != null) {
            scope.launch {
                val outcome = withContext(Dispatchers.IO) {
                    container.workspaceFiles.exportToSafUri(context, targetId, uri)
                }
                fileStatusBanner = when (outcome) {
                    is FileExportOutcome.Exported ->
                        "Exported ${outcome.bytesWritten} B (SHA-256 ${outcome.sha256Hex.take(12)}…)"
                    FileExportOutcome.FileNotFound -> "Export failed: workspace file missing."
                    FileExportOutcome.IntegrityCheckFailed -> "Export blocked: SHA-256 mismatch."
                    is FileExportOutcome.IoFailure -> "Export failed: ${outcome.reason}"
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "4CBOn2",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        StatusCard(
            title = if (env.isNetworkAvailable) "Mode: Online Companion (local-first)" else "Mode: Offline Workspace",
            detail = "Engine: Local SQLite + Scope Broker · Drafts: $draftsCount · Knowledge: $kbCount · Queued: $queueCount · Files: ${filesList.size}",
        )

        // Division 09a: Local Credential Management
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Connections & Local Credentials (Keystore)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CredentialSlot.entries.forEach { slot ->
                        val configured = container.credentialEntry.presenceOf(slot) == CredentialPresence.CONFIGURED
                        FilterChip(
                            selected = selectedSlot == slot,
                            onClick = { selectedSlot = slot },
                            label = {
                                Text(if (configured) "${slot.displayTitle} ✓" else slot.displayTitle)
                            },
                        )
                    }
                }
                Text(text = selectedSlot.scopeDescription, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = secretInput,
                    onValueChange = { secretInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Paste ${selectedSlot.displayTitle}") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val chars = secretInput.toCharArray()
                            secretInput = ""
                            val res = container.credentialEntry.saveUserSecret(selectedSlot, chars)
                            credentialBanner = when (res) {
                                is CredentialSaveOutcome.Saved -> "${res.slot.displayTitle} saved locally."
                                CredentialSaveOutcome.RejectedEmpty -> "Credential cannot be empty."
                                is CredentialSaveOutcome.RejectedInvalidFormat -> res.guidance
                                is CredentialSaveOutcome.StorageUnavailable -> res.message
                            }
                            container.waitingQueue.refreshReadiness(container.currentQueueEnvironment())
                            onMutated()
                        },
                    ) {
                        Text("Save Locally")
                    }
                    OutlinedButton(
                        onClick = {
                            container.credentialEntry.clearSlot(selectedSlot)
                            credentialBanner = "${selectedSlot.displayTitle} removed."
                            container.waitingQueue.refreshReadiness(container.currentQueueEnvironment())
                            onMutated()
                        },
                    ) {
                        Text("Clear")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val res = withContext(Dispatchers.IO) {
                                    container.githubReadClient.probePublicRepository()
                                }
                                probeBanner = "GitHub public read: ${res::class.simpleName}"
                            }
                        },
                    ) {
                        Text("Probe GitHub")
                    }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val synced = withContext(Dispatchers.IO) {
                                    competitions.firstOrNull()?.let { comp ->
                                        container.kaggleStatus.syncAuthorizedStatus(comp.slug)
                                    }
                                }
                                probeBanner = "Kaggle status: ${synced?.statusBadge() ?: "No competition tracked"}"
                                onMutated()
                            }
                        },
                    ) {
                        Text("Sync Kaggle Status")
                    }
                }
                competitions.forEach { comp ->
                    Text(
                        text = "Kaggle [${comp.slug}]: ${comp.statusBadge()} · Metric=${comp.evaluationMetric}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                credentialBanner?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                probeBanner?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }

        // Divisions 12, 13, 21, 26: Selected Apps, Scope Broker, Harmless Intent & Observation Policy
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Selected Apps, Scope & Observation Boundary",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "All apps start Off. Protected system/banking screens remain manual-only.",
                    style = MaterialTheme.typography.bodySmall,
                )
                appEntries.forEach { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = entry.displayName, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "${entry.packageName} · ${entry.coverageState.label}",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        Switch(
                            checked = entry.ownerSelected,
                            enabled = !entry.protectedManualOnly,
                            onCheckedChange = { enabled ->
                                container.capabilityRegistry.setOwnerSelected(entry.packageName, enabled)
                                onMutated()
                            },
                        )
                    }
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Opt-in Screen Capture (Sensitive Excluded)", style = MaterialTheme.typography.bodySmall)
                    Switch(
                        checked = obsPolicy.allowScreenCaptureOptIn,
                        onCheckedChange = { v ->
                            container.observationBoundary.updatePolicy(obsPolicy.copy(allowScreenCaptureOptIn = v))
                            onMutated()
                        },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Upload Observations to Cloud AI (Default Off)", style = MaterialTheme.typography.bodySmall)
                    Switch(
                        checked = obsPolicy.allowCloudObservationUpload,
                        onCheckedChange = { v ->
                            container.observationBoundary.updatePolicy(obsPolicy.copy(allowCloudObservationUpload = v))
                            onMutated()
                        },
                    )
                }
                Button(
                    onClick = {
                        val lease = container.takeoverLease.let { ctrl ->
                            val cur = ctrl.currentSnapshot()
                            if (cur.mode == ControlLeaseMode.AUTOMATION_ACTIVE) cur
                            else ctrl.startOrResumeAutomation(explicitOwnerResume = true)
                        }
                        val contract = container.scopeBroker.createContract(
                            taskTitle = "Prepare Gmail Summary Draft",
                            allowedPackages = setOf("com.google.android.gm"),
                            allowedActions = setOf(ScopedActionType.SHARE_TEXT_DRAFT),
                            maxActionCount = 2,
                        )
                        val res = container.intentExecutor.execute(
                            sessionId = lease.activeSessionId.orEmpty(),
                            commandEpoch = lease.leaseEpoch,
                            scopeContractId = contract.id,
                            spec = HarmlessIntentSpec.SharePlainTextDraft(
                                targetPackage = "com.google.android.gm",
                                subject = "4CBOn2 Offline Summary",
                                draftBody = "Prepared locally by 4CBOn2; review and send manually.",
                            ),
                        )
                        scopeIntentBanner = when (res) {
                            is IntentDispatchResult.HandedOffToApp -> res.detail
                            is IntentDispatchResult.NoHandlerAvailable -> "No app installed for ${res.targetPackage}."
                            is IntentDispatchResult.BlockedByScope -> "Scope denied: ${res.reason}"
                            is IntentDispatchResult.BlockedByLease -> "Lease blocked: ${res.reason}"
                            is IntentDispatchResult.BlockedByCriticalGuard -> "Guard: ${res.reason}"
                            is IntentDispatchResult.InvalidParameters -> res.reason
                        }
                        onMutated()
                    },
                ) {
                    Text("Prepare Scoped Gmail Draft Intent")
                }
                scopeIntentBanner?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }

        // Division 22: GitHub Cache & Diff
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "GitHub Snapshot Cache & Local Diff",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val res = withContext(Dispatchers.IO) {
                                    container.githubCacheDiff.refreshPermittedFile("4CBOn2_Build_Divisions.md")
                                }
                                gitDiffBanner = when (res) {
                                    is GitHubSnapshotRefreshOutcome.Refreshed ->
                                        "Synced ${res.snapshot.repoPath} (SHA-256 ${res.snapshot.baseSha256.take(10)}…)"
                                    is GitHubSnapshotRefreshOutcome.HttpError -> "HTTP ${res.statusCode}"
                                    is GitHubSnapshotRefreshOutcome.RejectedPath -> res.reason
                                    GitHubSnapshotRefreshOutcome.NetworkUnavailable -> "Offline: using cached copy if present."
                                }
                                onMutated()
                            }
                        },
                    ) {
                        Text("Refresh Build Plan")
                    }
                    OutlinedButton(
                        onClick = {
                            val patch = container.githubCacheDiff.prepareExportableChange("4CBOn2_Build_Divisions.md")
                            gitDiffBanner = if (patch == null) {
                                "Refresh or seed a cached file first."
                            } else {
                                "Prepared exportable patch (+${patch.addedLines}/-${patch.removedLines} lines; remote write=false)."
                            }
                        },
                    ) {
                        Text("Inspect Diff")
                    }
                }
                gitDiffBanner?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }

        // Division 16 + 18: Scoped Workspace Files & Local CSV/JSON Analysis
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Scoped Files & Local CSV/JSON Routine",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                        Text("Import Document")
                    }
                    FilterChip(
                        selected = analysisFormatCsv,
                        onClick = { analysisFormatCsv = true },
                        label = { Text("CSV") },
                    )
                    FilterChip(
                        selected = !analysisFormatCsv,
                        onClick = { analysisFormatCsv = false },
                        label = { Text("JSON") },
                    )
                }
                filesList.take(3).forEach { file ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${file.displayName} (${file.sizeBytes} B)",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = {
                                container.workspaceFiles.readVerifiedUtf8Text(file.id)?.let { text ->
                                    analysisInput = text
                                    analysisFormatCsv = !file.displayName.endsWith(".json", ignoreCase = true)
                                }
                            },
                        ) {
                            Text("Load")
                        }
                        TextButton(
                            onClick = {
                                pendingExportFileId = file.id
                                exportLauncher.launch(file.displayName)
                            },
                        ) {
                            Text("Export")
                        }
                    }
                }
                fileStatusBanner?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

                OutlinedTextField(
                    value = analysisInput,
                    onValueChange = { analysisInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (analysisFormatCsv) "CSV fixture / data" else "JSON fixture / data") },
                    minLines = 3,
                    maxLines = 6,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val outcome = if (analysisFormatCsv) {
                                container.localAnalyzer.analyzeCsv(analysisInput, "local_input.csv")
                            } else {
                                container.localAnalyzer.analyzeJson(analysisInput, "local_input.json")
                            }
                            when (outcome) {
                                is LocalAnalysisOutcome.Success -> {
                                    analysisReport = outcome.report
                                    fileStatusBanner = null
                                }
                                is LocalAnalysisOutcome.InvalidInput -> {
                                    analysisReport = null
                                    fileStatusBanner = "Analysis rejected: ${outcome.reason}"
                                }
                            }
                        },
                    ) {
                        Text("Run Local Routine")
                    }
                    val currentReport = analysisReport
                    if (currentReport != null) {
                        OutlinedButton(
                            onClick = {
                                container.knowledgeLibrary.saveEntry(
                                    title = "Analysis: ${currentReport.datasetName} (${currentReport.rowCount} rows)",
                                    body = currentReport.toMarkdownSummary(),
                                    provenance = KnowledgeProvenance.LOCAL_ANALYSIS,
                                    tags = listOf("analysis", currentReport.format.name.lowercase()),
                                )
                                fileStatusBanner = "Saved analysis summary to Offline Knowledge Library."
                                onMutated()
                            },
                        ) {
                            Text("Save to Knowledge")
                        }
                    }
                }
                analysisReport?.let { report ->
                    Text(
                        text = report.toMarkdownSummary(),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun BrowserWorkspaceScreen(
    container: FourCBOn2WorkspaceContainer,
    refreshTick: Int,
    onMutated: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var readerUrl by remember {
        mutableStateOf("https://raw.githubusercontent.com/mohamtur1/4CBOn2/main/4CBOn2_Build_Divisions.md")
    }
    var browserBanner by remember { mutableStateOf<String?>(null) }
    var readerSnapshot by remember { mutableStateOf<PublicPageReaderSnapshot?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Browser Surfaces",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        StatusCard(
            title = "Chrome Custom Tabs + Isolated Reader Workspace",
            detail = "Interactive logins & Colab open in Chrome Custom Tabs. Public evidence reading runs without cookies, JS bridges, or file access.",
        )

        // Division 24: Chrome-Backed Custom Tabs
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Chrome-Backed Service Links (Manual Session)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            val res = container.customTabLauncher.openColabNotebook()
                            browserBanner = formatChromeOutcome(res)
                            onMutated()
                        },
                    ) {
                        Text("Open Colab (${ColabNotebookReference.notebookFileName})")
                    }
                    OutlinedButton(
                        onClick = {
                            val res = container.customTabLauncher.openServiceLink(
                                url = "https://github.com/mohamtur1/4CBOn2",
                                label = "GitHub Repository",
                            )
                            browserBanner = formatChromeOutcome(res)
                            onMutated()
                        },
                    ) {
                        Text("Open GitHub")
                    }
                    OutlinedButton(
                        onClick = {
                            val res = container.customTabLauncher.openServiceLink(
                                url = "https://www.kaggle.com/competitions",
                                label = "Kaggle Competitions",
                            )
                            browserBanner = formatChromeOutcome(res)
                            onMutated()
                        },
                    ) {
                        Text("Open Kaggle")
                    }
                }
            }
        }

        // Division 25: Controlled Browser Reader Workspace
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Controlled Public-Page Reader",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Allowed hosts: ${ControlledBrowserWorkspace.DEFAULT_ALLOWED_PUBLIC_DOMAINS.joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall,
                )
                OutlinedTextField(
                    value = readerUrl,
                    onValueChange = { readerUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Public HTTPS URL") },
                    singleLine = true,
                )
                Button(
                    onClick = {
                        scope.launch {
                            val res = withContext(Dispatchers.IO) {
                                container.browserWorkspace.readPublicPage(readerUrl, saveToKnowledge = true)
                            }
                            when (res) {
                                is ControlledReaderOutcome.ReadSuccess -> {
                                    readerSnapshot = res.snapshot
                                    browserBanner = "Captured '${res.snapshot.title}' & saved to Offline Knowledge Library."
                                }
                                is ControlledReaderOutcome.RedirectToCustomTabRequired -> {
                                    readerSnapshot = null
                                    browserBanner = res.reason
                                }
                                is ControlledReaderOutcome.BlockedUrlOrDomain -> {
                                    readerSnapshot = null
                                    browserBanner = "Blocked: ${res.reason}"
                                }
                                is ControlledReaderOutcome.FetchFailed -> {
                                    readerSnapshot = null
                                    browserBanner = "Fetch error: ${res.reason}"
                                }
                            }
                            onMutated()
                        }
                    },
                ) {
                    Text("Read & Save Public Passage")
                }
                browserBanner?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                readerSnapshot?.let { snap ->
                    HorizontalDivider()
                    Text(
                        text = "${snap.title} (SHA-256 ${snap.contentSha256.take(12)}…)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = snap.extractedText.take(1_200),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

private fun formatChromeOutcome(outcome: ChromeOpenOutcome): String = when (outcome) {
    is ChromeOpenOutcome.OpenedManualSession -> "${outcome.mode.name}: ${outcome.disclaimer}"
    is ChromeOpenOutcome.RejectedUrl -> "Rejected URL: ${outcome.reason}"
    ChromeOpenOutcome.NoBrowserAvailable -> "No browser available on this device."
}

@Composable
private fun AskKnowledgeScreen(
    container: FourCBOn2WorkspaceContainer,
    refreshTick: Int,
    onMutated: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var tutorialsOnly by remember { mutableStateOf(false) }
    var newNoteTitle by remember { mutableStateOf("") }
    var newNoteBody by remember { mutableStateOf("") }
    var localMutation by remember { mutableIntStateOf(0) }

    val hits = remember(query, tutorialsOnly, refreshTick, localMutation) {
        container.knowledgeLibrary.search(
            query = query,
            provenanceFilter = if (tutorialsOnly) KnowledgeProvenance.SYSTEM_TUTORIAL else null,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Ask & Offline Knowledge",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        StatusCard(
            title = "Engine: ${KnowledgeSearchHit.ENGINE_LABEL_OFFLINE_LIBRARY}",
            detail = "Deterministic local passage lookup across saved evidence, public-page snapshots, analysis reports, and How do I? guides.",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !tutorialsOnly,
                onClick = { tutorialsOnly = false },
                label = { Text("All Saved Knowledge") },
            )
            FilterChip(
                selected = tutorialsOnly,
                onClick = { tutorialsOnly = true },
                label = { Text("How do I? Tutorials") },
            )
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search saved notes, reports, or tutorials") },
            singleLine = true,
        )

        hits.forEach { hit ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = hit.entry.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${hit.entry.provenance.label} · ${hit.engineLabel}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = hit.supportingSnippet,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        HorizontalDivider()
        Text(
            text = "Save Note or Evidence Offline",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        OutlinedTextField(
            value = newNoteTitle,
            onValueChange = { newNoteTitle = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Title") },
            singleLine = true,
        )
        OutlinedTextField(
            value = newNoteBody,
            onValueChange = { newNoteBody = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Evidence or note content") },
            minLines = 3,
        )
        Button(
            enabled = newNoteTitle.isNotBlank() && newNoteBody.isNotBlank(),
            onClick = {
                container.knowledgeLibrary.saveEntry(
                    title = newNoteTitle,
                    body = newNoteBody,
                    provenance = KnowledgeProvenance.USER_NOTE,
                    tags = listOf("note"),
                )
                newNoteTitle = ""
                newNoteBody = ""
                localMutation++
                onMutated()
            },
        ) {
            Text("Save to Offline Library")
        }
    }
}

@Composable
private fun AgentsBuilderScreen(
    container: FourCBOn2WorkspaceContainer,
    refreshTick: Int,
    onMutated: () -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var goal by remember { mutableStateOf("") }
    var selectedProfile by remember { mutableStateOf(JobProfile.RESEARCH_SYNTHESIS) }
    var maxStepsText by remember { mutableStateOf("10") }
    var allowNetwork by remember { mutableStateOf(false) }
    var allowCloudAi by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var localMutation by remember { mutableIntStateOf(0) }

    val drafts = remember(refreshTick, localMutation) { container.agentDrafts.listAll() }
    val colabAiState = remember(refreshTick, localMutation) { container.colabAiBridge.currentState() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Agent Builder, Loop & Bundles",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        StatusCard(
            title = "Colab AI: ${colabAiState.label}",
            detail = "Profile: ${DistributionProfile.STANDARD_PROTOTYPE.label} · Personal-Lab UI: Disabled",
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    val lease = container.takeoverLease.let { ctrl ->
                        val cur = ctrl.currentSnapshot()
                        if (cur.mode == ControlLeaseMode.AUTOMATION_ACTIVE) cur
                        else ctrl.startOrResumeAutomation(explicitOwnerResume = true)
                    }
                    val res = container.boundedAgentLoop.runBoundedWorkflow(
                        jobId = "loop_demo",
                        goal = "Verify offline tutorial & tabular baseline fixture",
                        sessionId = lease.activeSessionId.orEmpty(),
                        leaseEpoch = lease.leaseEpoch,
                        steps = listOf(
                            BoundedLoopStepSpec.SearchLocalKnowledge("tutorial"),
                            BoundedLoopStepSpec.AnalyzeCsvFixture(
                                datasetName = "loop_fixture.csv",
                                rawCsv = "fold,auc\n1,0.84\n2,0.86",
                            ),
                            BoundedLoopStepSpec.SaveVerifiedSummary(
                                title = "Bounded Loop Verified Report",
                            ),
                        ),
                        maxSteps = 5,
                    )
                    feedback = when (res) {
                        is BoundedLoopOutcome.CompletedVerified -> res.verificationNote
                        is BoundedLoopOutcome.StoppedByTakeoverOrCap -> res.reason
                        is BoundedLoopOutcome.FailedVerificationOrError -> res.reason
                    }
                    localMutation++
                    onMutated()
                },
            ) {
                Text("Run Bounded Loop")
            }
            OutlinedButton(
                onClick = {
                    val lease = container.takeoverLease.let { ctrl ->
                        val cur = ctrl.currentSnapshot()
                        if (cur.mode == ControlLeaseMode.AUTOMATION_ACTIVE) cur
                        else ctrl.startOrResumeAutomation(explicitOwnerResume = true)
                    }
                    val contract = container.scopeBroker.createContract(
                        taskTitle = "Routine Share Draft",
                        allowedPackages = setOf("com.google.android.gm"),
                        allowedActions = setOf(ScopedActionType.SHARE_TEXT_DRAFT),
                    )
                    val routine = HumanDefinedRoutine(
                        id = "rtn_daily_brief",
                        title = "Share Knowledge Brief Draft",
                        preconditions = RoutinePreconditions(
                            requireUnlockedDevice = true,
                            requiredSelectedPackages = setOf("com.google.android.gm"),
                        ),
                        steps = listOf(
                            RoutineActionStep.VerifyKnowledgeExists("tutorial"),
                            RoutineActionStep.PrepareShareDraftIntent(
                                targetPackage = "com.google.android.gm",
                                subject = "4CBOn2 Routine Brief",
                                body = "Prepared by owner-triggered fixed routine.",
                            ),
                        ),
                    )
                    val out = container.routineEngine.runRoutine(
                        routine = routine,
                        triggerSource = RoutineTriggerSource.EXPLICIT_HUMAN_TAP,
                        isDeviceUnlocked = true,
                        sessionId = lease.activeSessionId.orEmpty(),
                        leaseEpoch = lease.leaseEpoch,
                        scopeContractId = contract.id,
                    )
                    feedback = when (out) {
                        is RoutineExecutionOutcome.Completed -> "Routine completed (${out.stepsCompleted} steps)."
                        is RoutineExecutionOutcome.PreconditionFailed -> "Routine precondition: ${out.reason}"
                        is RoutineExecutionOutcome.RejectedPolicyOrTrigger -> out.reason
                        is RoutineExecutionOutcome.StoppedMidRoutine -> out.reason
                    }
                    localMutation++
                    onMutated()
                },
            ) {
                Text("Run Human Routine")
            }
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BuiltInAgentTemplates.templates.forEach { tpl ->
                OutlinedButton(
                    onClick = {
                        val created = container.agentDrafts.instantiateFromTemplate(tpl)
                        feedback = "Created draft '${created.title}'."
                        localMutation++
                        onMutated()
                    },
                ) {
                    Text("+ ${tpl.title}")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = if (editingId == null) "New Agent Draft" else "Editing Draft ($editingId)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Draft Title") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = goal,
                    onValueChange = { goal = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Permitted Goal") },
                    minLines = 2,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    JobProfile.entries.forEach { profile ->
                        FilterChip(
                            selected = selectedProfile == profile,
                            onClick = { selectedProfile = profile },
                            label = { Text(profile.label) },
                        )
                    }
                }
                OutlinedTextField(
                    value = maxStepsText,
                    onValueChange = { maxStepsText = it.filter(Char::isDigit) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Max Steps Cap (1..200)") },
                    singleLine = true,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = allowNetwork, onCheckedChange = { allowNetwork = it })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Allow Network")
                    Spacer(modifier = Modifier.width(16.dp))
                    Switch(checked = allowCloudAi, onCheckedChange = { allowCloudAi = it })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Allow Colab AI")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = title.isNotBlank() && goal.isNotBlank(),
                        onClick = {
                            val steps = (maxStepsText.toIntOrNull() ?: 10).coerceIn(1, 200)
                            val caps = ExecutionCaps(
                                maxSteps = steps,
                                allowNetwork = allowNetwork,
                                allowCloudAi = allowCloudAi,
                            )
                            val existing = editingId?.let { container.agentDrafts.load(it) }
                            if (existing != null) {
                                container.agentDrafts.save(
                                    existing.copy(
                                        title = title,
                                        goal = goal,
                                        profile = selectedProfile,
                                        caps = caps,
                                    ),
                                    nowEpochMs = System.currentTimeMillis(),
                                )
                                feedback = "Updated draft '${title.trim()}'."
                            } else {
                                container.agentDrafts.createDraft(
                                    title = title,
                                    goal = goal,
                                    profile = selectedProfile,
                                    caps = caps,
                                )
                                feedback = "Saved new draft '${title.trim()}'."
                            }
                            editingId = null
                            title = ""
                            goal = ""
                            localMutation++
                            onMutated()
                        },
                    ) {
                        Text(if (editingId == null) "Save Draft" else "Update Draft")
                    }
                    if (editingId != null) {
                        TextButton(
                            onClick = {
                                editingId = null
                                title = ""
                                goal = ""
                            },
                        ) {
                            Text("Cancel Edit")
                        }
                    }
                }
                feedback?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }

        drafts.forEach { draft ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "${draft.title} (${draft.profile.label})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(text = draft.goal, style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = "Caps: ${draft.caps.maxSteps} steps · Net=${draft.caps.allowNetwork} · Colab=${draft.caps.allowCloudAi}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val queued = container.waitingQueue.enqueueFromDraft(
                                    draft = draft,
                                    env = container.currentQueueEnvironment(),
                                )
                                container.actionTimeline.recordEvent(
                                    stage = TimelineStage.PLAN,
                                    actor = TimelineActor.OWNER,
                                    summary = "Queued draft '${queued.title}' -> ${queued.state.label}",
                                    outcome = TimelineOutcomeState.PENDING,
                                    jobId = queued.id,
                                )
                                feedback = "Queued '${queued.title}' -> ${queued.state.label}"
                                localMutation++
                                onMutated()
                            },
                        ) {
                            Text("Queue Job")
                        }
                        OutlinedButton(
                            onClick = {
                                val manifest = container.jobBundleManager.createBundleFromDraft(draft)
                                val json = container.jobBundleManager.serializeBundle(manifest)
                                container.workspaceFiles.importUtf8Text(
                                    displayName = "${manifest.bundleId}.json",
                                    text = json,
                                    mimeType = "application/json",
                                )
                                feedback = "Exported Shared Job Bundle ${manifest.bundleId} (SHA-256 ${manifest.manifestSha256.take(10)}…) to Scoped Files."
                                localMutation++
                                onMutated()
                            },
                        ) {
                            Text("Bundle")
                        }
                        OutlinedButton(
                            onClick = {
                                editingId = draft.id
                                title = draft.title
                                goal = draft.goal
                                selectedProfile = draft.profile
                                maxStepsText = draft.caps.maxSteps.toString()
                                allowNetwork = draft.caps.allowNetwork
                                allowCloudAi = draft.caps.allowCloudAi
                            },
                        ) {
                            Text("Edit")
                        }
                        TextButton(
                            onClick = {
                                container.agentDrafts.delete(draft.id)
                                localMutation++
                                onMutated()
                            },
                        ) {
                            Text("Delete")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueAndActivityScreen(
    container: FourCBOn2WorkspaceContainer,
    refreshTick: Int,
    onMutated: () -> Unit,
) {
    var localMutation by remember { mutableIntStateOf(0) }
    val jobs = remember(refreshTick, localMutation) {
        container.waitingQueue.refreshReadiness(container.currentQueueEnvironment())
    }
    val timelineEvents = remember(refreshTick, localMutation) {
        container.actionTimeline.listEvents(limit = 30)
    }
    val grants = remember(refreshTick, localMutation) {
        container.approvalGate.listGrants()
    }
    val submissionRecords = remember(refreshTick, localMutation) {
        container.kaggleSubmissionGate.listRecords()
    }
    val labReports = remember(refreshTick, localMutation) {
        container.improvementLab.listReports()
    }
    var labBanner by remember { mutableStateOf<String?>(null) }

    // Batch 5 (v0.3) — Daily Experiment Cycle state
    var dailySlug by remember { mutableStateOf("arc-prize-2026") }
    var diagnosticsInput by remember { mutableStateOf("") }
    var candidateInput by remember { mutableStateOf("") }
    var dailyBanner by remember { mutableStateOf<String?>(null) }
    val dailyCard = remember(refreshTick, localMutation, dailySlug) {
        container.dailyLabPresenter.card(dailySlug)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Activity, Approvals & Timeline",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        StatusCard(
            title = "Actual Action Timeline (${timelineEvents.size} events) · Outbox (${jobs.size} jobs)",
            detail = "Displays only real recorded plan, gate, action, observation, verification, and takeover events.",
        )

        // Batch 5 (v0.3) — Daily Experiment Cycle: ingest -> diagnose -> propose -> dispatch -> gate
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Daily Lab — Self-Improvement Cycle",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = dailyCard.headline,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (dailyCard.alert) FontWeight.SemiBold else FontWeight.Normal,
                )
                Text(text = "Stage: ${dailyCard.stageLabel}", style = MaterialTheme.typography.bodySmall)
                Text(text = "Next: ${dailyCard.nextAction}", style = MaterialTheme.typography.bodySmall)
                Text(text = dailyCard.championLine, style = MaterialTheme.typography.bodySmall)
                Text(text = dailyCard.latestRunLine, style = MaterialTheme.typography.bodySmall)
                if (dailyCard.candidateLine.isNotBlank()) {
                    Text(text = dailyCard.candidateLine, style = MaterialTheme.typography.bodySmall)
                }
                if (dailyCard.gateLine.isNotBlank()) {
                    Text(text = dailyCard.gateLine, style = MaterialTheme.typography.bodySmall)
                }
                if (dailyCard.dispatchLine.isNotBlank()) {
                    Text(text = dailyCard.dispatchLine, style = MaterialTheme.typography.bodySmall)
                }
                dailyBanner?.let { banner ->
                    HorizontalDivider()
                    Text(text = banner, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(
                    value = dailySlug,
                    onValueChange = { dailySlug = it.trim() },
                    label = { Text("Competition slug") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = diagnosticsInput,
                    onValueChange = { diagnosticsInput = it },
                    label = { Text("Paste run diagnostics.json") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            val cycleId = container.dailyCycle.activeCycle(dailySlug)?.cycleId
                                ?: (container.dailyCycle.openCycle(
                                    competitionSlug = dailySlug,
                                    benchmarkKind = BenchmarkKind.ARC_AGI_INTERACTIVE,
                                    channel = DispatchChannel.COLAB_MANUAL,
                                ) as? CycleOpenResult.Opened)?.state?.cycleId
                            dailyBanner = if (cycleId == null) {
                                "Could not open a cycle for '$dailySlug'."
                            } else {
                                when (val result = container.dailyCycle.ingestDiagnoseAndPropose(
                                    cycleId = cycleId,
                                    rawDiagnosticsJson = diagnosticsInput,
                                    runLabel = "owner-pasted run",
                                )) {
                                    is DailyCycleStepResult.AwaitingOwner ->
                                        "${result.state.stage.label}: ${result.prompt}"
                                    is DailyCycleStepResult.Advanced ->
                                        "${result.state.stage.label}: ${result.note}"
                                    is DailyCycleStepResult.Blocked ->
                                        "Blocked: ${result.reason}"
                                }
                            }
                            localMutation++
                            onMutated()
                        },
                    ) {
                        Text("Ingest Run & Diagnose")
                    }
                    Button(
                        enabled = dailyCard.canLockChampion,
                        onClick = {
                            val latest = container.runTelemetry.latestFor(dailySlug)
                            dailyBanner = when {
                                latest == null -> "No telemetry ingested yet."
                                else -> container.dailyCycle.lockStartingChampion(latest.telemetryId, ownerConfirmed = true)
                                    ?.let { "Locked champion ${it.versionRef} (${it.metricName}=${it.score})." }
                                    ?: "A champion is already locked for $dailySlug."
                            }
                            localMutation++
                            onMutated()
                        },
                    ) {
                        Text("Lock Champion")
                    }
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        enabled = dailyCard.canApproveDispatch,
                        onClick = {
                            val active = container.dailyCycle.activeCycle(dailySlug)
                            dailyBanner = if (active == null) {
                                "No cycle awaiting approval."
                            } else {
                                when (val result = container.dailyCycle.approveAndDispatch(
                                    cycleId = active.cycleId,
                                    approvedByOwner = true,
                                )) {
                                    is DailyCycleStepResult.Advanced -> "Dispatched: ${result.note}"
                                    is DailyCycleStepResult.AwaitingOwner -> result.prompt
                                    is DailyCycleStepResult.Blocked -> "Blocked: ${result.reason}"
                                }
                            }
                            localMutation++
                            onMutated()
                        },
                    ) {
                        Text("Approve & Run")
                    }
                    OutlinedButton(
                        enabled = dailyCard.canApprovePromotion,
                        onClick = {
                            val active = container.dailyCycle.activeCycle(dailySlug)
                            dailyBanner = if (active == null) {
                                "No cycle awaiting a promotion decision."
                            } else {
                                when (val result = container.dailyCycle.resolvePromotion(
                                    cycleId = active.cycleId,
                                    approvedByOwner = true,
                                )) {
                                    is DailyCycleStepResult.Advanced -> result.note
                                    is DailyCycleStepResult.AwaitingOwner -> result.prompt
                                    is DailyCycleStepResult.Blocked -> "Champion kept: ${result.reason}"
                                }
                            }
                            localMutation++
                            onMutated()
                        },
                    ) {
                        Text("Approve Promotion")
                    }
                    OutlinedButton(
                        enabled = dailyCard.canApprovePromotion,
                        onClick = {
                            val active = container.dailyCycle.activeCycle(dailySlug)
                            dailyBanner = if (active == null) {
                                "No cycle awaiting a promotion decision."
                            } else {
                                val result = container.dailyCycle.resolvePromotion(
                                    cycleId = active.cycleId,
                                    approvedByOwner = false,
                                )
                                "Champion kept: ${result.state.note}"
                            }
                            localMutation++
                            onMutated()
                        },
                    ) {
                        Text("Decline")
                    }
                    OutlinedButton(
                        enabled = dailyCard.canRollback,
                        onClick = {
                            dailyBanner = container.championLedger
                                .rollbackLastPromotion(dailySlug, ownerConfirmed = true)
                                ?.let { "Rolled back to champion ${it.versionRef}." }
                                ?: "Nothing to roll back."
                            localMutation++
                            onMutated()
                        },
                    ) {
                        Text("Roll Back Champion")
                    }
                }
                OutlinedTextField(
                    value = candidateInput,
                    onValueChange = { candidateInput = it },
                    label = { Text("Paste candidate run diagnostics.json") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            val active = container.dailyCycle.activeCycle(dailySlug)
                            dailyBanner = if (active == null) {
                                "No active cycle to receive candidate telemetry."
                            } else {
                                when (val result = container.dailyCycle.recordCandidateTelemetry(
                                    cycleId = active.cycleId,
                                    rawDiagnosticsJson = candidateInput,
                                    runLabel = "candidate run",
                                )) {
                                    is DailyCycleStepResult.AwaitingOwner ->
                                        "Gate decided: ${result.prompt}"
                                    is DailyCycleStepResult.Advanced ->
                                        "${result.state.stage.label}: ${result.note}"
                                    is DailyCycleStepResult.Blocked ->
                                        "Blocked: ${result.reason}"
                                }
                            }
                            localMutation++
                            onMutated()
                        },
                    ) {
                        Text("Ingest Candidate Run")
                    }
                    OutlinedButton(
                        onClick = {
                            val active = container.dailyCycle.activeCycle(dailySlug)
                            dailyBanner = if (active == null) {
                                "No active cycle to stop."
                            } else {
                                container.dailyCycle.stopCycle(active.cycleId, reason = "Stopped by owner from Daily Lab.")
                                    ?.let { "Cycle stopped." }
                                    ?: "Cycle not found."
                            }
                            localMutation++
                            onMutated()
                        },
                    ) {
                        Text("Stop Cycle")
                    }
                }
                if (dailyCard.recentTimeline.isNotEmpty()) {
                    HorizontalDivider()
                    Text(
                        text = "Recent audit trail",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    dailyCard.recentTimeline.forEach { line ->
                        Text(text = "• $line", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // Divisions 37, 38, 39: Kaggle Prepare-Only Pipeline, Submission Gate & Improvement Lab
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Kaggle Prepare Pipeline & Improvement Lab",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            val train = "id,x1,x2,target\n1,1.0,2.0,3.0\n2,2.0,4.0,6.0\n3,3.0,6.0,9.0\n4,4.0,8.0,12.0\n5,5.0,10.0,15.0\n6,6.0,12.0,18.0"
                            val test = "id,x1,x2\n10,2.5,5.0\n11,3.5,7.0"
                            val out = container.kagglePreparePipeline.prepareTabularBaseline(
                                config = TabularPipelineConfig(competitionSlug = "tabular-playground"),
                                trainCsv = train,
                                testCsv = test,
                            )
                            if (out is PreparePipelineOutcome.Prepared) {
                                val rec = container.kaggleSubmissionGate.recordPreparedOnly("ui_run", out.artifact)
                                labBanner = "Prepared ${out.artifact.artifactId} (SHA-256 ${out.artifact.artifactSha256.take(10)}…) · ${rec.state.label}"
                            }
                            localMutation++
                            onMutated()
                        },
                    ) {
                        Text("Run Prepare-Only Baseline")
                    }
                    OutlinedButton(
                        onClick = {
                            val train = "id,x1,x2,target\n1,1.0,2.0,3.0\n2,2.0,4.0,6.0\n3,3.0,6.0,9.0\n4,4.0,8.0,12.0\n5,5.0,10.0,15.0\n6,6.0,12.0,18.0"
                            val test = "id,x1,x2\n10,2.5,5.0\n11,3.5,7.0"
                            val base = container.improvementLab.registerBaseline("Baseline L2=2.0", l2Regularization = 2.0)
                            val cand = container.improvementLab.proposeCandidate(base.versionId, "Candidate L2=0.05", l2Regularization = 0.05)
                            val rep = container.improvementLab.evaluateCandidateAgainstBaseline(base.versionId, cand.versionId, train, test)
                            labBanner = "Lab comparison: ΔRMSE=${"%.4f".format(rep.rmseImprovement)} -> ${rep.state.label}"
                            localMutation++
                            onMutated()
                        },
                    ) {
                        Text("Run Lab Comparison")
                    }
                }
                labBanner?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                labReports.take(2).forEach { rep ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${rep.candidateVersionId}: ΔRMSE=${"%.4f".format(rep.rmseImprovement)} (${rep.state.label})",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.weight(1f),
                        )
                        if (rep.state == CandidatePromotionState.PENDING_OWNER_APPROVAL) {
                            TextButton(
                                onClick = {
                                    container.improvementLab.approvePromotion(rep.reportId, approvedByOwner = true)
                                    localMutation++
                                    onMutated()
                                },
                            ) {
                                Text("Promote")
                            }
                        }
                    }
                }
                submissionRecords.take(2).forEach { rec ->
                    Text(
                        text = "Receipt [${rec.competitionSlug}]: ${rec.state.label}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }

        if (grants.isNotEmpty()) {
            Text(
                text = "Sensitive-Action Approval Grants",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            grants.forEach { grant ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${grant.actionType} -> ${grant.destinationIdentity}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "SHA-256 ${grant.artifactSha256.take(12)}… · Uses ${grant.usesConsumed}/${grant.maxUses}",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        if (!grant.approvedByOwner) {
                            Button(
                                onClick = {
                                    container.approvalGate.confirmByOwner(grant.id)
                                    localMutation++
                                    onMutated()
                                },
                            ) {
                                Text("Confirm")
                            }
                        } else {
                            Text("Approved", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        Text(
            text = "Persistent Waiting Queue",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (jobs.isEmpty()) {
            Text(text = "No queued jobs.", style = MaterialTheme.typography.bodySmall)
        }
        jobs.forEach { job ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "${job.title} · ${job.state.label}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(text = job.statusDetail, style = MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (job.state == QueuedJobState.WAITING_FOR_APPROVAL) {
                            Button(
                                onClick = {
                                    container.waitingQueue.approveJob(job.id, container.currentQueueEnvironment())
                                    localMutation++
                                    onMutated()
                                },
                            ) {
                                Text("Approve")
                            }
                        }
                        if (job.state == QueuedJobState.UNCERTAIN_EXTERNAL_OUTCOME) {
                            OutlinedButton(
                                onClick = {
                                    container.waitingQueue.reconcileUncertainOutcome(
                                        jobId = job.id,
                                        externalSucceeded = false,
                                        note = "Marked failed after owner inspection.",
                                    )
                                    localMutation++
                                    onMutated()
                                },
                            ) {
                                Text("Mark Failed")
                            }
                        }
                        if (job.state != QueuedJobState.COMPLETED && job.state != QueuedJobState.CANCELLED) {
                            TextButton(
                                onClick = {
                                    container.waitingQueue.cancelJob(job.id)
                                    localMutation++
                                    onMutated()
                                },
                            ) {
                                Text("Cancel")
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider()
        Text(
            text = "Actual Action Timeline",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (timelineEvents.isEmpty()) {
            Text(
                text = "No actions have run yet.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        timelineEvents.forEach { evt ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "[${evt.stage.label}] ${evt.outcome.label} · ${evt.actor.label}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(text = evt.summary, style = MaterialTheme.typography.bodySmall)
                    evt.targetReference?.let { ref ->
                        Text(text = ref, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    title: String,
    detail: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                modifier = Modifier.padding(top = 6.dp),
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

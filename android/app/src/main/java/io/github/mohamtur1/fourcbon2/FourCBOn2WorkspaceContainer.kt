package io.github.mohamtur1.fourcbon2

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.File

/**
 * Shared local service container wiring Batch 1 + Batch 2 modules:
 * - Batch 1: 09a, 11, 15, 16, 17, 18, 19
 * - Batch 2: 12, 13, 14, 20, 21, 22, 23, 24, 25, 26, 27, 32
 */
class FourCBOn2WorkspaceContainer(
    val database: FourCBOn2Database,
    val credentialStore: CredentialStore,
    workspaceFilesDir: File,
    private val networkCheck: () -> Boolean = { false },
    intentLauncher: PlatformIntentLauncher = PlatformIntentLauncher { false },
    customTabDriver: CustomTabPlatformDriver = object : CustomTabPlatformDriver {
        override fun openHttpsCustomTab(url: String, preferChromePackage: Boolean): BrowserOpenMode? = null
    },
) {
    // Batch 1
    val credentialEntry = CredentialEntryController(credentialStore)
    val agentDrafts = AgentDraftRepository(database)
    val workspaceFiles = ScopedWorkspaceFileManager(workspaceFilesDir, database)
    val knowledgeLibrary = OfflineKnowledgeLibrary(database).also {
        it.ensureStarterTutorials()
    }
    val localAnalyzer = LocalDataAnalyzer()
    val waitingQueue = PersistentWaitingQueue(database)
    val githubReadClient = GitHubRepositoryReadClient(credentialStore)
    val kaggleReadClient = KaggleCompetitionReadClient(credentialStore)

    // Batch 2
    val capabilityRegistry = SelectedAppCapabilityRegistry(database).also {
        it.ensureDefaultCatalog()
    }
    val scopeBroker = OnDeviceScopeBroker(database, capabilityRegistry)
    val takeoverLease = TakeoverLeaseController(database)
    val approvalGate = SensitiveActionApprovalGate(database)
    val observationBoundary = ConsentedObservationBoundary(database)
    val criticalGuard = CriticalPhoneActionGuard(database, approvalGate)
    val actionTimeline = ActionTimelineRepository(database)
    val intentExecutor = HarmlessAppIntentExecutor(
        leaseController = takeoverLease,
        criticalGuard = criticalGuard,
        scopeBroker = scopeBroker,
        timeline = actionTimeline,
        launcher = intentLauncher,
    )
    val githubCacheDiff = GitHubCacheAndDiffManager(database, credentialStore)
    val kaggleStatus = KaggleStatusRepository(database, kaggleReadClient).also { repo ->
        if (repo.listCompetitions().isEmpty()) {
            repo.trackCompetition(
                slug = "tabular-playground",
                title = "Tabular Playground Baseline",
                evaluationMetric = "ROC-AUC",
                submissionCapPerDay = 5,
            )
        }
    }
    val customTabLauncher = ChromeCustomTabLauncher(customTabDriver, actionTimeline)
    val browserWorkspace = ControlledBrowserWorkspace(
        timeline = actionTimeline,
        observationBoundary = observationBoundary,
        knowledgeLibrary = knowledgeLibrary,
    )

    // Batch 3
    val jobBundleManager = SharedJobBundleManager(workspaceFiles)
    val colabAiBridge = ColabBuiltInAiBridge(database)
    val boundedAgentLoop = BoundedAgentLoopRunner(
        leaseController = takeoverLease,
        knowledgeLibrary = knowledgeLibrary,
        localAnalyzer = localAnalyzer,
        timeline = actionTimeline,
    )
    val routineEngine = HumanDefinedRoutineEngine(
        capabilityRegistry = capabilityRegistry,
        knowledgeLibrary = knowledgeLibrary,
        intentExecutor = intentExecutor,
        leaseController = takeoverLease,
        timeline = actionTimeline,
    )
    val personalLabRunner = ConditionalPersonalLabUiRunner(
        leaseController = takeoverLease,
        scopeBroker = scopeBroker,
        timeline = actionTimeline,
    )
    val actionRouter = PhoneControlActionRouter()

    // Batch 4
    val privacyInjectionGuard = PrivacyAndPromptInjectionGuard(observationBoundary)
    val kagglePreparePipeline = KagglePrepareOnlyPipeline()
    val kaggleSubmissionGate = KaggleSubmissionGate(
        database = database,
        approvalGate = approvalGate,
        leaseController = takeoverLease,
        timeline = actionTimeline,
    )
    val improvementLab = MinimalImprovementLab(database, kagglePreparePipeline)

    fun currentQueueEnvironment(
        isColabRuntimeVerified: Boolean =
            colabAiBridge.currentState() == ColabBuiltInAiState.VERIFIED_VIA_COLAB_RECEIPT,
    ): QueueEnvironmentSnapshot =
        QueueEnvironmentSnapshot(
            isNetworkAvailable = networkCheck(),
            configuredCredentials = credentialEntry.configuredSlots(),
            isColabRuntimeVerified = isColabRuntimeVerified,
        )

    companion object {
        fun createForAndroid(context: Context): FourCBOn2WorkspaceContainer {
            val appCtx = context.applicationContext
            val backend: LocalStoreBackend = runCatching { AndroidSqliteBackend(appCtx) }
                .getOrElse { DiskDirectoryBackend(File(appCtx.filesDir, "local_db_fallback")) }
            val credStore: CredentialStore = runCatching { AndroidKeyStoreCredentialStore(appCtx) }
                .getOrElse { InMemoryCredentialStore() }
            val db = FourCBOn2Database(backend)
            val container = FourCBOn2WorkspaceContainer(
                database = db,
                credentialStore = credStore,
                workspaceFilesDir = File(appCtx.filesDir, "workspace"),
                networkCheck = { hasValidatedInternet(appCtx) },
                intentLauncher = AndroidPlatformIntentLauncher(appCtx),
                customTabDriver = AndroidCustomTabPlatformDriver(appCtx),
            )
            container.waitingQueue.recoverAfterRestart(container.currentQueueEnvironment())
            return container
        }

        fun createForTest(
            rootDir: File,
            isNetworkAvailable: Boolean = false,
            intentLauncher: PlatformIntentLauncher = PlatformIntentLauncher { true },
            customTabDriver: CustomTabPlatformDriver = object : CustomTabPlatformDriver {
                override fun openHttpsCustomTab(url: String, preferChromePackage: Boolean): BrowserOpenMode =
                    BrowserOpenMode.CHROME_CUSTOM_TAB
            },
        ): FourCBOn2WorkspaceContainer {
            val dbDir = File(rootDir, "db")
            val filesDir = File(rootDir, "files")
            val db = FourCBOn2Database(DiskDirectoryBackend(dbDir))
            return FourCBOn2WorkspaceContainer(
                database = db,
                credentialStore = InMemoryCredentialStore(),
                workspaceFilesDir = filesDir,
                networkCheck = { isNetworkAvailable },
                intentLauncher = intentLauncher,
                customTabDriver = customTabDriver,
            )
        }

        private fun hasValidatedInternet(context: Context): Boolean = runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val active = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(active) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }.getOrDefault(false)
    }
}

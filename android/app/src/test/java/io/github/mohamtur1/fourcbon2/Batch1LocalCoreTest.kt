package io.github.mohamtur1.fourcbon2

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Batch1LocalCoreTest {
    private fun tempDir(prefix: String): File = Files.createTempDirectory(prefix).toFile()

    // Division 11: Local database persistence & restart recovery
    @Test
    fun localDatabasePersistsAcrossSimulatedRestartAndHandlesCorruptTableFile() {
        val dir = tempDir("4cbon2_db_test")
        try {
            val firstInstance = FourCBOn2Database(DiskDirectoryBackend(dir))
            firstInstance.putSetting("schema_check", "ok_v1", nowEpochMs = 100L)
            firstInstance.backend.upsert("custom_ns", "row_1", """{"hello":"world"}""", 200L)

            // Simulate process restart by opening a fresh backend on the same directory
            val restartedInstance = FourCBOn2Database(DiskDirectoryBackend(dir))
            assertEquals("ok_v1", restartedInstance.getSetting("schema_check"))
            assertEquals("""{"hello":"world"}""", restartedInstance.backend.get("custom_ns", "row_1")?.payloadJson)

            assertTrue(restartedInstance.backend.delete("custom_ns", "row_1"))
            assertNull(FourCBOn2Database(DiskDirectoryBackend(dir)).backend.get("custom_ns", "row_1"))

            // Corrupt file resilience
            File(dir, "broken_ns.table.json").writeText("{corrupt json", Charsets.UTF_8)
            assertTrue(restartedInstance.backend.listByNamespace("broken_ns").isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    // Division 15: Offline Agent Drafts
    @Test
    fun agentDraftsSaveEditReloadAndEnforceCaps() {
        val dir = tempDir("4cbon2_drafts_test")
        try {
            val repo1 = AgentDraftRepository(FourCBOn2Database(DiskDirectoryBackend(dir)))
            val fromTemplate = repo1.instantiateFromTemplate(
                BuiltInAgentTemplates.templates.first(),
                nowEpochMs = 1_000L,
            )
            val edited = repo1.save(
                fromTemplate.copy(
                    goal = "Validate local tabular features offline",
                    caps = ExecutionCaps(maxSteps = 18, maxRuntimeMinutes = 25, maxCostUsd = 0.0, allowNetwork = false, allowCloudAi = false),
                    state = AgentDraftState.READY_TO_QUEUE,
                ),
                nowEpochMs = 2_000L,
            )

            // Reopen after restart
            val repo2 = AgentDraftRepository(FourCBOn2Database(DiskDirectoryBackend(dir)))
            val loaded = repo2.load(edited.id)!!
            assertEquals("Validate local tabular features offline", loaded.goal)
            assertEquals(18, loaded.caps.maxSteps)
            assertEquals(AgentDraftState.READY_TO_QUEUE, loaded.state)
            assertEquals(1, repo2.listAll().size)
        } finally {
            dir.deleteRecursively()
        }
    }

    // Division 16: Safe File Import/Export
    @Test
    fun scopedWorkspaceFilesSanitizePathsEnforceCapsAndVerifyIntegrityOnExport() {
        val dir = tempDir("4cbon2_files_test")
        try {
            val db = FourCBOn2Database(DiskDirectoryBackend(File(dir, "db")))
            val manager = ScopedWorkspaceFileManager(
                workspaceRoot = File(dir, "ws"),
                database = db,
                maxImportBytes = 64L,
            )

            // Path traversal attempt is sanitized to safe leaf filename
            val imported = manager.importUtf8Text("../../etc/evil..name.csv", "a,b\n1,2\n", "text/csv", 500L)
            assertTrue(imported is FileImportOutcome.Imported)
            val record = (imported as FileImportOutcome.Imported).record
            assertTrue(!record.displayName.contains("..") || !record.displayName.contains("/"))
            assertEquals("a,b\n1,2\n", manager.readVerifiedUtf8Text(record.id))

            // Export round-trip
            val outBuffer = java.io.ByteArrayOutputStream()
            val exported = manager.exportFile(record.id, outBuffer)
            assertEquals(FileExportOutcome.Exported(8L, record.sha256Hex), exported)
            assertEquals("a,b\n1,2\n", outBuffer.toString(Charsets.UTF_8.name()))

            // Exceeding byte cap is rejected cleanly
            val oversized = manager.importUtf8Text("huge.txt", "x".repeat(120))
            assertEquals(FileImportOutcome.ExceededSizeLimit(64L), oversized)

            // On-disk tampering triggers integrity failure
            val internalFile = File(File(dir, "ws/scoped_imports"), record.storedFileName)
            internalFile.writeText("tampered", Charsets.UTF_8)
            assertNull(manager.readVerifiedBytes(record.id))
            assertEquals(
                FileExportOutcome.IntegrityCheckFailed,
                manager.exportFile(record.id, java.io.ByteArrayOutputStream()),
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    // Division 17: Offline Knowledge Library
    @Test
    fun offlineKnowledgeLibrarySeedsTutorialsAndRanksLocalEvidenceTruthfully() {
        val dir = tempDir("4cbon2_kb_test")
        try {
            val lib = OfflineKnowledgeLibrary(FourCBOn2Database(DiskDirectoryBackend(dir)))
            lib.ensureStarterTutorials(nowEpochMs = 100L)
            val tutorials = lib.listEntries(KnowledgeProvenance.SYSTEM_TUTORIAL)
            assertEquals(4, tutorials.size)

            lib.saveEntry(
                title = "Stratified K-Fold Validation Notes",
                body = "Use 5-fold stratified cross validation to avoid target leakage on imbalanced binary labels.",
                provenance = KnowledgeProvenance.COMPETITION_BRIEF,
                tags = listOf("cv", "validation", "tabular"),
                nowEpochMs = 500L,
            )

            val hits = lib.search("stratified leakage")
            assertTrue(hits.isNotEmpty())
            assertEquals("Stratified K-Fold Validation Notes", hits.first().entry.title)
            assertEquals(KnowledgeSearchHit.ENGINE_LABEL_OFFLINE_LIBRARY, hits.first().engineLabel)
        } finally {
            dir.deleteRecursively()
        }
    }

    // Division 18: Small Local CSV & JSON Analysis
    @Test
    fun localDataAnalyzerHandlesValidAndInvalidCsvAndJsonFixtures() {
        val analyzer = LocalDataAnalyzer(maxInputBytes = 4096, maxRows = 20)

        val validCsv = listOf(
            "id,metric,label,active",
            "1,0.50,\"alpha, one\",true",
            "2,1.50,\"beta \"\"quoted\"\"\",false",
            "3,NA,gamma,true",
        ).joinToString("\n")

        val csvRes = analyzer.analyzeCsv(validCsv, "metrics.csv")
        assertTrue(csvRes is LocalAnalysisOutcome.Success)
        val report = (csvRes as LocalAnalysisOutcome.Success).report
        assertEquals(3, report.rowCount)
        assertEquals(4, report.columnCount)
        val metricCol = report.columns.first { it.name == "metric" }
        assertEquals(InferredColumnType.NUMERIC, metricCol.inferredType)
        assertEquals(2, metricCol.nonEmptyCount)
        assertEquals(1, metricCol.missingCount)
        assertEquals(1.0, metricCol.numericSummary!!.mean, 1e-6)
        assertEquals(InferredColumnType.BOOLEAN, report.columns.first { it.name == "active" }.inferredType)

        // Invalid CSV fixtures
        assertTrue(analyzer.analyzeCsv("", "empty.csv") is LocalAnalysisOutcome.InvalidInput)
        assertTrue(analyzer.analyzeCsv("a,b\n1,2,3", "ragged.csv") is LocalAnalysisOutcome.InvalidInput)
        assertTrue(analyzer.analyzeCsv("a,b\n\"unclosed,2", "badquote.csv") is LocalAnalysisOutcome.InvalidInput)

        // Valid JSON fixture
        val validJson = """
            {"rows":[
                {"fold":1,"rmse":0.42},
                {"fold":2,"rmse":0.38}
            ]}
        """.trimIndent()
        val jsonRes = analyzer.analyzeJson(validJson, "folds.json")
        assertTrue(jsonRes is LocalAnalysisOutcome.Success)
        val jsonReport = (jsonRes as LocalAnalysisOutcome.Success).report
        assertEquals(2, jsonReport.rowCount)
        assertEquals(0.40, jsonReport.columns.first { it.name == "rmse" }.numericSummary!!.mean, 1e-6)

        // Invalid JSON fixtures
        assertTrue(analyzer.analyzeJson("{bad", "bad.json") is LocalAnalysisOutcome.InvalidInput)
        assertTrue(analyzer.analyzeJson("[]", "empty.json") is LocalAnalysisOutcome.InvalidInput)
    }

    // Division 19: Persistent Waiting Queue & No-Auto-Replay Safety Invariant
    @Test
    fun waitingQueueDefersOfflineTasksAndNeverAutoReplaysInterruptedExternalActions() {
        val dir = tempDir("4cbon2_queue_test")
        try {
            val db1 = FourCBOn2Database(DiskDirectoryBackend(dir))
            val drafts = AgentDraftRepository(db1)
            val queue1 = PersistentWaitingQueue(db1)

            val kaggleDraft = drafts.createDraft(
                title = "Kaggle Submission Candidate",
                goal = "Validate and submit tabular predictions",
                profile = JobProfile.COMPETITION_SOLVER,
                caps = ExecutionCaps(maxSteps = 10, allowNetwork = true, allowCloudAi = false),
                nowEpochMs = 1_000L,
            )

            // Enqueue while offline -> WAITING_FOR_INTERNET
            val offlineEnv = QueueEnvironmentSnapshot(
                isNetworkAvailable = false,
                configuredCredentials = emptySet(),
                isColabRuntimeVerified = false,
            )
            val job = queue1.enqueueFromDraft(
                draft = kaggleDraft,
                env = offlineEnv,
                requiredCredentialSlot = CredentialSlot.KAGGLE_BEARER_ACCESS_TOKEN,
                requiresColabRuntime = false,
                consequentialExternalAction = true,
                nowEpochMs = 1_100L,
            )
            assertEquals(QueuedJobState.WAITING_FOR_INTERNET, job.state)

            // Internet comes back, still missing Kaggle credential -> WAITING_FOR_ACCOUNT
            val onlineNoCreds = offlineEnv.copy(isNetworkAvailable = true)
            queue1.refreshReadiness(onlineNoCreds, nowEpochMs = 1_200L)
            assertEquals(QueuedJobState.WAITING_FOR_ACCOUNT, queue1.getJob(job.id)!!.state)

            // Kaggle credential added -> WAITING_FOR_APPROVAL (since consequentialExternalAction = true)
            val onlineWithCreds = onlineNoCreds.copy(
                configuredCredentials = setOf(CredentialSlot.KAGGLE_API_TOKEN),
            )
            queue1.refreshReadiness(onlineWithCreds, nowEpochMs = 1_300L)
            assertEquals(QueuedJobState.WAITING_FOR_APPROVAL, queue1.getJob(job.id)!!.state)

            // Owner approves -> READY -> mark RUNNING
            val approved = queue1.approveJob(job.id, onlineWithCreds, nowEpochMs = 1_400L)!!
            assertEquals(QueuedJobState.READY, approved.state)
            queue1.markRunning(job.id, nowEpochMs = 1_500L)
            assertEquals(QueuedJobState.RUNNING, queue1.getJob(job.id)!!.state)

            // Simulate app crash & restart while external action was RUNNING:
            // MUST become UNCERTAIN_EXTERNAL_OUTCOME and stay that way even on refreshReadiness!
            val db2 = FourCBOn2Database(DiskDirectoryBackend(dir))
            val queue2 = PersistentWaitingQueue(db2)
            queue2.recoverAfterRestart(onlineWithCreds, nowEpochMs = 2_000L)
            assertEquals(QueuedJobState.UNCERTAIN_EXTERNAL_OUTCOME, queue2.getJob(job.id)!!.state)

            queue2.refreshReadiness(onlineWithCreds, nowEpochMs = 2_500L)
            assertEquals(QueuedJobState.UNCERTAIN_EXTERNAL_OUTCOME, queue2.getJob(job.id)!!.state)
        } finally {
            dir.deleteRecursively()
        }
    }
}

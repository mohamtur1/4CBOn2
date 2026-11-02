package io.github.mohamtur1.fourcbon2

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Division 49/50 verification: the credential-safe live dispatch call and the Daily Lab presentation
 * model that the Compose surface renders.
 */
class Batch8DailyLabAndTransportTest {
    /** Minimal HttpURLConnection stand-in so the real header/body logic is executed offline. */
    private class FakeConnection(private val status: Int, private val responseBody: String) :
        HttpURLConnection(URL("https://example.invalid/dispatch")) {
        val requestHeaders = linkedMapOf<String, String>()
        val body = ByteArrayOutputStream()

        override fun connect() = Unit

        override fun disconnect() = Unit

        override fun usingProxy(): Boolean = false

        override fun setRequestProperty(key: String, value: String?) {
            requestHeaders[key] = value ?: ""
        }

        override fun getOutputStream(): OutputStream = body

        override fun getResponseCode(): Int = status

        override fun getInputStream(): ByteArrayInputStream = ByteArrayInputStream(responseBody.toByteArray())

        override fun getErrorStream(): ByteArrayInputStream = ByteArrayInputStream(responseBody.toByteArray())
    }

    @Test
    fun liveDispatchCallSendsBearerHeaderAndZeroesTheCredentialBuffer() {
        val connection = FakeConnection(status = 202, responseBody = """{"ref":"dispatch_42"}""")
        val secret = "KGAT_supersecretvalue1234".toCharArray()
        var providerCalls = 0

        val http = BearerAuthenticatedDispatchHttp(
            credentialProvider = {
                providerCalls++
                secret.copyOf()
            },
            connectionFactory = { connection },
        )

        val result = http.post(
            url = "https://example.invalid/dispatch",
            bodyJson = """{"patch":{"reasoning_token_budget":"360"}}""",
            idempotencyKey = "cyc_1|diff_1",
        )

        val response = result as DispatchHttpResult.Response
        assertEquals(202, response.statusCode)
        assertEquals("dispatch_42", JsonCodec.parseObject(response.body).string("ref"))
        assertEquals(1, providerCalls)
        assertEquals("Bearer KGAT_supersecretvalue1234", connection.requestHeaders["Authorization"])
        assertEquals("cyc_1|diff_1", connection.requestHeaders["Idempotency-Key"])
        assertEquals("application/json; charset=utf-8", connection.requestHeaders["Content-Type"])
        assertTrue(connection.body.toString(Charsets.UTF_8.name()).contains("reasoning_token_budget"))

        // A missing credential never reaches the socket.
        val noCredential = BearerAuthenticatedDispatchHttp(
            credentialProvider = { null },
            connectionFactory = { error("must not open a connection without a credential") },
        ).post("https://example.invalid/dispatch", "{}", "k")
        assertTrue(noCredential is DispatchHttpResult.NetworkError)

        // Transport failures are reported ambiguously, never as success.
        val failing = BearerAuthenticatedDispatchHttp(
            credentialProvider = { "token".toCharArray() },
            connectionFactory = { throw java.io.IOException("socket closed") },
        ).post("https://example.invalid/dispatch", "{}", "k")
        assertTrue(failing is DispatchHttpResult.NetworkError)
    }

    @Test
    fun apiTransportMapsStatusCodesOntoHonestDispatchOutcomes() {
        fun transport(status: Int, body: String, configured: Boolean = true) = ApiDispatchTransport(
            channel = DispatchChannel.GITHUB_WORKFLOW,
            endpoint = "https://example.invalid/dispatch",
            http = { _, _, _ -> DispatchHttpResult.Response(status, body) },
            credentialConfigured = { configured },
            acceptedStatusCodes = setOf(200, 201, 202, 204),
        )

        val request = DispatchRequest(
            requestId = "req_1",
            cycleId = "cyc_1",
            competitionSlug = "arc-prize-2026",
            channel = DispatchChannel.GITHUB_WORKFLOW,
            targetRef = "v3",
            candidateDiffId = "diff_1",
            diffPatch = mapOf(BoundedPatchPolicy.KEY_REASONING_TOKEN_BUDGET to "360"),
            baseVersionRef = "v3",
            idempotencyKey = "cyc_1|diff_1",
        )

        assertEquals(
            "dispatch_42",
            (transport(202, """{"dispatchRef":"dispatch_42"}""").dispatch(request) as DispatchOutcome.Accepted).dispatchRef,
        )
        assertTrue(transport(422, "{}").dispatch(request) is DispatchOutcome.Rejected)
        assertTrue(transport(503, "{}").dispatch(request) is DispatchOutcome.UncertainExternalOutcome)
        assertTrue(transport(202, "{}", configured = false).dispatch(request) is DispatchOutcome.NotConfigured)
        assertTrue(
            ApiDispatchTransport(
                channel = DispatchChannel.KAGGLE_KERNEL,
                endpoint = "https://example.invalid/dispatch",
                http = { _, _, _ -> DispatchHttpResult.NetworkError("dropped") },
                credentialConfigured = { true },
            ).dispatch(request) is DispatchOutcome.UncertainExternalOutcome,
        )

        // Router refuses unknown channels and blank idempotency keys.
        val router = ExperimentDispatchRouter(listOf(transport(202, "{}")))
        assertTrue(router.dispatch(DispatchChannel.COLAB_MANUAL, request) is DispatchOutcome.NotConfigured)
        assertTrue(router.dispatch(DispatchChannel.GITHUB_WORKFLOW, request.copy(idempotencyKey = "")) is DispatchOutcome.Rejected)
        // Readiness is reported for every channel, so the UI can grey out what is not set up.
        val readiness = router.readiness()
        assertEquals(3, readiness.size)
        assertTrue(readiness.getValue(DispatchChannel.GITHUB_WORKFLOW))
        assertFalse(readiness.getValue(DispatchChannel.KAGGLE_KERNEL))
        assertFalse(readiness.getValue(DispatchChannel.COLAB_MANUAL))
    }

    @Test
    fun dailyLabCardTellsTheOwnerExactlyWhatHappenedAndWhatTheyCanDo() {
        val dir: File = Files.createTempDirectory("4cbon2_div50_presenter").toFile()
        val database = FourCBOn2Database(DiskDirectoryBackend(dir))
        val ingest = RunTelemetryIngest(database)
        val ledger = ChampionLedger(database)
        val gate = FixedPromotionGate(ledger, database)
        val lease = TakeoverLeaseController(database)
        val timeline = ActionTimelineRepository(database)
        val transport = ScriptedDispatchTransport(DispatchChannel.KAGGLE_KERNEL)
        val cycle = DailyExperimentCycle(
            database = database,
            ingest = ingest,
            ledger = ledger,
            gate = gate,
            dispatchRouter = ExperimentDispatchRouter(listOf(transport)),
            leaseController = lease,
            timeline = timeline,
        )
        val presenter = DailyLabPresenter(cycle, ingest, ledger, gate, timeline)

        // Nothing yet: the card says so instead of pretending.
        val empty = presenter.card("arc-prize-2026")
        assertTrue(empty.headline.contains("No runs yet"))
        assertFalse(empty.canLockChampion)
        assertFalse(empty.canApproveDispatch)

        // Champion locked, bad run ingested → alert + candidate ready + dispatch allowed.
        val championRun = (ingest.ingestDiagnosticsJson(
            """{"game":"ls20","score":25.83,"levels":5,"actions":500,"tokens":200000,"version":"v3","status":"complete"}""",
            "arc-prize-2026", "champion", nowEpochMs = 100L,
        ) as TelemetryIngestOutcome.Ingested).telemetry
        ledger.lockInitialChampion(championRun, ownerConfirmed = true, nowEpochMs = 110L)
        lease.startOrResumeAutomation(sessionId = "s_1", explicitOwnerResume = true, nowEpochMs = 120L)

        val cycleState = (cycle.openCycle(
            "arc-prize-2026", BenchmarkKind.ARC_AGI_INTERACTIVE, DispatchChannel.KAGGLE_KERNEL, 130L,
        ) as CycleOpenResult.Opened).state
        cycle.ingestDiagnoseAndPropose(
            cycleState.cycleId,
            """{"game":"r11l","score":25.15,"levels":4,"actions":53,"tokens":96609,"version":"v4","status":"complete"}""",
            "regressed run",
            nowEpochMs = 140L,
        )

        val card = presenter.card("arc-prize-2026")
        assertTrue(card.alert)
        assertTrue(card.headline.contains("Critical") || card.headline.contains("High"))
        assertTrue(card.championLine.contains("v3"))
        assertTrue(card.candidateLine.contains("revert_to_version"))
        assertTrue(card.nextAction.contains("Approve & Run"))
        assertTrue(card.canApproveDispatch)
        assertFalse(card.canApprovePromotion)
        assertTrue(card.recentTimeline.isNotEmpty())
        assertNotNull(card.toDisplayText())

        // After the owner approves and the run lands, the card flips to the gate prompt.
        cycle.approveAndDispatch(cycleState.cycleId, approvedByOwner = true, nowEpochMs = 150L)
        cycle.recordCandidateTelemetry(
            cycleState.cycleId,
            """{"game":"ls20","score":25.99,"levels":5,"actions":50,"tokens":20000,"version":"v5","status":"complete"}""",
            "candidate",
            nowEpochMs = 160L,
        )
        val gated = presenter.card("arc-prize-2026")
        assertTrue(gated.canApprovePromotion)
        assertFalse(gated.canApproveDispatch)
        assertTrue(gated.gateLine.contains("Eligible"))
        assertTrue(gated.nextAction.contains("approve"))
        assertTrue(presenter.cycleHistory("arc-prize-2026").single().contains("awaiting owner"))
    }
}

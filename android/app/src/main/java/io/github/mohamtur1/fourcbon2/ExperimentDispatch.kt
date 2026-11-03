package io.github.mohamtur1.fourcbon2

import java.util.Locale

/**
 * Division 49 (v0.3) — Experiment dispatch transports.
 *
 * v0.2 could only open a Colab/Kaggle URL and hope the owner pasted telemetry back. The daily loop
 * needs a *legitimate, owner-authorized* way to send one approved candidate diff to a runner and get
 * a reference back. Three channels are modelled:
 *
 *  - [DispatchChannel.KAGGLE_KERNEL]     — push the companion notebook version and poll its status.
 *  - [DispatchChannel.GITHUB_WORKFLOW]   — dispatch a repository workflow that runs the evaluator.
 *  - [DispatchChannel.COLAB_MANUAL]      — hand off to the owner in Chrome Custom Tabs (no API).
 *
 * Design rules:
 *  1. Transports are injected. Nothing here opens a socket, so every path is unit-testable and the
 *     Android layer supplies the real HTTP call with a Keystore-held credential.
 *  2. A missing credential produces [DispatchOutcome.NotConfigured] — never a fabricated success.
 *  3. Ambiguous transport failures produce [DispatchOutcome.UncertainExternalOutcome]; the caller
 *     must reconcile, never blindly retry (same rule as the Division 38 submission gate).
 */

enum class DispatchChannel(val label: String) {
    KAGGLE_KERNEL("Kaggle kernel push & poll"),
    GITHUB_WORKFLOW("GitHub Actions workflow"),
    COLAB_MANUAL("Colab manual handoff"),
}

data class DispatchRequest(
    val requestId: String,
    val cycleId: String,
    val competitionSlug: String,
    val channel: DispatchChannel,
    val targetRef: String,
    val candidateDiffId: String,
    val diffPatch: Map<String, String>,
    val baseVersionRef: String,
    val idempotencyKey: String,
    val dryRun: Boolean = true,
    val plannedMaxTokensPerAction: Double = 0.0,
) {
    fun toJson(): JsonValue.Obj = JsonValue.Obj(
        linkedMapOf(
            "requestId" to JsonValue.Str(requestId),
            "cycleId" to JsonValue.Str(cycleId),
            "competitionSlug" to JsonValue.Str(competitionSlug),
            "channel" to JsonValue.Str(channel.name),
            "targetRef" to JsonValue.Str(targetRef),
            "candidateDiffId" to JsonValue.Str(candidateDiffId),
            "diffPatch" to JsonValue.Obj(diffPatch.toSortedMap().mapValues { (_, v) -> JsonValue.Str(v) as JsonValue }),
            "baseVersionRef" to JsonValue.Str(baseVersionRef),
            "idempotencyKey" to JsonValue.Str(idempotencyKey),
            "dryRun" to JsonValue.Bool(dryRun),
            "plannedMaxTokensPerAction" to JsonValue.Num(plannedMaxTokensPerAction.coerceFinite()),
        ),
    )
}

sealed interface DispatchOutcome {
    /** The runner acknowledged the request; [dispatchRef] is what the next poll keys on. */
    data class Accepted(
        val dispatchRef: String,
        val channel: DispatchChannel,
        val note: String,
        val dryRun: Boolean = false,
    ) : DispatchOutcome

    data class Rejected(val reason: String) : DispatchOutcome

    /** The request may or may not have landed. Reconcile before any retry. */
    data class UncertainExternalOutcome(val note: String) : DispatchOutcome

    /** A credential or target is missing. Development continues; nothing is sent. */
    data class NotConfigured(val missing: String) : DispatchOutcome
}

/** A transport binding for one channel. Implementations must never log request bodies or secrets. */
interface ExperimentDispatchTransport {
    val channel: DispatchChannel

    fun isConfigured(): Boolean

    fun dispatch(request: DispatchRequest): DispatchOutcome
}

/** Outcome of the injected HTTP call, kept separate from transport policy so tests need no network. */
sealed interface DispatchHttpResult {
    data class Response(val statusCode: Int, val body: String) : DispatchHttpResult

    data class NetworkError(val message: String) : DispatchHttpResult
}

fun interface DispatchHttpCall {
    fun post(url: String, bodyJson: String, idempotencyKey: String): DispatchHttpResult
}

/**
 * Generic API transport used by the Kaggle and GitHub channels. The credential itself is resolved by
 * the injected [DispatchHttpCall]; this class only applies the retry/uncertainty policy.
 */
class ApiDispatchTransport(
    override val channel: DispatchChannel,
    private val endpoint: String,
    private val http: DispatchHttpCall,
    private val credentialConfigured: () -> Boolean,
    private val acceptedStatusCodes: Set<Int> = setOf(200, 201, 202),
) : ExperimentDispatchTransport {
    override fun isConfigured(): Boolean = credentialConfigured() && endpoint.isNotBlank()

    override fun dispatch(request: DispatchRequest): DispatchOutcome {
        if (!credentialConfigured()) {
            return DispatchOutcome.NotConfigured("A stored ${channel.label} credential is required.")
        }
        if (endpoint.isBlank()) {
            return DispatchOutcome.NotConfigured("No ${channel.label} endpoint is configured.")
        }
        val body = JsonCodec.stringify(request.toJson())
        return when (val result = http.post(endpoint, body, request.idempotencyKey)) {
            is DispatchHttpResult.NetworkError -> DispatchOutcome.UncertainExternalOutcome(
                "Dispatch to ${channel.label} did not complete (${result.message}); reconcile before retrying.",
            )
            is DispatchHttpResult.Response -> when {
                result.statusCode in acceptedStatusCodes -> DispatchOutcome.Accepted(
                    dispatchRef = extractDispatchRef(result.body) ?: request.idempotencyKey,
                    channel = channel,
                    note = "Accepted with HTTP ${result.statusCode}.",
                    dryRun = request.dryRun,
                )
                result.statusCode in 400..499 -> DispatchOutcome.Rejected(
                    "Runner rejected the request (HTTP ${result.statusCode}).",
                )
                else -> DispatchOutcome.UncertainExternalOutcome(
                    "Runner answered HTTP ${result.statusCode}; outcome is unknown, so reconcile before retrying.",
                )
            }
        }
    }

    private fun extractDispatchRef(body: String): String? {
        val parsed = runCatching { JsonCodec.parseObject(body) }.getOrNull() ?: return null
        return parsed.string("ref") ?: parsed.string("dispatchRef") ?: parsed.string("versionNumber")
    }
}

/**
 * Manual channel: the owner taps through to Colab/Kaggle in Chrome Custom Tabs. This transport never
 * claims a run happened — it returns an accepted handoff with `dryRun = true` semantics.
 */
class ManualHandoffDispatchTransport(
    private val url: String = "",
    private val urlBuilder: (DispatchRequest) -> String = { it.targetRef },
) : ExperimentDispatchTransport {
    override val channel: DispatchChannel = DispatchChannel.COLAB_MANUAL

    override fun isConfigured(): Boolean = true

    override fun dispatch(request: DispatchRequest): DispatchOutcome {
        val target = urlBuilder(request).ifBlank { url }
        if (target.isBlank()) {
            return DispatchOutcome.NotConfigured("No notebook URL is available for the manual handoff.")
        }
        return DispatchOutcome.Accepted(
            dispatchRef = "manual_${request.idempotencyKey}",
            channel = channel,
            note = "Handoff prepared for $target; the owner runs it and returns telemetry.",
            dryRun = true,
        )
    }
}

/** Scriptable transport for tests and for the "prepare-only" development path. */
class ScriptedDispatchTransport(
    override val channel: DispatchChannel,
    private val configured: Boolean = true,
    private val script: (DispatchRequest) -> DispatchOutcome = { request ->
        DispatchOutcome.Accepted(
            dispatchRef = "scripted_${request.idempotencyKey}",
            channel = channel,
            note = "Scripted acceptance.",
            dryRun = request.dryRun,
        )
    },
) : ExperimentDispatchTransport {
    val sentRequests = mutableListOf<DispatchRequest>()

    override fun isConfigured(): Boolean = configured

    override fun dispatch(request: DispatchRequest): DispatchOutcome {
        sentRequests += request
        return script(request)
    }
}

/** Routes a cycle's dispatch to the channel the owner approved. */
class ExperimentDispatchRouter(
    transports: List<ExperimentDispatchTransport>,
) {
    private val byChannel = transports.associateBy { it.channel }

    fun transportFor(channel: DispatchChannel): ExperimentDispatchTransport? = byChannel[channel]

    fun readiness(): Map<DispatchChannel, Boolean> = DispatchChannel.entries.associateWith { channel ->
        byChannel[channel]?.isConfigured() == true
    }

    fun dispatch(channel: DispatchChannel, request: DispatchRequest): DispatchOutcome {
        val transport = byChannel[channel]
            ?: return DispatchOutcome.NotConfigured("No transport is registered for ${channel.label}.")
        if (!transport.isConfigured()) {
            return DispatchOutcome.NotConfigured("${channel.label} is not configured on this device.")
        }
        if (request.idempotencyKey.isBlank()) {
            return DispatchOutcome.Rejected("Dispatch requires an idempotency key.")
        }
        return transport.dispatch(request)
    }

    private companion object {
        @Suppress("unused")
        fun describe(channel: DispatchChannel): String = channel.label.lowercase(Locale.US)
    }
}

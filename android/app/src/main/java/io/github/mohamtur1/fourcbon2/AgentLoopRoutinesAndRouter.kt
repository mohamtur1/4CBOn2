package io.github.mohamtur1.fourcbon2

/**
 * Divisions 31, 33, 34, and 36:
 * - Division 31: One bounded agent loop (Goal -> Permitted Action -> Verification, with clean
 *   stop/takeover and error/retry paths).
 * - Division 33: Human-defined routine engine (static deterministic script, explicit human trigger,
 *   precondition checks, bounded execution; blocks AI-initiated triggers).
 * - Division 34: Conditional personal-lab UI runner (strictly unavailable in Standard profile;
 *   only runs against eligible owner-controlled fixture apps when Lab profile + disclosure pass;
 *   never represented as Play-compliant).
 * - Division 36: Online/offline phone-control routing (least-powerful adequate route, no fake
 *   cloud planning, no locked-device bypass, no silent alternate model provider).
 */

// --- Division 31: One Bounded Agent Loop ---

sealed interface BoundedLoopStepSpec {
    data class SearchLocalKnowledge(val query: String, val minHitsRequired: Int = 1) : BoundedLoopStepSpec
    data class AnalyzeCsvFixture(val datasetName: String, val rawCsv: String) : BoundedLoopStepSpec
    data class SaveVerifiedSummary(val title: String, val tags: List<String> = listOf("agent-loop")) : BoundedLoopStepSpec
}

sealed interface BoundedLoopOutcome {
    data class CompletedVerified(
        val stepsExecuted: Int,
        val savedKnowledgeId: String?,
        val verificationNote: String,
    ) : BoundedLoopOutcome

    data class StoppedByTakeoverOrCap(
        val stepsExecuted: Int,
        val reason: String,
    ) : BoundedLoopOutcome

    data class FailedVerificationOrError(
        val stepsExecuted: Int,
        val reason: String,
    ) : BoundedLoopOutcome
}

class BoundedAgentLoopRunner(
    private val leaseController: TakeoverLeaseController,
    private val knowledgeLibrary: OfflineKnowledgeLibrary,
    private val localAnalyzer: LocalDataAnalyzer,
    private val timeline: ActionTimelineRepository,
) {
    /**
     * Executes a bounded `Goal -> Permitted Action -> Verification` workflow locally while
     * checking [TakeoverLeaseController] and `maxSteps` before every single step.
     */
    fun runBoundedWorkflow(
        jobId: String,
        goal: String,
        sessionId: String,
        leaseEpoch: Long,
        steps: List<BoundedLoopStepSpec>,
        maxSteps: Int = 6,
        onStepHook: ((stepIndex: Int) -> Unit)? = null,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): BoundedLoopOutcome {
        if (steps.isEmpty()) {
            return BoundedLoopOutcome.FailedVerificationOrError(0, "Workflow contains no steps.")
        }
        val effectiveCap = maxSteps.coerceIn(1, 50)
        timeline.recordEvent(
            stage = TimelineStage.PLAN,
            actor = TimelineActor.LOCAL_ROUTINE,
            summary = "Planned bounded workflow (${steps.size} steps, cap=$effectiveCap) for goal: $goal",
            outcome = TimelineOutcomeState.ATTEMPTED,
            jobId = jobId,
            nowEpochMs = nowEpochMs,
        )

        val observations = ArrayList<String>()
        var savedEntryId: String? = null
        var executed = 0

        for ((index, step) in steps.withIndex()) {
            onStepHook?.invoke(index)

            if (executed >= effectiveCap) {
                val msg = "Stopped at maxSteps cap ($effectiveCap) before completing all steps."
                timeline.recordEvent(
                    stage = TimelineStage.VERIFICATION,
                    actor = TimelineActor.LOCAL_BROKER,
                    summary = msg,
                    outcome = TimelineOutcomeState.BLOCKED,
                    jobId = jobId,
                    nowEpochMs = nowEpochMs + index,
                )
                return BoundedLoopOutcome.StoppedByTakeoverOrCap(executed, msg)
            }

            when (val lease = leaseController.validateCommand(sessionId, leaseEpoch)) {
                is LeaseValidationOutcome.RejectedStaleOrCancelled -> {
                    timeline.recordEvent(
                        stage = TimelineStage.TAKEOVER_OR_STOP,
                        actor = TimelineActor.LOCAL_BROKER,
                        summary = "Agent loop halted cleanly at step ${index + 1}: ${lease.reason}",
                        outcome = TimelineOutcomeState.BLOCKED,
                        jobId = jobId,
                        nowEpochMs = nowEpochMs + index,
                    )
                    return BoundedLoopOutcome.StoppedByTakeoverOrCap(executed, lease.reason)
                }
                is LeaseValidationOutcome.Valid -> Unit
            }

            executed++
            when (step) {
                is BoundedLoopStepSpec.SearchLocalKnowledge -> {
                    val hits = knowledgeLibrary.search(step.query)
                    if (hits.size < step.minHitsRequired) {
                        val reason = "Verification failed: expected >= ${step.minHitsRequired} knowledge hits for '${step.query}', got ${hits.size}."
                        timeline.recordEvent(
                            stage = TimelineStage.VERIFICATION,
                            actor = TimelineActor.LOCAL_ROUTINE,
                            summary = reason,
                            outcome = TimelineOutcomeState.FAILED,
                            jobId = jobId,
                            nowEpochMs = nowEpochMs + index,
                        )
                        return BoundedLoopOutcome.FailedVerificationOrError(executed, reason)
                    }
                    val note = "Matched ${hits.size} local passage(s); top='${hits.first().entry.title}'."
                    observations.add(note)
                    timeline.recordEvent(
                        stage = TimelineStage.OBSERVATION,
                        actor = TimelineActor.LOCAL_ROUTINE,
                        summary = note,
                        outcome = TimelineOutcomeState.VERIFIED,
                        jobId = jobId,
                        nowEpochMs = nowEpochMs + index,
                    )
                }

                is BoundedLoopStepSpec.AnalyzeCsvFixture -> {
                    when (val res = localAnalyzer.analyzeCsv(step.rawCsv, step.datasetName)) {
                        is LocalAnalysisOutcome.InvalidInput -> {
                            val reason = "CSV analysis error: ${res.reason}"
                            timeline.recordEvent(
                                stage = TimelineStage.ACTION,
                                actor = TimelineActor.LOCAL_ROUTINE,
                                summary = reason,
                                outcome = TimelineOutcomeState.FAILED,
                                jobId = jobId,
                                nowEpochMs = nowEpochMs + index,
                            )
                            return BoundedLoopOutcome.FailedVerificationOrError(executed, reason)
                        }
                        is LocalAnalysisOutcome.Success -> {
                            observations.add(res.report.toMarkdownSummary())
                            timeline.recordEvent(
                                stage = TimelineStage.VERIFICATION,
                                actor = TimelineActor.LOCAL_ROUTINE,
                                summary = "Verified '${step.datasetName}': ${res.report.rowCount} rows, ${res.report.columnCount} columns.",
                                outcome = TimelineOutcomeState.VERIFIED,
                                jobId = jobId,
                                nowEpochMs = nowEpochMs + index,
                            )
                        }
                    }
                }

                is BoundedLoopStepSpec.SaveVerifiedSummary -> {
                    if (observations.isEmpty()) {
                        val reason = "Cannot save verified summary without prior verified observations."
                        timeline.recordEvent(
                            stage = TimelineStage.VERIFICATION,
                            actor = TimelineActor.LOCAL_ROUTINE,
                            summary = reason,
                            outcome = TimelineOutcomeState.FAILED,
                            jobId = jobId,
                            nowEpochMs = nowEpochMs + index,
                        )
                        return BoundedLoopOutcome.FailedVerificationOrError(executed, reason)
                    }
                    val saved = knowledgeLibrary.saveEntry(
                        title = step.title,
                        body = "Goal: $goal\n\n" + observations.joinToString("\n\n"),
                        provenance = KnowledgeProvenance.LOCAL_ANALYSIS,
                        tags = step.tags,
                        nowEpochMs = nowEpochMs + index,
                    )
                    savedEntryId = saved.id
                    timeline.recordEvent(
                        stage = TimelineStage.VERIFICATION,
                        actor = TimelineActor.LOCAL_ROUTINE,
                        summary = "Saved verified output '${saved.title}' (${saved.id}, SHA-256 ${saved.contentSha256.take(10)}…).",
                        outcome = TimelineOutcomeState.VERIFIED,
                        jobId = jobId,
                        nowEpochMs = nowEpochMs + index,
                    )
                }
            }
        }

        return BoundedLoopOutcome.CompletedVerified(
            stepsExecuted = executed,
            savedKnowledgeId = savedEntryId,
            verificationNote = "Completed $executed bounded step(s) with verified evidence.",
        )
    }
}

// --- Division 33: Human-Defined Routine Engine ---

enum class RoutineTriggerSource {
    EXPLICIT_HUMAN_TAP,
    AI_PLANNER_ATTEMPT,
    BACKGROUND_TIMER_ATTEMPT,
}

data class RoutinePreconditions(
    val requireUnlockedDevice: Boolean = true,
    val requiredSelectedPackages: Set<String> = emptySet(),
    val requiredKnowledgeEntryId: String? = null,
)

sealed interface RoutineActionStep {
    data class VerifyKnowledgeExists(val query: String) : RoutineActionStep
    data class PrepareShareDraftIntent(val targetPackage: String, val subject: String, val body: String) :
        RoutineActionStep
}

data class HumanDefinedRoutine(
    val id: String,
    val title: String,
    val preconditions: RoutinePreconditions,
    val steps: List<RoutineActionStep>,
    val maxSteps: Int = 5,
) {
    init {
        require(title.isNotBlank()) { "Routine title must not be blank." }
        require(steps.isNotEmpty() && steps.size <= maxSteps) {
            "Routine steps (${steps.size}) must be non-empty and within maxSteps ($maxSteps)."
        }
    }
}

sealed interface RoutineExecutionOutcome {
    data class Completed(val stepsCompleted: Int) : RoutineExecutionOutcome
    data class RejectedPolicyOrTrigger(val reason: String) : RoutineExecutionOutcome
    data class PreconditionFailed(val reason: String) : RoutineExecutionOutcome
    data class StoppedMidRoutine(val stepsCompleted: Int, val reason: String) : RoutineExecutionOutcome
}

class HumanDefinedRoutineEngine(
    private val capabilityRegistry: SelectedAppCapabilityRegistry,
    private val knowledgeLibrary: OfflineKnowledgeLibrary,
    private val intentExecutor: HarmlessAppIntentExecutor,
    private val leaseController: TakeoverLeaseController,
    private val timeline: ActionTimelineRepository,
) {
    /**
     * Executes a static, human-defined script ONLY when triggered directly by the human owner.
     * Never acts as an indirect AI-driven Accessibility loophole.
     */
    fun runRoutine(
        routine: HumanDefinedRoutine,
        triggerSource: RoutineTriggerSource,
        isDeviceUnlocked: Boolean,
        sessionId: String,
        leaseEpoch: Long,
        scopeContractId: String,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): RoutineExecutionOutcome {
        if (triggerSource != RoutineTriggerSource.EXPLICIT_HUMAN_TAP) {
            val reason = "Human-defined routines require EXPLICIT_HUMAN_TAP; rejected ${triggerSource.name}."
            timeline.recordEvent(
                stage = TimelineStage.APPROVAL_CHECK,
                actor = TimelineActor.LOCAL_BROKER,
                summary = reason,
                outcome = TimelineOutcomeState.BLOCKED,
                targetReference = routine.id,
                nowEpochMs = nowEpochMs,
            )
            return RoutineExecutionOutcome.RejectedPolicyOrTrigger(reason)
        }

        if (routine.preconditions.requireUnlockedDevice && !isDeviceUnlocked) {
            return RoutineExecutionOutcome.PreconditionFailed("Device is locked; routine requires unlocked foreground state.")
        }

        for (pkg in routine.preconditions.requiredSelectedPackages) {
            val app = capabilityRegistry.getApp(pkg)
            if (app == null || !app.ownerSelected || app.protectedManualOnly) {
                return RoutineExecutionOutcome.PreconditionFailed("Required app '$pkg' is not selected by owner.")
            }
        }

        val reqKbId = routine.preconditions.requiredKnowledgeEntryId
        if (reqKbId != null && knowledgeLibrary.getEntry(reqKbId) == null) {
            return RoutineExecutionOutcome.PreconditionFailed("Required knowledge entry '$reqKbId' is missing.")
        }

        var completed = 0
        for (step in routine.steps) {
            when (val lease = leaseController.validateCommand(sessionId, leaseEpoch)) {
                is LeaseValidationOutcome.RejectedStaleOrCancelled ->
                    return RoutineExecutionOutcome.StoppedMidRoutine(completed, lease.reason)
                is LeaseValidationOutcome.Valid -> Unit
            }

            when (step) {
                is RoutineActionStep.VerifyKnowledgeExists -> {
                    if (knowledgeLibrary.search(step.query).isEmpty()) {
                        return RoutineExecutionOutcome.StoppedMidRoutine(
                            completed,
                            "Routine step failed: no knowledge match for '${step.query}'.",
                        )
                    }
                    completed++
                }
                is RoutineActionStep.PrepareShareDraftIntent -> {
                    val res = intentExecutor.execute(
                        sessionId = sessionId,
                        commandEpoch = leaseEpoch,
                        scopeContractId = scopeContractId,
                        spec = HarmlessIntentSpec.SharePlainTextDraft(
                            targetPackage = step.targetPackage,
                            subject = step.subject,
                            draftBody = step.body,
                        ),
                        nowEpochMs = nowEpochMs + completed,
                    )
                    if (res !is IntentDispatchResult.HandedOffToApp) {
                        return RoutineExecutionOutcome.StoppedMidRoutine(
                            completed,
                            "Intent step did not hand off: ${res::class.simpleName}",
                        )
                    }
                    completed++
                }
            }
        }

        timeline.recordEvent(
            stage = TimelineStage.VERIFICATION,
            actor = TimelineActor.LOCAL_ROUTINE,
            summary = "Human-defined routine '${routine.title}' completed ($completed step(s)).",
            outcome = TimelineOutcomeState.VERIFIED,
            targetReference = routine.id,
            nowEpochMs = nowEpochMs + completed,
        )
        return RoutineExecutionOutcome.Completed(completed)
    }
}

// --- Division 34: Conditional Personal-Lab UI Runner ---

enum class DistributionProfile(val label: String, val isPlayEligibleDesign: Boolean) {
    STANDARD_PROTOTYPE(label = "Standard Prototype (API/Intent/Manual)", isPlayEligibleDesign = true),
    PERSONAL_DEVICE_LAB(label = "Experimental Personal-Device Lab (Not Play-Compliant)", isPlayEligibleDesign = false),
}

data class PersonalLabConfiguration(
    val distributionProfile: DistributionProfile = DistributionProfile.STANDARD_PROTOTYPE,
    val ownerDisclosureAccepted: Boolean = false,
    val deviceEligibilityVerified: Boolean = false,
    val ownerFixturePackages: Set<String> = setOf(DEFAULT_FIXTURE_PACKAGE),
) {
    val isPlayStoreCompliant: Boolean
        get() = distributionProfile == DistributionProfile.STANDARD_PROTOTYPE

    companion object {
        const val DEFAULT_FIXTURE_PACKAGE = "io.github.mohamtur1.fourcbon2.fixture"
    }
}

data class FixtureUiStep(
    val targetPackage: String,
    val elementTag: String,
    val actionName: String,
    val riskContext: ActionRiskContext = ActionRiskContext.HARMLESS_LOCAL_OR_PREPARE,
)

sealed interface PersonalLabRunnerOutcome {
    data class UnavailableInStandardProfile(
        val note: String = "Experimental UI runner is disabled in Standard Prototype profile.",
    ) : PersonalLabRunnerOutcome

    data class IneligibleConfiguration(val reason: String) : PersonalLabRunnerOutcome
    data class RejectedNonFixtureOrProtectedTarget(val reason: String) : PersonalLabRunnerOutcome
    data class ExecutedAgainstFixture(
        val targetPackage: String,
        val elementTag: String,
        val playComplianceDisclaimer: String = "Personal-lab fixture test only; NOT Google Play Accessibility-policy compliance proof.",
    ) : PersonalLabRunnerOutcome
}

class ConditionalPersonalLabUiRunner(
    private val leaseController: TakeoverLeaseController,
    private val scopeBroker: OnDeviceScopeBroker,
    private val timeline: ActionTimelineRepository,
) {
    fun runFixtureStep(
        config: PersonalLabConfiguration,
        sessionId: String,
        leaseEpoch: Long,
        scopeContractId: String,
        step: FixtureUiStep,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): PersonalLabRunnerOutcome {
        if (config.distributionProfile != DistributionProfile.PERSONAL_DEVICE_LAB) {
            return PersonalLabRunnerOutcome.UnavailableInStandardProfile()
        }
        if (!config.ownerDisclosureAccepted || !config.deviceEligibilityVerified) {
            return PersonalLabRunnerOutcome.IneligibleConfiguration(
                "Personal-lab disclosure and device eligibility checks must both pass first.",
            )
        }
        if (step.riskContext != ActionRiskContext.HARMLESS_LOCAL_OR_PREPARE) {
            return PersonalLabRunnerOutcome.RejectedNonFixtureOrProtectedTarget(
                "Experimental UI runner is strictly forbidden on sensitive/protected screens (${step.riskContext.name}).",
            )
        }
        if (step.targetPackage !in config.ownerFixturePackages) {
            return PersonalLabRunnerOutcome.RejectedNonFixtureOrProtectedTarget(
                "Target '${step.targetPackage}' is not an owner-controlled test fixture app.",
            )
        }
        when (val lease = leaseController.validateCommand(sessionId, leaseEpoch)) {
            is LeaseValidationOutcome.RejectedStaleOrCancelled ->
                return PersonalLabRunnerOutcome.IneligibleConfiguration(lease.reason)
            is LeaseValidationOutcome.Valid -> Unit
        }
        when (
            val scope = scopeBroker.authorizeAndConsume(
                ProposedScopedAction(
                    contractId = scopeContractId,
                    targetPackage = step.targetPackage,
                    actionType = ScopedActionType.EXPERIMENTAL_FIXTURE_UI,
                ),
                nowEpochMs = nowEpochMs,
            )
        ) {
            is ScopeDecision.Denied ->
                return PersonalLabRunnerOutcome.RejectedNonFixtureOrProtectedTarget(scope.reason)
            is ScopeDecision.Permitted -> Unit
        }

        timeline.recordEvent(
            stage = TimelineStage.ACTION,
            actor = TimelineActor.LOCAL_ROUTINE,
            summary = "Executed personal-lab fixture step '${step.actionName}' on ${step.targetPackage}#${step.elementTag} (not Play-compliant).",
            outcome = TimelineOutcomeState.VERIFIED,
            targetReference = step.targetPackage,
            nowEpochMs = nowEpochMs,
        )
        return PersonalLabRunnerOutcome.ExecutedAgainstFixture(
            targetPackage = step.targetPackage,
            elementTag = step.elementTag,
        )
    }
}

// --- Division 36: Online/Offline Phone-Control Routing ---

enum class PhoneControlRouteKind(val label: String) {
    AUTHORIZED_SERVICE_API("Authorized Service API"),
    SUPPORTED_ANDROID_INTENT("Supported Android Intent / Custom Tab"),
    LOCAL_DETERMINISTIC_WORKFLOW("Offline Local Deterministic Workflow"),
    PERMITTED_HUMAN_ROUTINE("Permitted Human-Defined Routine"),
    PERSONAL_LAB_FIXTURE_UI("Conditional Personal-Lab Fixture UI"),
    DEFERRED_OFFLINE_OR_LOCKED("Deferred (Offline / Locked / Runtime Unavailable)"),
    EXPLICIT_MANUAL_HANDOFF("Explicit Manual Handoff"),
}

data class RoutingEnvironment(
    val isOnline: Boolean,
    val isDeviceUnlocked: Boolean,
    val configuredCredentials: Set<CredentialSlot>,
    val colabAiState: ColabBuiltInAiState,
    val requestedProviderOverride: String? = null,
    val labConfig: PersonalLabConfiguration = PersonalLabConfiguration(),
)

data class ActionRoutingRequest(
    val goalSummary: String,
    val targetPackage: String?,
    val riskContext: ActionRiskContext = ActionRiskContext.HARMLESS_LOCAL_OR_PREPARE,
    val requiresCloudColabPlanning: Boolean = false,
    val hasAuthorizedApiAdapter: Boolean = false,
    val requiredApiCredential: CredentialSlot? = null,
    val hasSupportedIntentAdapter: Boolean = false,
    val isExplicitHumanRoutineTrigger: Boolean = false,
    val canRunPurelyOfflineLocally: Boolean = false,
)

data class RouteSelectionDecision(
    val selectedRoute: PhoneControlRouteKind,
    val engineLabel: String,
    val explanation: String,
)

class PhoneControlActionRouter {
    /**
     * Selects the least-powerful adequate supported route while enforcing:
     * 1. Protected screens -> always `EXPLICIT_MANUAL_HANDOFF`.
     * 2. No silent alternate AI provider (`requestedProviderOverride != null && != "google.colab.ai"` rejected).
     * 3. No locked-device bypass (interactive phone intents/routines defer when `isDeviceUnlocked == false`).
     * 4. No fake cloud planning (when offline or Colab AI unverified, never pretends cloud planning ran).
     */
    fun selectRoute(
        request: ActionRoutingRequest,
        env: RoutingEnvironment,
    ): RouteSelectionDecision {
        // 1. Reject silent/explicit alternate cloud AI providers
        if (env.requestedProviderOverride != null &&
            env.requestedProviderOverride != ColabAiProbeReceipt.REQUIRED_PROVIDER_ID
        ) {
            return RouteSelectionDecision(
                selectedRoute = PhoneControlRouteKind.EXPLICIT_MANUAL_HANDOFF,
                engineLabel = "Blocked Alternate Provider",
                explanation = "Alternate AI provider '${env.requestedProviderOverride}' is prohibited; only google.colab.ai or local deterministic tools are allowed.",
            )
        }

        // 2. Protected contexts are always manual-only
        if (request.riskContext in setOf(
                ActionRiskContext.AUTHENTICATION_OR_OTP,
                ActionRiskContext.BANKING_OR_PAYMENT,
                ActionRiskContext.OS_PERMISSION_OR_SECURITY,
                ActionRiskContext.LOCK_SCREEN_OR_BIOMETRIC,
            )
        ) {
            return RouteSelectionDecision(
                selectedRoute = PhoneControlRouteKind.EXPLICIT_MANUAL_HANDOFF,
                engineLabel = "Manual Handoff",
                explanation = "Protected workflow (${request.riskContext.name}) cannot be automated.",
            )
        }

        // 3. Cloud Colab planning requested -> never fake it offline or when unverified
        if (request.requiresCloudColabPlanning) {
            if (!env.isOnline) {
                return RouteSelectionDecision(
                    selectedRoute = PhoneControlRouteKind.DEFERRED_OFFLINE_OR_LOCKED,
                    engineLabel = "Waiting for Internet",
                    explanation = "Offline: Colab AI planning is deferred cleanly rather than faking cloud responses.",
                )
            }
            if (env.colabAiState != ColabBuiltInAiState.VERIFIED_VIA_COLAB_RECEIPT) {
                return RouteSelectionDecision(
                    selectedRoute = PhoneControlRouteKind.EXPLICIT_MANUAL_HANDOFF,
                    engineLabel = "Manual Browser (Colab)",
                    explanation = "Colab runtime is '${env.colabAiState.label}'; export Shared Job Bundle and open 4CBOn2_Kaggle6.ipynb in Chrome Custom Tab.",
                )
            }
        }

        // 4. Least-powerful hierarchy:
        //    (a) Pure offline local deterministic routine (safe even while offline!)
        if (request.canRunPurelyOfflineLocally && !request.hasAuthorizedApiAdapter) {
            return RouteSelectionDecision(
                selectedRoute = PhoneControlRouteKind.LOCAL_DETERMINISTIC_WORKFLOW,
                engineLabel = "Local Task",
                explanation = "Using deterministic on-device library/analyzer workflow.",
            )
        }

        //    (b) Authorized Service API
        if (request.hasAuthorizedApiAdapter) {
            if (!env.isOnline) {
                return RouteSelectionDecision(
                    selectedRoute = PhoneControlRouteKind.DEFERRED_OFFLINE_OR_LOCKED,
                    engineLabel = "Waiting for Internet",
                    explanation = "API adapter available, but device is currently offline.",
                )
            }
            val slot = request.requiredApiCredential
            if (slot != null && slot !in env.configuredCredentials) {
                return RouteSelectionDecision(
                    selectedRoute = PhoneControlRouteKind.EXPLICIT_MANUAL_HANDOFF,
                    engineLabel = "Waiting for Account",
                    explanation = "Enter ${slot.displayTitle} locally in Connections before running this API route.",
                )
            }
            return RouteSelectionDecision(
                selectedRoute = PhoneControlRouteKind.AUTHORIZED_SERVICE_API,
                engineLabel = "Authorized API",
                explanation = "Routing through verified read/service API adapter.",
            )
        }

        //    (c) Interactive phone routes NEVER bypass a locked device!
        if (!env.isDeviceUnlocked && (request.hasSupportedIntentAdapter || request.isExplicitHumanRoutineTrigger)) {
            return RouteSelectionDecision(
                selectedRoute = PhoneControlRouteKind.DEFERRED_OFFLINE_OR_LOCKED,
                engineLabel = "Device Locked",
                explanation = "Phone screen is locked; interactive app intents and routines never bypass lock-screen protections.",
            )
        }

        //    (d) Supported Android Intent / Custom Tab
        if (request.hasSupportedIntentAdapter) {
            return RouteSelectionDecision(
                selectedRoute = PhoneControlRouteKind.SUPPORTED_ANDROID_INTENT,
                engineLabel = "Android Intent",
                explanation = "Routing through scoped Android Intent / Custom Tab handoff.",
            )
        }

        //    (e) Permitted Human-Defined Routine
        if (request.isExplicitHumanRoutineTrigger) {
            return RouteSelectionDecision(
                selectedRoute = PhoneControlRouteKind.PERMITTED_HUMAN_ROUTINE,
                engineLabel = "Human Routine",
                explanation = "Routing through owner-triggered static routine.",
            )
        }

        //    (f) Conditional Personal-Lab Fixture UI (only if lab profile + fixture target)
        val pkg = request.targetPackage
        if (pkg != null &&
            env.labConfig.distributionProfile == DistributionProfile.PERSONAL_DEVICE_LAB &&
            env.labConfig.ownerDisclosureAccepted &&
            env.labConfig.deviceEligibilityVerified &&
            pkg in env.labConfig.ownerFixturePackages &&
            env.isDeviceUnlocked
        ) {
            return RouteSelectionDecision(
                selectedRoute = PhoneControlRouteKind.PERSONAL_LAB_FIXTURE_UI,
                engineLabel = "Personal-Lab Fixture",
                explanation = "Routing to owner-controlled fixture in Personal-Device Lab mode.",
            )
        }

        //    (g) Default fallback: honest manual handoff (never an Accessibility workaround!)
        return RouteSelectionDecision(
            selectedRoute = PhoneControlRouteKind.EXPLICIT_MANUAL_HANDOFF,
            engineLabel = "Manual Handoff",
            explanation = "No permitted API, intent, or local routine covers this step; handing off to owner.",
        )
    }
}

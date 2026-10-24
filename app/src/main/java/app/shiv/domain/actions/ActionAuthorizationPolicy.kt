package app.shiv.domain.actions

/** Safety level required before Shiv carries out an action. */
enum class ActionTier { FREE, ACT_THEN_TELL, ASK_FIRST }

enum class ActionPermissionState { NOT_REQUIRED, GRANTED, DENIED }

enum class ActionAuthorizationOutcome { ALLOW, REQUIRE_CONFIRMATION, DENY_PERMISSION, DENY_INVALID_REQUEST }

/** A model or UI proposal, including the inputs that constrain its authority. */
data class ActionAuthorizationRequest(
    val actionType: String,
    val arguments: Map<String, String>,
    val configuredTier: String?,
    val minimumTier: ActionTier,
    val permissionState: ActionPermissionState = ActionPermissionState.NOT_REQUIRED,
    val confirmed: Boolean = false
)

data class ActionAuthorizationDecision(
    val request: ActionAuthorizationRequest,
    val effectiveTier: ActionTier,
    val outcome: ActionAuthorizationOutcome
)

/** Pure rules for resolving configured action tiers and authorization decisions. */
object ActionAuthorizationPolicy {
    private data class ActionContract(
        val acceptedArgumentKeys: Set<Set<String>>,
        val validValues: (Map<String, String>) -> Boolean
    )

    private val actionContracts = mapOf(
        "CREATE_EVENT" to contract(setOf("id", "title", "startsAt")) { args ->
            args.hasText("id") && args.hasText("title") && args.hasPositiveLong("startsAt")
        },
        "UPDATE_EVENT" to contract(setOf("eventId", "title", "startsAt")) { args ->
            args.hasText("eventId") && args.hasText("title") && args.hasPositiveLong("startsAt")
        },
        "SET_REMINDER" to contract(setOf("minutes")) { args ->
            args["minutes"]?.toIntOrNull()?.let { it in 1..1440 } == true
        },
        "SAVE_MEMORY" to contract(setOf("id", "text")) { args ->
            args.hasText("id") && args.hasText("text")
        },
        "SAVE_RULE" to contract(setOf("key", "value")) { args ->
            args.hasText("key") && args.hasText("value")
        },
        "ASK_USER" to contract(setOf("question")) { args -> args.hasText("question") },
        "SEND_NOTIFICATION" to contract(setOf("title", "body")) { args ->
            args.hasText("title") && args.hasText("body")
        },
        "STAY_QUIET" to contract(emptySet()) { true },
        "SAVE_PROFILE" to contract(setOf("name", "notes")) { true },
        "RESCHEDULE" to contract(setOf("eventId", "startsAt", "newStartsAt")) { args ->
            args.hasText("eventId") && args.hasPositiveLong("startsAt") && args.hasPositiveLong("newStartsAt")
        },
        "DRAFT_REPLY" to contract(setOf("person", "body")) { args ->
            args.hasText("person") && args.hasText("body")
        },
        "SEND_MESSAGE" to contract(setOf("person", "body")) { args ->
            args.hasText("person") && args.hasText("body")
        },
        "READ_CALENDAR" to contract(emptySet()) { true },
        "DELETE_DATA" to ActionContract(
            acceptedArgumentKeys = setOf(emptySet(), setOf("eventId"), setOf("memoryId")),
            validValues = { args -> args.values.all(String::isNotBlank) }
        )
    )

    private fun contract(keys: Set<String>, validValues: (Map<String, String>) -> Boolean) =
        ActionContract(setOf(keys), validValues)

    private fun Map<String, String>.hasText(key: String): Boolean = this[key]?.isNotBlank() == true

    private fun Map<String, String>.hasPositiveLong(key: String): Boolean =
        this[key]?.toLongOrNull()?.let { it > 0L } == true

    private fun validate(request: ActionAuthorizationRequest): Boolean {
        val contract = actionContracts[request.actionType] ?: return false
        return request.arguments.keys in contract.acceptedArgumentKeys &&
            request.arguments.keys.none(String::isBlank) &&
            contract.validValues(request.arguments)
    }

    /** A stored tier may make an action safer, but can never lower its required minimum. */
    fun effectiveTier(configuredTier: String?, minimum: ActionTier): ActionTier {
        val configured = configuredTier
            ?.let { runCatching { ActionTier.valueOf(it) }.getOrNull() }
            ?: minimum
        return if (configured.ordinal < minimum.ordinal) minimum else configured
    }

    /** Background actions that require confirmation cannot be dispatched automatically. */
    fun backgroundTier(configuredTier: String?, minimum: ActionTier): ActionTier? =
        effectiveTier(configuredTier, minimum).takeUnless { it == ActionTier.ASK_FIRST }

    fun authorize(request: ActionAuthorizationRequest): ActionAuthorizationDecision {
        val tier = effectiveTier(request.configuredTier, request.minimumTier)
        val outcome = when {
            !validate(request) -> ActionAuthorizationOutcome.DENY_INVALID_REQUEST
            request.permissionState == ActionPermissionState.DENIED ->
                ActionAuthorizationOutcome.DENY_PERMISSION
            tier == ActionTier.ASK_FIRST && !request.confirmed ->
                ActionAuthorizationOutcome.REQUIRE_CONFIRMATION
            else -> ActionAuthorizationOutcome.ALLOW
        }
        return ActionAuthorizationDecision(request, tier, outcome)
    }
}

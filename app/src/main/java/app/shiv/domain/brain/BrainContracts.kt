package app.shiv.domain.brain

import app.shiv.domain.model.EventRecord
import app.shiv.domain.model.MemoryFact
import app.shiv.domain.model.ReplyDraft
import app.shiv.domain.model.UserProfile
import kotlinx.coroutines.flow.StateFlow

data class BrainReply(
    val text: String,
    val event: EventRecord? = null,
    val memory: MemoryFact? = null,
    val reminderLeadMinutes: Int? = null,
    val reschedule: RescheduleRequest? = null,
    val draft: ReplyDraft? = null,
    val sms: SmsDraft? = null,
    val deleteEverything: Boolean = false,
    val forgetPhrase: String? = null
)

data class RescheduleRequest(val search: String, val newStart: Long)
data class SmsDraft(val person: String, val body: String)

data class AgentDecision(
    val tool: String?,
    val say: String,
    val text: String? = null,
    val key: String? = null,
    val minutes: Int? = null,
    val title: String? = null,
    val startsAt: Long? = null,
    val place: String? = null,
    val person: String? = null,
    val eventId: String? = null,
    val notificationTitle: String? = null,
    val notificationBody: String? = null
)

data class BrainRuntimeStatus(
    val queuedRequests: Int = 0,
    val inferenceRunning: Boolean = false,
    val lastRoutingMillis: Long? = null,
    val lastReplyMillis: Long? = null,
    val status: String = "No local chat model installed"
)

/** Presentation and workers depend on this capability, never on model-runtime classes. */
interface BrainPort {
    val runtimeStatus: StateFlow<BrainRuntimeStatus>
    val modelStatus: String

    suspend fun respond(
        input: String,
        profile: UserProfile?,
        memories: List<MemoryFact>,
        reminderLeadMinutes: Int,
        contextSnapshot: String
    ): BrainReply

    suspend fun decideProactive(context: String): AgentDecision?
}

fun interface ProactiveBrain {
    suspend fun decideProactive(context: String): AgentDecision?
}

package com.shiv.assistant.model

import com.shiv.assistant.domain.ComputeLease

data class LocalModelChoice(
    val modelId: String?,
    val isConversationModel: Boolean,
    val reason: String
)

/**
 * Deterministic, owner-configured model selection. Neural output never chooses
 * a model, changes its roles or bypasses the resource governor. One LiteRT
 * engine is kept at a time: swapping is safer on memory-constrained devices.
 */
object LocalModelRouting {
    fun choose(
        lease: ComputeLease,
        routerId: String?,
        conversationId: String?,
        importedIds: Set<String>
    ): LocalModelChoice {
        if (!lease.allowed)
            return LocalModelChoice(null, false, lease.reason)
        if (lease.allowLargeModel && conversationId != null &&
            conversationId in importedIds && conversationId != routerId)
            return LocalModelChoice(conversationId, true,
                "Resource lease allows on-demand conversation model")
        if (routerId != null && routerId in importedIds)
            return LocalModelChoice(routerId, false,
                "Small router selected; large model unavailable or disallowed")
        return LocalModelChoice(null, false,
            "No available small router; assign a router model in Models")
    }
}

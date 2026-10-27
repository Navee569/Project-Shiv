package com.shiv.assistant.ai

/** The classifier chooses a model only; it never grants an action permission. */
enum class RouterDecision {
    ROUTER, CONVERSATION;

    companion object {
        fun classificationPrompt(request:String):String =
            "Classify the owner request. Reply with exactly ROUTER for a short, simple answer, " +
                "or CONVERSATION for reasoning, personal context, multilingual nuance, " +
                "uncertainty, or any action. Output one word only.\nRequest: " +
                request.take(1200)

        fun parse(reply:String):RouterDecision? = when(reply.trim().uppercase()) {
            "ROUTER" -> ROUTER
            "CONVERSATION" -> CONVERSATION
            else -> null
        }
    }
}

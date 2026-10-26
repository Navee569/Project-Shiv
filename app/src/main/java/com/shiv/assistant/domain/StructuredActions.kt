package com.shiv.assistant.domain

import org.json.JSONObject

sealed class BrainTool {
    data class Proposed(val tool: String, val args: Map<String, String>) : BrainTool()
    data class Quiet(val reason: String) : BrainTool()
}

/** Parse once, never retry an unsafe output. A parsed proposal has no permission to execute. */
object StructuredActions {
    private val signatures = mapOf(
        "create_event" to setOf("title", "starts_at"),
        "update_event" to setOf("event_id", "title"),
        "set_reminder" to setOf("text", "when"),
        "save_memory" to setOf("fact"),
        "save_rule" to setOf("rule"),
        "ask_user" to setOf("question"),
        "send_notification" to setOf("text"),
        "stay_quiet" to emptySet()
    )
    fun parse(raw: String): BrainTool {
        if (raw.length !in 2..8192) return BrainTool.Quiet("invalid length")
        return try {
            val root = JSONObject(raw)
            if (root.keys().asSequence().toSet() != setOf("tool", "args"))
                return BrainTool.Quiet("unexpected root fields")
            val name = root.get("tool") as? String ?: return BrainTool.Quiet("invalid tool")
            val signature = signatures[name] ?: return BrainTool.Quiet("unknown tool")
            val args = root.get("args") as? JSONObject ?: return BrainTool.Quiet("invalid args")
            if (args.keys().asSequence().toSet() != signature)
                return BrainTool.Quiet("missing or unexpected arguments")
            val values = signature.associateWith { key ->
                val v = args.get(key)
                if (v !is String || v.isBlank() || v.length > 1000)
                    throw IllegalArgumentException("invalid argument")
                v
            }
            if (name == "stay_quiet") BrainTool.Quiet("model chose silence")
            else BrainTool.Proposed(name, values)
        } catch (_: Exception) { BrainTool.Quiet("malformed model output") }
    }
}

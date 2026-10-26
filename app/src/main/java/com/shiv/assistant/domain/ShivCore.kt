package com.shiv.assistant.domain

/** Shiv-owned, Android-independent contracts. No external app or provider is required. */
enum class IntelligenceMode { LOCAL, CLOUD, HYBRID }
enum class Risk { READ_ONLY, USER_CONFIRMATION, RESTRICTED }
enum class ActionKind {
    DIAL, SMS, EMAIL, TELEGRAM, WHATSAPP, MAPS, CALENDAR, ALARM, TIMER,
    CAMERA, VIDEO, SETTINGS, SMART_HOME, UPI, SHARE, NOTES, REMINDER,
    WEB, MUSIC, UNKNOWN
}
enum class CapabilityState { BUILT_IN, REQUIRES_CONNECTION, REQUIRES_DEVICE_PERMISSION, PLANNED }
data class Capability(
    val id: String, val name: String, val group: String, val state: CapabilityState,
    val description: String
)
data class ActionProposal(val kind: ActionKind, val argument: String, val risk: Risk, val explanation: String)
data class Plan(val objective: String, val steps: List<ActionProposal>)
data class AssistantConfiguration(
    val mode: IntelligenceMode = IntelligenceMode.LOCAL,
    val endpoint: String = "https://api.openai.com/v1/chat/completions",
    val model: String = "gpt-4.1-mini",
    val allowCloudTransmission: Boolean = false
)

/** Comprehensive capability inventory; states deliberately distinguish implementations from integrations. */
object ShivCapabilities {
    val all: List<Capability> = listOf(
        Capability("chat", "Assistant chat", "Intelligence", CapabilityState.BUILT_IN, "Local rule engine and configurable cloud inference"),
        Capability("planning", "Multi-step plans", "Intelligence", CapabilityState.BUILT_IN, "Explicit review before external actions"),
        Capability("recovery", "Plan recovery", "Intelligence", CapabilityState.BUILT_IN, "Failed steps remain pending for retry"),
        Capability("model-api", "Cloud model providers", "Intelligence", CapabilityState.BUILT_IN, "Custom HTTPS, OpenAI-compatible endpoint"),
        Capability("model-local", "LiteRT-LM local inference", "Intelligence", CapabilityState.BUILT_IN, "Real on-device inference when a compatible .litertlm model is imported and loaded"),
        Capability("model-download", "Offline model import", "Intelligence", CapabilityState.BUILT_IN, "Owner-selected file copied to app-private space with SHA-256 checksum"),
        Capability("memory", "Encrypted personal memory", "Knowledge", CapabilityState.BUILT_IN, "Android Keystore-protected notes and conversation history"),
        Capability("knowledge-graph", "Semantic knowledge graph", "Knowledge", CapabilityState.PLANNED, "Entity linking and inferred relationships are not implemented"),
        Capability("notifications", "Notification insights", "Knowledge", CapabilityState.REQUIRES_DEVICE_PERMISSION, "Opt-in Android notification listener"),
        Capability("voice", "Voice dictation", "Perception", CapabilityState.BUILT_IN, "Android speech recognition UI"),
        Capability("tts", "Text-to-speech", "Perception", CapabilityState.BUILT_IN, "Android speech synthesizer"),
        Capability("vision", "Photo understanding", "Perception", CapabilityState.REQUIRES_CONNECTION, "User-selected image via consented vision API"),
        Capability("screen", "Live screen understanding", "Perception", CapabilityState.PLANNED, "Requires scoped MediaProjection consent"),
        Capability("wakeword", "Offline wake word", "Perception", CapabilityState.PLANNED, "No always-listening service"),
        Capability("routines", "Saved routines", "Automation", CapabilityState.BUILT_IN, "Local routine queue and explicit review"),
        Capability("habits", "Opt-in hypotheses", "Automation", CapabilityState.BUILT_IN, "Deterministic charging-only repeated-observation learner; owner review required"),
        Capability("macros", "Action macros", "Automation", CapabilityState.BUILT_IN, "Plans saved locally for manual replay"),
        Capability("reminder", "Reminders", "Productivity", CapabilityState.BUILT_IN, "WorkManager local notifications"),
        Capability("calendar", "Calendar", "Productivity", CapabilityState.BUILT_IN, "User-confirmed event insertion intent"),
        Capability("alarms", "Alarms and timers", "Productivity", CapabilityState.BUILT_IN, "Android Clock intents"),
        Capability("notes", "Notes", "Productivity", CapabilityState.BUILT_IN, "Encrypted local capture and import"),
        Capability("calls", "Phone calls", "Communication", CapabilityState.BUILT_IN, "Dialer handoff; never secretly places calls"),
        Capability("sms", "SMS composer", "Communication", CapabilityState.BUILT_IN, "User sends from messaging app"),
        Capability("email", "Email drafts", "Communication", CapabilityState.BUILT_IN, "Email app composer"),
        Capability("telegram", "Telegram share", "Communication", CapabilityState.BUILT_IN, "User-confirmed share sheet"),
        Capability("whatsapp", "WhatsApp share", "Communication", CapabilityState.BUILT_IN, "User-confirmed share sheet"),
        Capability("contacts", "Contact name preview", "Communication", CapabilityState.REQUIRES_DEVICE_PERMISSION, "Owner-requested, temporary name search after Android Contacts permission; no numbers or addresses saved"),
        Capability("system", "System controls", "Device", CapabilityState.BUILT_IN, "Safe Wi-Fi, Bluetooth, display settings handoffs"),
        Capability("accessibility", "On-demand screen fact preview", "Device", CapabilityState.REQUIRES_DEVICE_PERMISSION, "Owner-enabled, 15-second, single-app non-sensitive screen inspection; no general UI automation"),
        Capability("music", "Music and video", "Media", CapabilityState.BUILT_IN, "Search and player handoffs"),
        Capability("camera", "Camera", "Media", CapabilityState.BUILT_IN, "User launches camera"),
        Capability("maps", "Navigation", "Travel", CapabilityState.BUILT_IN, "Maps handoff"),
        Capability("rides", "Ride-hailing", "Travel", CapabilityState.REQUIRES_CONNECTION, "External provider booking must be user-confirmed"),
        Capability("shopping", "Shopping and food", "Commerce", CapabilityState.REQUIRES_CONNECTION, "External marketplace handoffs; no checkout API"),
        Capability("upi", "UPI payment", "Commerce", CapabilityState.BUILT_IN, "Payment application opened without autonomous transfer"),
        Capability("home", "Smart home", "Home", CapabilityState.REQUIRES_CONNECTION, "External home application handoff"),
        Capability("social-draft", "Social content drafts", "Social", CapabilityState.BUILT_IN, "Editable draft and explicit share"),
        Capability("social-publish", "Social publishing", "Social", CapabilityState.REQUIRES_CONNECTION, "Requires platform API/OAuth authorization"),
        Capability("social-analytics", "Social analytics", "Social", CapabilityState.REQUIRES_CONNECTION, "Requires connected platform analytics API"),
        Capability("social-inbox", "Social inbox and comments", "Social", CapabilityState.REQUIRES_CONNECTION, "Requires platform inbox API"),
        Capability("social-campaigns", "Campaign planning", "Social", CapabilityState.BUILT_IN, "Encrypted local planning notes"),
        Capability("mcp", "MCP tools and external services", "Integrations", CapabilityState.PLANNED, "No unauthenticated local command server"),
        Capability("terminal", "Terminal and privileged commands", "Integrations", CapabilityState.PLANNED, "Deliberately disabled until sandboxing"),
        Capability("workspace", "Document import", "Integrations", CapabilityState.BUILT_IN, "User-selected text files via document picker"),
        Capability("privacy", "Encrypted credentials and data", "Security", CapabilityState.BUILT_IN, "Non-exportable Android Keystore AES-GCM")
    )
    init { require(all.map { it.id }.distinct().size == all.size) }
}

object ShivPlanner {
    fun parse(text: String): ActionProposal {
        val input = text.trim()
        val lower = input.lowercase()
        fun proposal(kind: ActionKind, argument: String, explanation: String) =
            ActionProposal(kind, argument.trim(), Risk.USER_CONFIRMATION, explanation)
        return when {
            lower.startsWith("call ") -> proposal(ActionKind.DIAL, input.drop(5), "Review number in dialer")
            lower.startsWith("sms ") || lower.startsWith("text ") -> proposal(ActionKind.SMS, input.substringAfter(' '), "Review SMS before sending")
            lower.startsWith("email ") -> proposal(ActionKind.EMAIL, input.drop(6), "Review draft in email app")
            lower.startsWith("telegram ") -> proposal(ActionKind.TELEGRAM, input.drop(9), "Share via Telegram")
            lower.startsWith("whatsapp ") -> proposal(ActionKind.WHATSAPP, input.drop(9), "Share via WhatsApp")
            lower.startsWith("navigate ") || lower.startsWith("directions ") -> proposal(ActionKind.MAPS, input.substringAfter(' '), "Open Maps")
            lower.startsWith("calendar ") || lower.startsWith("event ") -> proposal(ActionKind.CALENDAR, input.substringAfter(' '), "Review event")
            lower.startsWith("alarm ") -> proposal(ActionKind.ALARM, input.drop(6), "Review alarm")
            lower.startsWith("timer ") -> proposal(ActionKind.TIMER, input.drop(6), "Review timer")
            lower == "camera" || lower.startsWith("camera ") -> proposal(ActionKind.CAMERA, input, "Open camera")
            lower.startsWith("youtube ") || lower.startsWith("video ") -> proposal(ActionKind.VIDEO, input.substringAfter(' '), "Search video")
            lower.startsWith("music ") -> proposal(ActionKind.MUSIC, input.drop(6), "Search music")
            lower.startsWith("settings ") || lower.startsWith("wifi") || lower.startsWith("bluetooth") -> proposal(ActionKind.SETTINGS, input, "Open Android settings")
            lower.startsWith("home ") || lower.startsWith("smart home") -> proposal(ActionKind.SMART_HOME, input, "Open home controls")
            lower.startsWith("pay ") || lower.startsWith("upi ") -> proposal(ActionKind.UPI, input.substringAfter(' '), "Open payment application; no automatic payment")
            lower.startsWith("share ") || lower.startsWith("post ") -> proposal(ActionKind.SHARE, input.substringAfter(' '), "Review external share")
            lower.startsWith("remember ") || lower.startsWith("note ") -> ActionProposal(ActionKind.NOTES, input.substringAfter(' '), Risk.READ_ONLY, "Store encrypted local note")
            lower.startsWith("remind ") -> proposal(ActionKind.REMINDER, input.drop(7), "Schedule notification after review")
            lower.startsWith("open website ") -> proposal(ActionKind.WEB, input.drop(13), "Open browser")
            else -> ActionProposal(ActionKind.UNKNOWN, input, Risk.READ_ONLY, "No external action inferred")
        }
    }
    fun plan(goal: String): Plan {
        val parts = goal.trim().split(Regex("(?i)\\s+(?:and\\s+then|then)\\s+"))
            .map { it.trim() }.filter { it.isNotBlank() }.take(12)
        return Plan(goal, parts.map(::parse))
    }
}
object CloudSafety {
    fun endpointAllowed(endpoint: String): Boolean {
        val uri = try { java.net.URI(endpoint) } catch (_: Exception) { return false }
        val host = uri.host?.lowercase() ?: return false
        if (uri.userInfo != null || uri.fragment != null || uri.port == 0) return false
        return uri.scheme == "https" && host.isNotBlank() ||
            uri.scheme == "http" && (host == "127.0.0.1" || host == "localhost" || host == "::1")
    }
    fun maySend(cfg: AssistantConfiguration) =
        cfg.mode != IntelligenceMode.LOCAL && cfg.allowCloudTransmission && endpointAllowed(cfg.endpoint)
}

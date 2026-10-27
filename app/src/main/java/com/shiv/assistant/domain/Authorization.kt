package com.shiv.assistant.domain

import com.shiv.assistant.data.ActionLogRecord
import java.util.UUID

enum class AuthorityTier { FREE, ACT_THEN_TELL, ASK_FIRST }
data class ActionDecision(val permitted: Boolean, val requiresConfirmation: Boolean,
    val tier: AuthorityTier, val reason: String)
object ActionAuthorization {
    private val irreversible = setOf(ActionKind.UPI,ActionKind.SMS,ActionKind.EMAIL,
        ActionKind.TELEGRAM,ActionKind.WHATSAPP,ActionKind.SHARE)
    private val freeAllowed=setOf(ActionKind.NOTES,ActionKind.MAPS,ActionKind.WEB,
        ActionKind.VIDEO,ActionKind.MUSIC)
    fun editableTiers(kind:ActionKind):List<AuthorityTier> = when {
        kind==ActionKind.UNKNOWN || kind in irreversible -> listOf(AuthorityTier.ASK_FIRST)
        kind in freeAllowed -> AuthorityTier.entries
        else -> listOf(AuthorityTier.ACT_THEN_TELL,AuthorityTier.ASK_FIRST)
    }
    /**
     * No model tool can set authority; only independently persisted owner preference
     * is accepted. Financial actions have a hard ASK_FIRST floor.
     */
    fun decide(action: ActionProposal, configured: AuthorityTier?, ownerConfirmed: Boolean): ActionDecision {
        if (action.kind == ActionKind.UNKNOWN || action.risk==Risk.RESTRICTED)
            return ActionDecision(false, false, AuthorityTier.ASK_FIRST, "Unsupported or restricted action")
        val tier = when {
            action.kind in irreversible -> AuthorityTier.ASK_FIRST
            configured != null && configured in editableTiers(action.kind) -> configured
            action.kind==ActionKind.NOTES -> AuthorityTier.FREE
            else -> AuthorityTier.ASK_FIRST
        }
        val needsConfirmation = tier == AuthorityTier.ASK_FIRST
        return ActionDecision(!needsConfirmation || ownerConfirmed, needsConfirmation, tier,
            if (needsConfirmation && !ownerConfirmed) "Owner approval required" else "Permitted by deterministic owner policy")
    }
    fun log(action: ActionProposal, decision: ActionDecision, permission: String, result: String, undo: String = "NOT_AVAILABLE") =
        ActionLogRecord(UUID.randomUUID().toString(), action.kind.name,
            // Arguments may contain names/numbers, never log them in a troubleshooting trace.
            "[REDACTED]", decision.tier.name, permission,
            decision.permitted, result, undo)
}

/** Hard-coded denylist acts before event storage or inference. The user can add exclusions. */
object SensitiveAppDenylist {
    private val blocked = setOf(
        "com.google.android.apps.authenticator2", "com.microsoft.authenticator",
        "com.azure.authenticator", "com.authy.authy", "com.lastpass.lpandroid",
        "com.x8bit.bitwarden", "com.onepassword.android",
        "com.google.android.apps.nbu.paisa.user", "com.phonepe.app",
        "net.one97.paytm", "in.org.npci.upiapp", "com.sbi.lotusintouch",
        "com.sbi.SBIFreedomPlus", "com.csam.icici.bank.imobile",
        "com.axis.mobile", "com.hdfcbank.payzapp",
        "com.kotak.bank.mobile", "com.idfcfirstbank.optimus"
    )
    private val criticalTokens = listOf(
        "bank", "wallet", "finance", "payment", "paytm", "phonepe",
        ".upi", "authenticator", "password", "bitwarden", "1password",
        "lastpass", "otp", "secureid", "creditcard"
    )
    fun excluded(packageName: String, extraOwnerDenylist: Set<String> = emptySet()): Boolean {
        val lower = packageName.trim().lowercase(java.util.Locale.ROOT)
        return lower.isBlank() ||
            blocked.any { known ->
                val denied=known.lowercase(java.util.Locale.ROOT)
                lower==denied || lower.startsWith(denied+".")
            } ||
            extraOwnerDenylist.any { lower==it.trim().lowercase(java.util.Locale.ROOT) } ||
            criticalTokens.any { it in lower }
    }
}

enum class ToolAction { CREATE_EVENT, UPDATE_EVENT, SET_REMINDER, SAVE_MEMORY,
    SAVE_RULE, ASK_USER, SEND_NOTIFICATION, STAY_QUIET }
data class ParsedToolAction(val action: ToolAction, val arguments: Map<String,String>, val needsReview: Boolean)
object StructuredToolParser {
    private val allowedArguments = mapOf(
        ToolAction.CREATE_EVENT to setOf("title","datetime","end"),
        ToolAction.UPDATE_EVENT to setOf("id","title","datetime","end"),
        ToolAction.SET_REMINDER to setOf("title","datetime"),
        ToolAction.SAVE_MEMORY to setOf("text","category"),
        ToolAction.SAVE_RULE to setOf("rule","value"),
        ToolAction.ASK_USER to setOf("question"),
        ToolAction.SEND_NOTIFICATION to setOf("text"),
        ToolAction.STAY_QUIET to emptySet()
    )
    /**
     * Action JSON is a suggestion; validation gives it no permission to execute.
     * Unknown actions, parameters and invalid date strings fail closed.
     */
    fun parse(json: String): ParsedToolAction = parseOrNull(json) ?:
        ParsedToolAction(ToolAction.STAY_QUIET,emptyMap(),false)

    fun parseOrNull(json: String): ParsedToolAction? {
        try {
            require(json.length in 2..4096)
            val objectValue = org.json.JSONObject(json)
            require(objectValue.length() == 2)
            require(objectValue.has("action") && objectValue.has("arguments"))
            val type = ToolAction.valueOf(objectValue.getString("action"))
            val argsJson = objectValue.getJSONObject("arguments")
            val keys = argsJson.keys().asSequence().toSet()
            require(keys.all { it in allowedArguments.getValue(type) })
            val params = keys.associateWith {
                require(argsJson.get(it) is String)
                val value=argsJson.getString(it)
                require(value.length in 1..500 && value==value.trim() && value.isNotBlank() &&
                    !value.any { char -> char.code==0 })
                value
            }
            // Validate each operation's full contract. Returning STAY_QUIET on
            // any malformed proposal is safer than approving incomplete tools.
            fun requireArgs(vararg required:String) {
                require(required.all { params.containsKey(it) })
            }
            fun parseTime(field:String)=java.time.OffsetDateTime.parse(params.getValue(field))
            when(type) {
                ToolAction.CREATE_EVENT -> {
                    requireArgs("title","datetime")
                    val start=parseTime("datetime")
                    if("end" in params) require(parseTime("end").isAfter(start))
                }
                ToolAction.UPDATE_EVENT -> {
                    requireArgs("id")
                    require(params.getValue("id").length<=128)
                    require("title" in params || "datetime" in params || "end" in params)
                    if("datetime" in params) parseTime("datetime")
                    if("end" in params) {
                        val end=parseTime("end")
                        if("datetime" in params) require(end.isAfter(parseTime("datetime")))
                    }
                }
                ToolAction.SET_REMINDER -> {
                    requireArgs("title","datetime")
                    parseTime("datetime")
                }
                ToolAction.SAVE_MEMORY -> {
                    requireArgs("text","category")
                    require(params.getValue("category").matches(Regex("[A-Z_]{2,32}")))
                }
                ToolAction.SAVE_RULE -> {
                    requireArgs("rule","value")
                    require(params.getValue("rule")=="reminders_minute_of_day")
                    require(params.getValue("value").toIntOrNull()?.let { it in 0..1439 } == true)
                }
                ToolAction.ASK_USER -> requireArgs("question")
                ToolAction.SEND_NOTIFICATION -> requireArgs("text")
                ToolAction.STAY_QUIET -> require(params.isEmpty())
            }
            return ParsedToolAction(type,params,type != ToolAction.STAY_QUIET)
        } catch (_: Exception) {
            return null
        }
    }
}

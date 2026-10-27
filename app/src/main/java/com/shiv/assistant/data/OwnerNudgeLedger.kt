package com.shiv.assistant.data

import com.shiv.assistant.domain.AgendaNudgePolicy
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

enum class OwnerNudgeReaction { ACTED, SNOOZED, DISMISSED, CORRECTED }

/**
 * Encrypted owner-only notification preferences and feedback.
 * Does not infer that a notification was acted upon without a user reaction.
 */
class OwnerNudgeLedger(private val vault: ShivVault) {
    private val deliveryKey="proactive_delivered_v1"
    private val feedbackKey="proactive_feedback_v1"

    /** One optional repeat after a snooze; acted, dismissed and corrected stay suppressed. */
    fun deliveryKey(base:String,now:Long=System.currentTimeMillis()):String? {
        val raw=JSONArray(vault.get(feedbackKey) ?: "[]")
        val row=(0 until raw.length()).mapNotNull { raw.optJSONObject(it) }
            .lastOrNull { it.optString("key")==base }
        return AgendaNudgePolicy.deliveryKey(base,row?.optString("reaction"),
            row?.optLong("at"),now)
    }

    @Synchronized
    fun deliveredToday(now:Long=System.currentTimeMillis()): Pair<Set<String>,Int> {
        val day=java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault())
            .toLocalDate().toString()
        val stored=JSONObject(vault.get(deliveryKey) ?: "{}")
        if(stored.optString("day")!=day) return emptySet<String>() to 0
        val ids=stored.optJSONArray("ids") ?: JSONArray()
        val keys=(0 until ids.length()).map { ids.optString(it) }
            .filter { it.isNotBlank() }.toSet()
        return keys to keys.size
    }
    @Synchronized
    fun reserveNotification(key:String, now:Long=System.currentTimeMillis()):Boolean {
        require(key.matches(Regex("[a-z0-9:_-]{1,100}")))
        val previous=deliveredToday(now).first
        if(key in previous || previous.size>=8)return false
        val day=java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault())
            .toLocalDate().toString()
        vault.put(deliveryKey,JSONObject().put("day",day)
            .put("ids",JSONArray((previous+key).sorted())).toString())
        vault.put("proactive_last_key",key)
        return true
    }
    @Synchronized
    fun react(reaction: OwnerNudgeReaction) {
        val key=vault.get("proactive_last_key")?.removeSuffix(":snooze") ?: return
        require(key.matches(Regex("[a-z0-9:_-]{1,100}")))
        val arr=JSONArray(vault.get(feedbackKey) ?: "[]")
        val kept=JSONArray()
        val minTime=System.currentTimeMillis()-90L*86_400_000L
        for(i in 0 until arr.length()) {
            val row=arr.optJSONObject(i) ?: continue
            if(row.optLong("at")>=minTime && row.optString("key")!=key)
                kept.put(row)
        }
        kept.put(JSONObject().put("key",key).put("reaction",reaction.name)
            .put("at",System.currentTimeMillis()))
        vault.put(feedbackKey,kept.toString())
    }
    fun reactions(days:Int=7):List<String> {
        require(days in 1..90)
        val cutoff=System.currentTimeMillis()-days*86_400_000L
        val json=JSONArray(vault.get(feedbackKey) ?: "[]")
        return (0 until json.length()).mapNotNull {
            val row=json.optJSONObject(it) ?: return@mapNotNull null
            if(row.optLong("at")<cutoff) null else
                row.optString("reaction").takeIf {
                    it in OwnerNudgeReaction.entries.map { value -> value.name }
                }
        }
    }
    fun suggestedDailyLimit():Int=AgendaNudgePolicy.limitFromFeedback(reactions())
    fun lastKey():String=vault.get("proactive_last_key").orEmpty()
    fun report():String {
        val records=reactions()
        if(records.isEmpty())return "No owner feedback entered during the last seven days."
        return "Owner feedback, last seven days: "+records.size+
            " • acted "+records.count { it=="ACTED" }+
            " • snoozed "+records.count { it=="SNOOZED" }+
            " • dismissed "+records.count { it=="DISMISSED" }+
            " • corrected "+records.count { it=="CORRECTED" }+
            ". Optional daily limit: "+suggestedDailyLimit()+"."
    }
}

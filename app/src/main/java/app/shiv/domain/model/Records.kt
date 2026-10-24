package app.shiv.domain.model

import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val fromUser: Boolean,
    val createdAt: Long = System.currentTimeMillis(),
    val tier: String? = null
)

data class UserProfile(
    val id: Int = 1,
    val name: String = "",
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

data class MemoryFact(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class EventRecord(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val startsAt: Long,
    val place: String? = null,
    val person: String? = null,
    val source: String = "CHAT",
    val createdAt: Long = System.currentTimeMillis()
)

data class ActionLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val action: String,
    val tier: String,
    val outcome: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class ReminderRule(val ruleKey: String, val value: String, val updatedAt: Long = System.currentTimeMillis())

data class ReplyDraft(
    val id: String = UUID.randomUUID().toString(),
    val person: String,
    val body: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class ActionTierSetting(val actionKey: String, val tier: String)

data class ShivSettings(
    val id: Int = 1,
    val proactiveEnabled: Boolean = true,
    val quietStartMinute: Int = 22 * 60,
    val quietEndMinute: Int = 8 * 60,
    val maxDailyInterruptions: Int = 3
)

data class PendingEventRecord(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val startsAt: Long,
    val place: String? = null,
    val person: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

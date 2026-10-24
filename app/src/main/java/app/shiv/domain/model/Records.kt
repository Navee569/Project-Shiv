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
    val createdAt: Long = System.currentTimeMillis(),
    val source: String = "OWNER",
    val confidence: Double? = null
)

data class EventRecord(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val startsAt: Long,
    val place: String? = null,
    val person: String? = null,
    val source: String = "CHAT",
    val createdAt: Long = System.currentTimeMillis(),
    val confidence: Double? = null
)

data class ActionLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val action: String,
    val tier: String,
    val outcome: String,
    val createdAt: Long = System.currentTimeMillis(),
    val actionType: String? = null,
    val arguments: Map<String, String> = emptyMap(),
    val permissionState: String? = null,
    val confirmationState: String? = null,
    val result: String? = null,
    val undoInfo: String? = null
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

data class TaskRecord(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val notes: String = "",
    val dueAt: Long? = null,
    val status: String = "OPEN",
    val source: String = "OWNER",
    val confidence: Double? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class ReminderRecord(
    val id: String = UUID.randomUUID().toString(),
    val eventId: String,
    val triggerAt: Long,
    val deliveryState: String = "PENDING",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class ModelMetadata(
    val role: String,
    val format: String,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
    val importedAt: Long = System.currentTimeMillis()
)

data class RecordSourceMetadata(
    val id: String = UUID.randomUUID().toString(),
    val recordType: String,
    val recordId: String,
    val source: String,
    val confidence: Double? = null,
    val capturedAt: Long = System.currentTimeMillis()
)

enum class CaptureStatus { QUEUED, PROCESSING, RETRY, COMPLETED, FAILED }
enum class CapturePriority(val rank: Int) { BACKGROUND(0), NORMAL(1), HIGH(2), URGENT(3) }

data class CaptureSignal(
    val id: String = UUID.randomUUID().toString(),
    val payload: String,
    val source: String,
    val capturedAt: Long = System.currentTimeMillis(),
    val priority: CapturePriority = CapturePriority.NORMAL,
    val status: CaptureStatus = CaptureStatus.QUEUED,
    val attempts: Int = 0,
    val nextAttemptAt: Long = capturedAt,
    val leaseToken: String? = null,
    val leaseUntil: Long? = null,
    val processedAt: Long? = null,
    val failureCode: String? = null
)

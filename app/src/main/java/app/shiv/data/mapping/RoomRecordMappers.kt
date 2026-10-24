package app.shiv.data.mapping

import app.shiv.data.local.*

import app.shiv.ActionLogEntry
import app.shiv.ActionTierSetting
import app.shiv.ChatMessage
import app.shiv.EventRecord
import app.shiv.MemoryFact
import app.shiv.PendingEventRecord
import app.shiv.ReminderRule
import app.shiv.ReplyDraft
import app.shiv.ShivSettings
import app.shiv.UserProfile
import app.shiv.domain.model.CapturePriority
import app.shiv.domain.model.CaptureSignal
import app.shiv.domain.model.CaptureStatus
import app.shiv.domain.model.ModelMetadata
import app.shiv.domain.model.RecordSourceMetadata
import app.shiv.domain.model.ReminderRecord
import app.shiv.domain.model.TaskRecord
import org.json.JSONObject

/** Explicit mapping keeps domain records independent from Room annotations. */
object RoomRecordMappers {
    fun toDomain(v: DbChatMessage) = ChatMessage(v.id, v.text, v.fromUser, v.createdAt, v.tier)
    fun toRoom(v: ChatMessage) = DbChatMessage(v.id, v.text, v.fromUser, v.createdAt, v.tier)
    fun toDomain(v: DbUserProfile) = UserProfile(v.id, v.name, v.notes, v.updatedAt)
    fun toRoom(v: UserProfile) = DbUserProfile(v.id, v.name, v.notes, v.updatedAt)
    fun toDomain(v: DbMemoryFact) = MemoryFact(v.id, v.text, v.createdAt, v.source, v.confidence)
    fun toRoom(v: MemoryFact) = DbMemoryFact(v.id, v.text, v.createdAt, v.source, v.confidence)
    fun toDomain(v: DbEventRecord) = EventRecord(v.id, v.title, v.startsAt, v.place, v.person, v.source, v.createdAt, v.confidence)
    fun toRoom(v: EventRecord) = DbEventRecord(v.id, v.title, v.startsAt, v.place, v.person, v.source, v.createdAt, v.confidence)
    fun toDomain(v: DbActionLogEntry) = ActionLogEntry(
        id = v.id,
        action = v.action,
        tier = v.tier,
        outcome = v.outcome,
        createdAt = v.createdAt,
        actionType = v.actionType,
        arguments = decodeArguments(v.argumentsJson),
        permissionState = v.permissionState,
        confirmationState = v.confirmationState,
        result = v.result,
        undoInfo = v.undoInfo
    )
    fun toRoom(v: ActionLogEntry) = DbActionLogEntry(
        id = v.id,
        action = v.action,
        tier = v.tier,
        outcome = v.outcome,
        createdAt = v.createdAt,
        actionType = v.actionType,
        argumentsJson = JSONObject(v.arguments).toString(),
        permissionState = v.permissionState,
        confirmationState = v.confirmationState,
        result = v.result,
        undoInfo = v.undoInfo
    )
    fun toDomain(v: DbReminderRule) = ReminderRule(v.ruleKey, v.value, v.updatedAt)
    fun toRoom(v: ReminderRule) = DbReminderRule(v.ruleKey, v.value, v.updatedAt)
    fun toDomain(v: DbReplyDraft) = ReplyDraft(v.id, v.person, v.body, v.createdAt)
    fun toRoom(v: ReplyDraft) = DbReplyDraft(v.id, v.person, v.body, v.createdAt)
    fun toDomain(v: DbActionTierSetting) = ActionTierSetting(v.actionKey, v.tier)
    fun toRoom(v: ActionTierSetting) = DbActionTierSetting(v.actionKey, v.tier)
    fun toDomain(v: DbShivSettings) = ShivSettings(v.id, v.proactiveEnabled, v.quietStartMinute, v.quietEndMinute, v.maxDailyInterruptions)
    fun toRoom(v: ShivSettings) = DbShivSettings(v.id, v.proactiveEnabled, v.quietStartMinute, v.quietEndMinute, v.maxDailyInterruptions)
    fun toDomain(v: DbPendingEventRecord) = PendingEventRecord(v.id, v.title, v.startsAt, v.place, v.person, v.createdAt)
    fun toRoom(v: PendingEventRecord) = DbPendingEventRecord(v.id, v.title, v.startsAt, v.place, v.person, v.createdAt)
    fun toDomain(v: DbTaskRecord) = TaskRecord(v.id, v.title, v.notes, v.dueAt, v.status, v.source, v.confidence, v.createdAt, v.updatedAt)
    fun toRoom(v: TaskRecord) = DbTaskRecord(v.id, v.title, v.notes, v.dueAt, v.status, v.source, v.confidence, v.createdAt, v.updatedAt)
    fun toDomain(v: DbReminderRecord) = ReminderRecord(v.id, v.eventId, v.triggerAt, v.deliveryState, v.createdAt, v.updatedAt)
    fun toRoom(v: ReminderRecord) = DbReminderRecord(v.id, v.eventId, v.triggerAt, v.deliveryState, v.createdAt, v.updatedAt)
    fun toDomain(v: DbModelMetadata) = ModelMetadata(v.role, v.format, v.fileName, v.sizeBytes, v.sha256, v.importedAt)
    fun toRoom(v: ModelMetadata) = DbModelMetadata(v.role, v.format, v.fileName, v.sizeBytes, v.sha256, v.importedAt)
    fun toDomain(v: DbRecordSourceMetadata) = RecordSourceMetadata(v.id, v.recordType, v.recordId, v.source, v.confidence, v.capturedAt)
    fun toRoom(v: RecordSourceMetadata) = DbRecordSourceMetadata(v.id, v.recordType, v.recordId, v.source, v.confidence, v.capturedAt)
    fun toDomain(v: DbCaptureSignal) = CaptureSignal(
        v.id, v.payload, v.source, v.capturedAt,
        CapturePriority.entries.firstOrNull { it.rank == v.priority } ?: CapturePriority.NORMAL,
        runCatching { CaptureStatus.valueOf(v.status) }.getOrDefault(CaptureStatus.FAILED),
        v.attempts, v.nextAttemptAt, v.leaseToken, v.leaseUntil, v.processedAt, v.failureCode
    )
    fun toRoom(v: CaptureSignal) = DbCaptureSignal(
        v.id, v.payload, v.source, v.capturedAt, v.priority.rank, v.status.name, v.attempts,
        v.nextAttemptAt, v.leaseToken, v.leaseUntil, v.processedAt, v.failureCode
    )

    private fun decodeArguments(json: String): Map<String, String> = runCatching {
        val value = JSONObject(json)
        value.keys().asSequence().associateWith(value::getString)
    }.getOrDefault(emptyMap())
}

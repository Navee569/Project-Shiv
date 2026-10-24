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
import org.json.JSONObject

/** Explicit mapping keeps domain records independent from Room annotations. */
object RoomRecordMappers {
    fun toDomain(v: DbChatMessage) = ChatMessage(v.id, v.text, v.fromUser, v.createdAt, v.tier)
    fun toRoom(v: ChatMessage) = DbChatMessage(v.id, v.text, v.fromUser, v.createdAt, v.tier)
    fun toDomain(v: DbUserProfile) = UserProfile(v.id, v.name, v.notes, v.updatedAt)
    fun toRoom(v: UserProfile) = DbUserProfile(v.id, v.name, v.notes, v.updatedAt)
    fun toDomain(v: DbMemoryFact) = MemoryFact(v.id, v.text, v.createdAt)
    fun toRoom(v: MemoryFact) = DbMemoryFact(v.id, v.text, v.createdAt)
    fun toDomain(v: DbEventRecord) = EventRecord(v.id, v.title, v.startsAt, v.place, v.person, v.source, v.createdAt)
    fun toRoom(v: EventRecord) = DbEventRecord(v.id, v.title, v.startsAt, v.place, v.person, v.source, v.createdAt)
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

    private fun decodeArguments(json: String): Map<String, String> = runCatching {
        val value = JSONObject(json)
        value.keys().asSequence().associateWith(value::getString)
    }.getOrDefault(emptyMap())
}

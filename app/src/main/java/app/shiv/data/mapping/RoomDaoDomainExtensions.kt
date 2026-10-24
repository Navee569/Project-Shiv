package app.shiv.data.mapping

import app.shiv.*

import app.shiv.data.mapping.RoomRecordMappers

// Domain-facing write overloads keep existing Android adapters from constructing Room rows.
suspend fun ShivDao.addMessage(value: ChatMessage) = addMessage(RoomRecordMappers.toRoom(value))
suspend fun ShivDao.saveProfile(value: UserProfile) = saveProfile(RoomRecordMappers.toRoom(value))
suspend fun ShivDao.saveMemory(value: MemoryFact) = saveMemory(RoomRecordMappers.toRoom(value))
suspend fun ShivDao.addPendingEvent(value: PendingEventRecord) = addPendingEvent(RoomRecordMappers.toRoom(value))
suspend fun ShivDao.saveEvent(value: EventRecord) = saveEvent(RoomRecordMappers.toRoom(value))
suspend fun ShivDao.logAction(value: ActionLogEntry) = logAction(RoomRecordMappers.toRoom(value))
suspend fun ShivDao.saveRule(value: ReminderRule) = saveRule(RoomRecordMappers.toRoom(value))
suspend fun ShivDao.saveDraft(value: ReplyDraft) = saveDraft(RoomRecordMappers.toRoom(value))
suspend fun ShivDao.saveActionTier(value: ActionTierSetting) = saveActionTier(RoomRecordMappers.toRoom(value))
suspend fun ShivDao.saveSettings(value: ShivSettings) = saveSettings(RoomRecordMappers.toRoom(value))
suspend fun ShivDao.saveMessages(values: List<ChatMessage>) = saveMessages(values.map(RoomRecordMappers::toRoom))
suspend fun ShivDao.saveMemories(values: List<MemoryFact>) = saveMemories(values.map(RoomRecordMappers::toRoom))
suspend fun ShivDao.saveEvents(values: List<EventRecord>) = saveEvents(values.map(RoomRecordMappers::toRoom))
suspend fun ShivDao.savePendingEvents(values: List<PendingEventRecord>) = savePendingEvents(values.map(RoomRecordMappers::toRoom))
suspend fun ShivDao.saveActions(values: List<ActionLogEntry>) = saveActions(values.map(RoomRecordMappers::toRoom))
suspend fun ShivDao.saveRules(values: List<ReminderRule>) = saveRules(values.map(RoomRecordMappers::toRoom))
suspend fun ShivDao.saveDrafts(values: List<ReplyDraft>) = saveDrafts(values.map(RoomRecordMappers::toRoom))
suspend fun ShivDao.saveActionTiers(values: List<ActionTierSetting>) = saveActionTiers(values.map(RoomRecordMappers::toRoom))

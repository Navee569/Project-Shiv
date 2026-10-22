package app.shiv

internal enum class NotificationEventOutcome {
    DUPLICATE, PENDING_CONFIRMATION, SAVED, SAVED_AND_NOTIFIED
}

/** Narrow side-effect boundary so notification tier behavior can be tested without Android services. */
internal interface NotificationEventSink {
    suspend fun isDuplicate(event: EventRecord): Boolean
    suspend fun createPendingConfirmation(event: EventRecord)
    suspend fun saveEvent(event: EventRecord)
    suspend fun scheduleReminder(event: EventRecord)
    suspend fun notifySavedEvent(event: EventRecord)
}

internal suspend fun orchestrateNotificationEvent(
    event: EventRecord,
    tier: ActionTier,
    sink: NotificationEventSink
): NotificationEventOutcome {
    if (sink.isDuplicate(event)) return NotificationEventOutcome.DUPLICATE
    return when (tier) {
        ActionTier.ASK_FIRST -> {
            sink.createPendingConfirmation(event)
            NotificationEventOutcome.PENDING_CONFIRMATION
        }
        ActionTier.FREE -> {
            sink.saveEvent(event)
            sink.scheduleReminder(event)
            NotificationEventOutcome.SAVED
        }
        ActionTier.ACT_THEN_TELL -> {
            sink.saveEvent(event)
            sink.scheduleReminder(event)
            sink.notifySavedEvent(event)
            NotificationEventOutcome.SAVED_AND_NOTIFIED
        }
    }
}

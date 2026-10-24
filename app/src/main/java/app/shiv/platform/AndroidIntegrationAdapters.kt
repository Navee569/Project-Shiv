package app.shiv.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import app.shiv.domain.actions.ActionPermissionState
import app.shiv.domain.integration.ActionPermissionPort
import app.shiv.domain.integration.CalendarSyncPort
import app.shiv.domain.integration.CalendarSyncResult
import app.shiv.domain.integration.DiagnosticArea
import app.shiv.domain.integration.DiagnosticCode
import app.shiv.domain.integration.DiagnosticEvent
import app.shiv.domain.integration.DiagnosticsPort
import app.shiv.domain.integration.ReminderSchedulingPort
import app.shiv.domain.model.EventRecord
import app.shiv.domain.repository.ShivViewRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidActionPermissionPort(context: Context) : ActionPermissionPort {
    private val appContext = context.applicationContext

    override fun stateFor(actionType: String): ActionPermissionState = when (actionType) {
        "READ_CALENDAR" -> permissionState(Manifest.permission.READ_CALENDAR)
        "SEND_NOTIFICATION" -> if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            ActionPermissionState.NOT_REQUIRED
        } else permissionState(Manifest.permission.POST_NOTIFICATIONS)
        else -> ActionPermissionState.NOT_REQUIRED
    }

    private fun permissionState(permission: String) = if (
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED
    ) ActionPermissionState.GRANTED else ActionPermissionState.DENIED
}

class AndroidReminderSchedulingPort(context: Context) : ReminderSchedulingPort {
    private val appContext = context.applicationContext

    override fun schedule(event: EventRecord, leadMinutes: Int) =
        AlarmScheduler.schedule(appContext, event, leadMinutes)

    override fun cancel(event: EventRecord) = AlarmScheduler.cancel(appContext, event)
}

class AndroidCalendarSyncPort(
    context: Context,
    private val repository: ShivViewRepository,
    private val permissions: ActionPermissionPort,
    private val diagnostics: DiagnosticsPort
) : CalendarSyncPort {
    private val appContext = context.applicationContext

    override suspend fun synchronize(): CalendarSyncResult {
        if (permissions.stateFor("READ_CALENDAR") == ActionPermissionState.DENIED) {
            recordDiagnosticSafely(DiagnosticCode.CALENDAR_PERMISSION_DENIED)
            return CalendarSyncResult(permissionDenied = true)
        }
        return try {
            CalendarSyncResult(importedCount = withContext(Dispatchers.IO) {
                CalendarSyncer.sync(appContext, repository)
            })
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            recordDiagnosticSafely(DiagnosticCode.CALENDAR_SYNC_FAILED)
            CalendarSyncResult(failed = true)
        }
    }

    private suspend fun recordDiagnosticSafely(code: DiagnosticCode) {
        try {
            diagnostics.record(DiagnosticEvent(DiagnosticArea.CALENDAR, code, System.currentTimeMillis()))
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Diagnostics never change whether a calendar operation is allowed or successful.
        }
    }
}

class AndroidDiagnosticsPort : DiagnosticsPort {
    override suspend fun record(event: DiagnosticEvent) {
        // Only enums and a timestamp reach Android logs; never include action arguments or exception text.
        Log.w("ShivDiagnostics", "${event.area.name}:${event.code.name}:${event.occurredAtMillis}")
    }
}

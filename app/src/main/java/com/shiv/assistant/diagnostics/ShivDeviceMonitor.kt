package com.shiv.assistant.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import com.shiv.assistant.domain.ResourceSnapshot

data class DeviceStats(
    val batteryPercent: Int?,
    val charging: Boolean?,
    val thermalStatus: Int?,
    val availableRamMb: Long?,
    val screenOn: Boolean?
) {
    fun asSnapshot(quietHours: Boolean): ResourceSnapshot? =
        if (batteryPercent == null || charging == null || thermalStatus == null ||
            availableRamMb == null || screenOn == null) null
        else ResourceSnapshot(batteryPercent, charging, thermalStatus, availableRamMb, screenOn, quietHours)
}
object ShivDeviceMonitor {
    fun read(context: Context): DeviceStats {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percentage = if (level >= 0 && scale > 0) (100 * level / scale) else null
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = if (status == -1) null else status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val thermal = if (Build.VERSION.SDK_INT >= 29) power?.currentThermalStatus else null
        val ramInfo = ActivityManager.MemoryInfo()
        val activity = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        activity?.getMemoryInfo(ramInfo)
        return DeviceStats(percentage, charging, thermal,
            if (activity != null) ramInfo.availMem / (1024 * 1024) else null,
            power?.isInteractive)
    }
}

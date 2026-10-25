package com.shiv.assistant.ai

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.PowerManager
import com.shiv.assistant.domain.PowerContext
import com.shiv.assistant.domain.PowerMode
import com.shiv.assistant.domain.PowerPolicy

data class ModelLease(val allowed:Boolean,val reason:String,val freeRamBytes:Long,val mode:PowerMode)

/** Conservative measured gating, not a fabricated S23 performance benchmark. */
class ModelGovernor(private val context:Context) {
    fun lease(role:String):ModelLease {
        val manager=context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info=ActivityManager.MemoryInfo().also { manager.getMemoryInfo(it) }
        val power=context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val battery=context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val pct=battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val hot=if(android.os.Build.VERSION.SDK_INT>=29)
            power.currentThermalStatus>=PowerManager.THERMAL_STATUS_SEVERE else false
        val charging=battery.isCharging
        val mode=PowerPolicy.mode(PowerContext(pct,charging,hot,0))
        val big=role=="conversation"
        val minimum=if(big) 2_500_000_000L else 450_000_000L
        val permitted=info.availMem>minimum && !hot && (!big || pct>=25 || charging)
        return ModelLease(permitted,when{
            hot->"Device too hot; heavy model paused"
            info.availMem<=minimum->"Not enough free RAM for requested model"
            big && pct<25 && !charging->"Battery too low for E2B; try router model"
            else->"Allowed by memory, battery and thermal gate"
        },info.availMem,mode)
    }
}

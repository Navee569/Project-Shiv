package com.shiv.assistant.ai

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.PowerManager
import com.shiv.assistant.domain.PowerContext
import com.shiv.assistant.domain.PowerMode
import com.shiv.assistant.domain.PowerPolicy
import com.shiv.assistant.data.ShivVault
import com.shiv.assistant.data.LocalModelComputeLedger
import com.shiv.assistant.data.DailyEnergyLedger
import com.shiv.assistant.domain.EnergyBudgetPolicy

data class ModelLease(val allowed:Boolean,val reason:String,val freeRamBytes:Long,
    val mode:PowerMode,val maxModelSeconds:Int=0)

/** Conservative measured gating, not a fabricated S23 performance benchmark. */
class ModelGovernor(private val context:Context) {
    fun lease(role:String,alreadyLoaded:Boolean=false):ModelLease {
        val manager=context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info=ActivityManager.MemoryInfo().also { manager.getMemoryInfo(it) }
        val power=context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val battery=context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val pct=battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val hot=if(android.os.Build.VERSION.SDK_INT>=29)
            power.currentThermalStatus>=PowerManager.THERMAL_STATUS_SEVERE else false
        val charging=battery.isCharging
        val ledger=LocalModelComputeLedger(ShivVault(context))
        val remaining=ledger.leaseSeconds(if(role=="conversation")60 else 30)
        val energyLedger=DailyEnergyLedger(context)
        val energyPercent=EnergyBudgetPolicy.usedPercent(
            energyLedger.snapshot().upperBoundNwh,energyLedger.budgetMWh())
        val usedPercent=maxOf(EnergyBudgetPolicy.computePercent(
            ledger.usedMillisecondsToday(),ledger.budgetSeconds()),energyPercent?:0)
        val mode=PowerPolicy.mode(PowerContext(pct,charging,hot,usedPercent))
        val big=role=="conversation"
        // Loading needs headroom for weights; an already-resident model has paid that cost.
        val minimum=if(big && !alreadyLoaded) 2_500_000_000L else 450_000_000L
        val permitted=remaining>0 && energyPercent!=100 && info.availMem>minimum && !info.lowMemory && !hot &&
            (!big || pct>=25 || charging)
        return ModelLease(permitted,when{
            remaining==0->"Daily owner-selected local compute limit exhausted"
            energyPercent==100->"Whole-device energy during Shiv windows reached the owner cap; other activity may contribute"
            hot->"Device too hot; heavy model paused"
            info.lowMemory->"Device reports low memory; local model paused"
            info.availMem<=minimum->"Not enough free RAM for requested model"
            big && pct<25 && !charging->"Battery too low for E2B; try router model"
            else->"Allowed by memory, battery and thermal gate"
        },info.availMem,mode,remaining)
    }
}

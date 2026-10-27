package com.shiv.assistant.data

import android.content.Context
import androidx.room.withTransaction
import com.shiv.assistant.domain.EnergyBudgetPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.time.LocalDate

data class DailyEnergySnapshot(val modelWindowNwh:Long,val micWindowNwh:Long,
    val micMs:Long,val ttsMs:Long,val energySamples:Int) {
    val upperBoundNwh:Long? get()=if(energySamples>0) modelWindowNwh+micWindowNwh else null
}

/** Whole-device drop in observation windows; never represented as attributable energy. */
class DailyEnergyLedger(context:Context) {
    private val database=EncryptedDatabase.open(context)
    private val dao=database.dao()
    private val key="daily_energy_windows_v1"
    fun budgetMWh():Int=runBlocking(Dispatchers.IO) {
        dao.vault("daily_energy_budget_mwh")?.value?.toIntOrNull()
            ?.takeIf { it in 50..1000 } ?: 200
    }
    fun setBudgetMWh(value:Int) {
        require(value in 50..1000)
        runBlocking(Dispatchers.IO) {
            dao.putVault(VaultEntry("daily_energy_budget_mwh",value.toString()))
        }
    }
    fun snapshot():DailyEnergySnapshot=runBlocking(Dispatchers.IO) {
        parse(dao.vault(key)?.value)
    }
    suspend fun recordModelWindow(dropNwh:Long?)=record("model",0L,dropNwh)
    suspend fun recordMicWindow(durationMs:Long,dropNwh:Long?)=record("mic",durationMs,dropNwh)
    suspend fun recordTts(durationMs:Long)=record("tts",durationMs,null)
    private suspend fun record(kind:String,durationMs:Long,dropNwh:Long?) {
        if(durationMs<=0 && dropNwh==null)return
        database.withTransaction {
            val old=parse(dao.vault(key)?.value)
            val energy=dropNwh?.takeIf { it>0 }?.coerceAtMost(1_000_000_000L)
            val next=when(kind) {
                "model"->old.copy(modelWindowNwh=old.modelWindowNwh+(energy?:0L),
                    energySamples=old.energySamples+if(energy!=null)1 else 0)
                "mic"->old.copy(micWindowNwh=old.micWindowNwh+(energy?:0L),
                    micMs=old.micMs+durationMs.coerceIn(0L,86_400_000L),
                    energySamples=old.energySamples+if(energy!=null)1 else 0)
                else->old.copy(ttsMs=old.ttsMs+durationMs.coerceIn(0L,86_400_000L))
            }
            dao.putVault(VaultEntry(key,JSONObject().put("day",LocalDate.now().toString())
                .put("modelNwh",next.modelWindowNwh).put("micNwh",next.micWindowNwh)
                .put("micMs",next.micMs).put("ttsMs",next.ttsMs)
                .put("samples",next.energySamples).toString()))
        }
    }
    private fun parse(raw:String?):DailyEnergySnapshot {
        val value=JSONObject(raw?:"{}")
        if(value.optString("day")!=LocalDate.now().toString())
            return DailyEnergySnapshot(0,0,0,0,0)
        return DailyEnergySnapshot(value.optLong("modelNwh").coerceAtLeast(0),
            value.optLong("micNwh").coerceAtLeast(0),value.optLong("micMs").coerceAtLeast(0),
            value.optLong("ttsMs").coerceAtLeast(0),value.optInt("samples").coerceAtLeast(0))
    }
    fun report():String {
        val value=snapshot()
        val percent=EnergyBudgetPolicy.usedPercent(value.upperBoundNwh,budgetMWh())
        return "Mic session "+(value.micMs/60_000L)+" min; TTS speech "+(value.ttsMs/60_000L)+
            " min; whole-device drop in Shiv windows "+
            (value.upperBoundNwh?.let { "%.2f".format(java.util.Locale.ROOT,it/1_000_000.0)+" mWh" }
                ?:"unavailable")+" of "+budgetMWh()+" mWh owner cap"+
            (percent?.let { " ($it%)" }?:"")+". Windows may overlap and include other apps."
    }
}

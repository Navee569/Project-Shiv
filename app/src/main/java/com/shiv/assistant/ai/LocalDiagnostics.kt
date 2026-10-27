package com.shiv.assistant.ai

/** No estimated tokens or attributed model energy: invalid observations stay unavailable. */
object LocalDiagnostics {
    fun positiveFinite(value:Double):Double?=value.takeIf { it.isFinite() && it>0.0 }
    fun tokenCount(value:Int):Int?=value.takeIf { it>0 }
    fun deviceEnergyDropNwh(start:Long,end:Long,discharging:Boolean):Long? =
        if(discharging && start>0L && end>0L && end<start) start-end else null
}

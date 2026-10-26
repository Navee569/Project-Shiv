package com.shiv.assistant.domain

/** Deterministic conservative local compute allowance, not a battery mAh claim. */
object LocalComputeBudget {
    fun remainingSeconds(usedMs:Long, budgetSeconds:Int):Int {
        require(budgetSeconds in 60..3600)
        val safeUsed=usedMs.coerceAtLeast(0)
        val usedSeconds=safeUsed/1000L + if(safeUsed%1000L>0) 1 else 0
        return (budgetSeconds.toLong()-usedSeconds).coerceIn(0L,3600L).toInt()
    }
    fun leaseSeconds(usedMs:Long, budgetSeconds:Int, desired:Int):Int =
        minOf(remainingSeconds(usedMs,budgetSeconds),desired.coerceIn(0,120))
}

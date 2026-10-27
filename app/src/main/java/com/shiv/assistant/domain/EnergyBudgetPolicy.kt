package com.shiv.assistant.domain

object EnergyBudgetPolicy {
    fun usedPercent(observedNwh:Long?,budgetMWh:Int):Int? {
        require(budgetMWh in 50..1000)
        if(observedNwh==null)return null
        return ((observedNwh.coerceAtLeast(0L)/1_000_000.0)/budgetMWh*100)
            .toInt().coerceIn(0,100)
    }
    fun computePercent(usedMs:Long,budgetSeconds:Int):Int {
        require(budgetSeconds in 60..3600)
        return ((usedMs.coerceAtLeast(0L)/1000.0)/budgetSeconds*100).toInt().coerceIn(0,100)
    }
}

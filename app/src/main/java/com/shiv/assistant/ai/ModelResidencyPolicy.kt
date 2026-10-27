package com.shiv.assistant.ai

/** Conservative local thresholds; actual device fit must be measured by the owner. */
object ModelResidencyPolicy {
    fun canCoexist(freeRamBytes:Long):Boolean=freeRamBytes>=4_000_000_000L
    fun slowConversation(lastReplyMs:Long?):Boolean=lastReplyMs!=null && lastReplyMs>12_000L
}

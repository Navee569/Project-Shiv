package com.shiv.assistant.voice

/** Converts streaming local model fragments into short speakable units. */
class SpeechChunker(private val maxCharacters:Int=160) {
    private val pending=StringBuilder()
    init { require(maxCharacters in 40..500) }
    fun accept(fragment:String):List<String> {
        pending.append(fragment)
        val ready=mutableListOf<String>()
        while(true) {
            val boundary=pending.indexOfAny(charArrayOf('.','?','!','।','\n'))
            val cut=when {
                boundary>=0 && boundary<maxCharacters->boundary+1
                pending.length>=maxCharacters->
                    pending.substring(0,maxCharacters).lastIndexOf(' ').takeIf { it>20 }
                        ?: maxCharacters
                else->break
            }
            pending.substring(0,cut).trim().takeIf { it.isNotEmpty() }?.let(ready::add)
            pending.delete(0,cut)
        }
        return ready
    }
    fun finish():String=pending.toString().trim().also { pending.clear() }
}

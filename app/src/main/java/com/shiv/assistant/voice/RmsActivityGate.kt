package com.shiv.assistant.voice

/** A conservative activity hint for recognizer duty cycling, never for identity or text. */
class RmsActivityGate {
    private var floor=0f
    private var samples=0
    private var consecutive=0

    fun observe(level:Float):Boolean {
        if(!level.isFinite())return false
        val value=level.coerceIn(0f,20f)
        if(samples<12) {
            floor=(floor*samples+value)/(samples+1)
            samples++
            return false
        }
        val active=value>floor+3f && value>=4f
        consecutive=if(active) consecutive+1 else 0
        if(!active)floor=floor*0.95f+value*0.05f
        return consecutive>=3
    }
    fun reset() { floor=0f;samples=0;consecutive=0 }
}

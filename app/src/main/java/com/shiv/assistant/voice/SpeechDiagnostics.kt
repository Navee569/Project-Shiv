package com.shiv.assistant.voice

import android.os.SystemClock

/** Process-local timings. TTS callback marks engine start, not audible first word. */
object SpeechDiagnostics {
    @Volatile private var endOfSpeechAt:Long?=null
    @Volatile var lastRecognitionMs:Long?=null
        private set
    @Volatile var lastEndToTtsStartMs:Long?=null
        private set
    @Volatile var lastEndToFirstAudioMs:Long?=null
        private set

    fun begin() {
        endOfSpeechAt=null;lastRecognitionMs=null;lastEndToTtsStartMs=null
        lastEndToFirstAudioMs=null
    }
    fun ended() { endOfSpeechAt=SystemClock.elapsedRealtime() }
    fun recognized() {
        lastRecognitionMs=endOfSpeechAt?.let { (SystemClock.elapsedRealtime()-it).takeIf { n -> n>=0 } }
    }
    fun ttsStarted() {
        if(lastEndToTtsStartMs==null)
            lastEndToTtsStartMs=endOfSpeechAt?.let {
                (SystemClock.elapsedRealtime()-it).takeIf { n -> n>=0 }
            }
    }
    fun firstAudioAvailable() {
        if(lastEndToFirstAudioMs==null)
            lastEndToFirstAudioMs=endOfSpeechAt?.let {
                (SystemClock.elapsedRealtime()-it).takeIf { n -> n>=0 }
            }
    }
    fun report():String="Speech end → transcription: "+(lastRecognitionMs?.let { "$it ms" }?:"unavailable")+
        " | Speech end → TTS engine start: "+(lastEndToTtsStartMs?.let { "$it ms" }?:"unavailable")+
        " | Speech end → first TTS audio buffer: "+(lastEndToFirstAudioMs?.let { "$it ms" }?:"unavailable")
}

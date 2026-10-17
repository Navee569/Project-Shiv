package app.shiv

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Speaks only with an installed offline TTS voice. */
class OfflineSpeechOutput(context: Context) : TextToSpeech.OnInitListener {
    private var engine: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var localVoiceReady = false

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val tts = engine ?: return
        val localVoice = tts.voices?.firstOrNull { !it.isNetworkConnectionRequired && it.locale.language == Locale.getDefault().language } ?: return
        tts.voice = localVoice
        localVoiceReady = true
    }

    fun speak(text: String) {
        if (localVoiceReady && text.isNotBlank()) engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "shiv-reply-${System.currentTimeMillis()}")
    }

    fun close() { engine?.stop(); engine?.shutdown(); engine = null; localVoiceReady = false }
}

package app.shiv

import android.content.Context
import app.shiv.domain.speech.SpeechRecognitionSession

/** One-shot microphone input using the phone's on-device recognizer only. */
class SpeechInput(context: Context, onText: (String) -> Unit, onError: (Int) -> Unit) : SpeechRecognitionSession {
    private val transcriber = Brain().createOnDeviceTranscriber(context.applicationContext, onText, onError)
    override val isAvailable: Boolean get() = transcriber != null
    override fun start() { transcriber?.start() }
    override fun close() { transcriber?.close() }
}

package app.shiv.domain.speech

/** One-shot speech-to-text capability. Implementations must disclose local availability. */
interface SpeechRecognitionSession : AutoCloseable {
    val isAvailable: Boolean
    fun start()
    override fun close()
}

/** Speech output capability independent of Android's TTS engine. */
interface SpeechSynthesis : AutoCloseable {
    fun speak(text: String)
    override fun close()
}

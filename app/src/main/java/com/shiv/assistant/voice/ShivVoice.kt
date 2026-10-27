package com.shiv.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import com.shiv.assistant.data.DailyEnergyLedger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Explicit language, explicit on-device STT. No network recognizer fallback. */
class ShivVoice(private val context:Context) {
    private var recognizer:SpeechRecognizer?=null
    private var tts:TextToSpeech?=null
    private var ttsReady=false
    private var ttsError:String?=null
    private var recognitionGeneration=0L
    private val ttsStarted=ConcurrentHashMap<String,Long>()
    private fun recordTts(utteranceId:String?) {
        val started=utteranceId?.let {ttsStarted.remove(it)}?:return
        val elapsed=(SystemClock.elapsedRealtime()-started).coerceAtLeast(0L)
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {DailyEnergyLedger(context.applicationContext).recordTts(elapsed)}
        }
    }

    fun startRecognition(tag:String, onPartial:(String)->Unit,onResult:(String)->Unit,onFailure:(String)->Unit) {
        stopListening()
        val generation=recognitionGeneration
        SpeechDiagnostics.begin()
        if(Build.VERSION.SDK_INT<33 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            onFailure("Offline recognition is not available. Install an offline language pack; cloud recognition was not started.")
            return
        }
        val supported=setOf("kn-IN","te-IN","en-IN")
        if(tag !in supported) { onFailure("Choose Kannada, Telugu or English");return }
        try {
            val service=SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            recognizer=service
            service.setRecognitionListener(object:RecognitionListener {
                override fun onReadyForSpeech(p0:Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(p0:Float) {}
                override fun onBufferReceived(p0:ByteArray?) {}
                override fun onEndOfSpeech() {
                    if(generation==recognitionGeneration)SpeechDiagnostics.ended()
                }
                override fun onError(error:Int) {
                    if(generation==recognitionGeneration)
                        onFailure("On-device recognizer error code $error. Check offline language data.")
                }
                override fun onResults(results:Bundle?) {
                    if(generation!=recognitionGeneration)return
                    SpeechDiagnostics.recognized()
                    onResult(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty())
                }
                override fun onPartialResults(results:Bundle?) {
                    if(generation==recognitionGeneration)
                        onPartial(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty())
                }
                override fun onEvent(p0:Int,p1:Bundle?) {}
            })
            val intent=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE,tag)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true)
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true)
            service.startListening(intent)
        } catch(e:Exception) { stopListening();onFailure(e.message?:"Offline recognition unavailable") }
    }
    fun initSpeech(onReady:(Boolean,String?)->Unit) {
        tts?.shutdown()
        tts=TextToSpeech(context,{code->
            ttsReady=code==TextToSpeech.SUCCESS
            ttsError=if(ttsReady)null else "Speech Services by Google is unavailable"
            onReady(ttsReady,ttsError)
        },"com.google.android.tts")
        tts?.setOnUtteranceProgressListener(object:UtteranceProgressListener() {
            override fun onStart(utteranceId:String?) {
                if(utteranceId!=null)ttsStarted[utteranceId]=SystemClock.elapsedRealtime()
                SpeechDiagnostics.ttsStarted()
            }
            override fun onAudioAvailable(utteranceId:String?,audio:ByteArray?) {
                SpeechDiagnostics.firstAudioAvailable()
            }
            override fun onDone(utteranceId:String?) {recordTts(utteranceId)}
            @Deprecated("Platform callback") override fun onError(utteranceId:String?) {recordTts(utteranceId)}
        })
    }
    fun speak(text:String,tag:String):String? = speakInternal(text,tag,TextToSpeech.QUEUE_FLUSH)
    fun speakQueued(text:String,tag:String):String? = speakInternal(text,tag,TextToSpeech.QUEUE_ADD)
    private fun speakInternal(text:String,tag:String,queueMode:Int):String? {
        if(!ttsReady)return "Google speech engine is not ready"
        val engine=tts?:return "Install Speech Services by Google"
        val locale=when(tag) {
            "kn-IN"->Locale("kn","IN")
            "te-IN"->Locale("te","IN")
            "en-IN"->Locale("en","IN")
            else->return "Language not supported"
        }
        val available=engine.isLanguageAvailable(locale)
        if(available<TextToSpeech.LANG_AVAILABLE)
            return "Missing $tag voice. Settings > General management > Language > Text-to-speech > Preferred engine > Speech Services by Google > gear > Install voice data."
        engine.language=locale
        return if(engine.speak(text,queueMode,null,"shiv-speech-"+System.nanoTime())==
            TextToSpeech.SUCCESS) null else "Speech engine rejected the utterance"
    }
    fun stopListening() {
        recognitionGeneration++
        recognizer?.cancel();recognizer?.destroy();recognizer=null
    }
    fun stopSpeech() { tts?.stop() }
    fun close() { stopListening();tts?.stop();tts?.shutdown();tts=null;ttsReady=false }
}

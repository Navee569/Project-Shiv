package com.shiv.assistant.voice

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import com.shiv.assistant.automation.SignalWorker
import com.shiv.assistant.data.ShivRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Owner-started, bounded 10-minute OFFLINE mic session.
 *
 * User must be foreground when starting it and Android displays an ongoing
 * microphone notification with STOP. No wake word, call recording, cloud STT
 * fallback, secret screen capture or autonomous actions. Recognized safe
 * utterances enter the same encrypted ASK_FIRST decision path as typed input.
 *
 * Some Android/One UI speech engines cannot restart recognition indefinitely.
 * Those environments stop safely rather than silently switching to cloud.
 */
class ShivMicSessionService : Service() {
    companion object {
        const val ACTION_START = "com.shiv.assistant.action.START_LOCAL_MIC"
        const val ACTION_STOP = "com.shiv.assistant.action.STOP_LOCAL_MIC"
        const val EXTRA_LOCALE = "language"
        const val EXTRA_REQUIRE_WAKE = "require_wake_phrase"
        const val EXTRA_ALLOW_SLEEP = "allow_sleep_hours"
        private const val CHANNEL = "shiv_local_mic"
        private const val NOTIFICATION_ID = 5160
    }
    private val main=Handler(Looper.getMainLooper())
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private var startedAt=0L
    private var errors=0
    private var active=false
    private var locale="en-IN"
    private var recognizer: SpeechRecognizer?=null
    private var listening=false
    private var requireWake=false
    private var allowSleep=false
    private var proximityCm:Float?=null
    private var proximityMaxCm:Float?=null
    private var lux:Float?=null
    private var sensors:SensorManager?=null
    private val sensorListener=object:SensorEventListener {
        override fun onAccuracyChanged(sensor:Sensor?,accuracy:Int) {}
        override fun onSensorChanged(event:SensorEvent) {
            when(event.sensor.type) {
                Sensor.TYPE_PROXIMITY->proximityCm=event.values.firstOrNull()
                Sensor.TYPE_LIGHT->lux=event.values.firstOrNull()
            }
            updateCaptureGate()
        }
    }
    private val monitor = object : Runnable {
        override fun run() {
            if(!active)return
            if(!allowed()) { stopSession();return }
            updateCaptureGate()
            main.postDelayed(this,10_000L)
        }
    }
    override fun onBind(intent:Intent?):IBinder?=null

    override fun onStartCommand(intent:Intent?, flags:Int, startId:Int):Int {
        if(intent?.action==ACTION_STOP) {
            stopSession()
            return START_NOT_STICKY
        }
        if(intent?.action!=ACTION_START) {
            stopSession()
            return START_NOT_STICKY
        }
        if(active) return START_NOT_STICKY
        val requested=intent.getStringExtra(EXTRA_LOCALE).orEmpty()
        if(requested !in setOf("en-IN","kn-IN","te-IN") ||
            Build.VERSION.SDK_INT<33 ||
            !SpeechRecognizer.isOnDeviceRecognitionAvailable(this) ||
            checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) {
            stopSession()
            return START_NOT_STICKY
        }
        locale=requested
        requireWake=intent.getBooleanExtra(EXTRA_REQUIRE_WAKE,false)
        allowSleep=intent.getBooleanExtra(EXTRA_ALLOW_SLEEP,false)
        startedAt=SystemClock.elapsedRealtime()
        active=true
        errors=0
        val manager=getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(CHANNEL,
            "Shiv offline microphone",NotificationManager.IMPORTANCE_LOW))
        val stopAction=PendingIntent.getService(this,NOTIFICATION_ID,
            Intent(this,ShivMicSessionService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification:Notification=NotificationCompat.Builder(this,CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Shiv • Local microphone active")
            .setContentText("Offline session; pauses in pocket/sleep hours, stops within 10 minutes.")
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel,"STOP",stopAction)
            .build()
        try {
            if(Build.VERSION.SDK_INT>=29) startForeground(NOTIFICATION_ID,notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            else startForeground(NOTIFICATION_ID,notification)
            if(!allowed()) {stopSession();return START_NOT_STICKY}
            recognizer=SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            sensors=getSystemService(Context.SENSOR_SERVICE) as SensorManager
            sensors?.getDefaultSensor(Sensor.TYPE_PROXIMITY)?.let { sensor ->
                proximityMaxCm=sensor.maximumRange
                sensors?.registerListener(sensorListener,sensor,SensorManager.SENSOR_DELAY_NORMAL)
            }
            sensors?.getDefaultSensor(Sensor.TYPE_LIGHT)?.let { sensor ->
                sensors?.registerListener(sensorListener,sensor,SensorManager.SENSOR_DELAY_NORMAL)
            }
            recognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params:Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB:Float) {}
                override fun onBufferReceived(buffer:ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error:Int) {
                    if(!active)return
                    listening=false
                    if(!captureAllowed())return
                    errors++
                    if(errors>=MicrophoneSessionPolicy.MAX_ERRORS) stopSession()
                    else scheduleNext()
                }
                override fun onResults(results:Bundle?) {
                    if(!active)return
                    listening=false
                    if(!captureAllowed()) { scheduleNext();return }
                    errors=0
                    val utterance=MicrophoneSessionPolicy.wakeCommand(
                        results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull().orEmpty(),requireWake)
                    if(utterance!=null) {
                        scope.launch {
                            // Only owner's explicitly started mic can produce this source.
                            // Sensitive phrases/codes are filtered before durable storage.
                            val id=ShivRepository(applicationContext)
                                .incoming("owner_mic_session",utterance,priority=5)
                            if(id!=null)SignalWorker.schedule(applicationContext)
                        }
                    }
                    scheduleNext()
                }
                override fun onPartialResults(results:Bundle?) {}
                override fun onEvent(eventType:Int, params:Bundle?) {}
            })
            startRecognizing()
            main.postDelayed(monitor,10_000L)
        } catch (_:Exception) {
            stopSession()
        }
        return START_NOT_STICKY
    }
    private fun powerOk():Boolean {
        val battery=registerReceiver(null,IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level=battery?.getIntExtra(BatteryManager.EXTRA_LEVEL,-1)?:-1
        val scale=battery?.getIntExtra(BatteryManager.EXTRA_SCALE,-1)?:-1
        val percent=if(level>=0 && scale>0) level*100/scale else -1
        val manager=getSystemService(Context.POWER_SERVICE) as PowerManager
        val thermal=if(Build.VERSION.SDK_INT>=29)manager.currentThermalStatus else 0
        return MicrophoneSessionPolicy.mayContinue(startedAt,SystemClock.elapsedRealtime(),
            percent,thermal,Build.VERSION.SDK_INT>=33 &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(this),errors)
    }
    private fun allowed():Boolean=active &&
        checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED &&
        powerOk()

    private fun captureAllowed():Boolean=allowed() &&
        !MicrophoneSessionPolicy.pocketed(proximityCm,proximityMaxCm,lux) &&
        (allowSleep || !MicrophoneSessionPolicy.sleepHours(java.time.LocalTime.now().hour))
    private fun updateCaptureGate() {
        if(!active)return
        if(!captureAllowed()) {
            if(listening) {
                listening=false
                runCatching { recognizer?.cancel() }
            }
        } else if(!listening) startRecognizing()
    }

    private fun startRecognizing() {
        if(!allowed()) {stopSession();return}
        if(!captureAllowed() || listening)return
        val intent=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE,locale)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,false)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true)
        try { recognizer?.startListening(intent); listening=true }
        catch (_:Exception) {stopSession()}
    }
    private fun scheduleNext() {
        main.postDelayed({if(active) startRecognizing()},1500L)
    }
    private fun stopSession() {
        active=false
        listening=false
        main.removeCallbacksAndMessages(null)
        sensors?.unregisterListener(sensorListener)
        sensors=null
        runCatching {recognizer?.cancel()}
        runCatching {recognizer?.destroy()}
        recognizer=null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    override fun onDestroy() {
        active=false
        listening=false
        main.removeCallbacksAndMessages(null)
        sensors?.unregisterListener(sensorListener)
        sensors=null
        runCatching {recognizer?.destroy()}
        recognizer=null
        scope.cancel()
        super.onDestroy()
    }
}

package com.shiv.assistant.awareness

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.shiv.assistant.data.ShivRepository
import com.shiv.assistant.automation.SignalWorker
import com.shiv.assistant.domain.SensitiveAppDenylist
import com.shiv.assistant.data.ShivVault
import kotlinx.coroutines.*

/**
 * Owner-enabled, on-demand awareness only. No raw screen text persisted.
 * App exclusion is checked before inspecting a node or dispatching work.
 */
class ShivAwareness : AccessibilityService() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private var lastCaptured=0L
    override fun onAccessibilityEvent(event:AccessibilityEvent?) {
        if(event==null) return
        val name=event.packageName?.toString()?:return
        if(SensitiveAppDenylist.excluded(name))return
        // Owner explicitly types the ONE package being inspected. Other apps are
        // never traversed even when the Accessibility service is system-enabled.
        val vault=try { ShivVault(applicationContext) } catch (_:Exception) { return }
        if(!ScreenInspectionPolicy.permits(name, vault.get("screen_allowed_package")))return
        val prefs=getSharedPreferences("shiv_awareness_control",Context.MODE_PRIVATE)
        if(!prefs.getBoolean("owner_enabled",false) || prefs.getBoolean("paused",true))return
        // A capture token is issued only by an owner tap, expires after 15 seconds,
        // and is consumed before any node is traversed.
        val until=prefs.getLong("capture_until",0L)
        if(until<System.currentTimeMillis())return
        prefs.edit().remove("capture_until").apply()
        if(System.currentTimeMillis()-lastCaptured<30_000L)return
        val source=rootInActiveWindow?:return
        try {
            val facts=mutableListOf<String>()
            extractFacts(source,facts,0)
            if(facts.isEmpty())return
            lastCaptured=System.currentTimeMillis()
            scope.launch {
                val id=ShivRepository(applicationContext).incoming("accessibility",facts.distinct().take(5).joinToString("; "),
                    priority=3,packageName=name)
                if(id!=null)SignalWorker.schedule(applicationContext)
            }
        } finally { source.recycle() }
    }
    private fun extractFacts(node:AccessibilityNodeInfo,facts:MutableList<String>,depth:Int) {
        if(depth>6 || facts.size>=5 || node.isPassword || !node.isVisibleToUser)return
        // Process *only* tightly-scoped date/time facts, never whole screen content.
        val text=node.text?.toString().orEmpty()
        if(text.length in 4..100 && !Regex("\\b[0-9]{4,}\\b").containsMatchIn(text) && Regex("(?i)\\b(today|tomorrow|meeting|deadline|appointment|\\d{1,2}:\\d{2})\\b")
                .containsMatchIn(text) && !Regex("(?i)\\b(otp|password|pin|cvv|card|account)\\b").containsMatchIn(text))
            facts.add(text.take(100))
        for(i in 0 until minOf(node.childCount,30)) {
            val child=node.getChild(i)?:continue
            try { extractFacts(child,facts,depth+1) } finally { child.recycle() }
        }
    }
    override fun onInterrupt() {}
    override fun onDestroy() { scope.cancel();super.onDestroy() }
}

/** Deterministic exact-match source policy. An unconfigured service cannot read screens. */
object ScreenInspectionPolicy {
    fun permits(source:String, ownerPackage:String?):Boolean {
        val allowed=ownerPackage?.trim()?.lowercase() ?: return false
        if(!allowed.matches(Regex("[a-z0-9_]+(?:\\.[a-z0-9_]+)+")))return false
        return source.trim().lowercase()==allowed &&
            !SensitiveAppDenylist.excluded(allowed)
    }
}

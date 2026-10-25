package com.shiv.assistant

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.*
import com.shiv.assistant.ai.*
import com.shiv.assistant.automation.SignalWorker
import com.shiv.assistant.data.*
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Live developer panels are honest about imported, loaded, and missing models. */
class PhaseOneController(private val context: Context, val hub: ShivBrainHub) {
    private val repository=ShivRepository(context)
    private val dao=EncryptedDatabase.open(context).dao()
    val models=mutableStateListOf<ModelRecord>()
    val upcoming=mutableStateListOf<EventRecord>()
    val beliefs=mutableStateListOf<BeliefRecord>()
    val decisions=mutableStateListOf<DecisionTraceRecord>()
    var candidate by mutableStateOf<Uri?>(null)
        private set
    var progress by mutableStateOf("No model import in progress")
        private set
    var loading by mutableStateOf(false)
        private set
    var issue by mutableStateOf<String?>(null)
        private set
    var notificationCount by mutableIntStateOf(0)
        private set
    fun openAccessibilitySettings() {
        context.startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    fun requestOneShotAwareness() {
        context.getSharedPreferences("shiv_awareness_control",Context.MODE_PRIVATE).edit()
            .putBoolean("owner_enabled",true).putBoolean("paused",false)
            .putLong("capture_until",System.currentTimeMillis()+15_000L).apply()
        progress="One-shot permission active for 15 seconds. Open the allowed screen."
    }
    suspend fun readPhoneCalendar() {
        val events=com.shiv.assistant.integrations.CalendarAdapter(context).inspectNext48Hours()
        val database=EncryptedDatabase.open(context).dao()
        for(event in events)database.putEvent(event)
        reload()
    }
    fun setPatternLearning(on:Boolean) {
        context.getSharedPreferences("shiv_learning",Context.MODE_PRIVATE).edit()
            .putBoolean("owner_enabled",on).apply()
        if(on) com.shiv.assistant.automation.PatternWorker.enable(context)
        else androidx.work.WorkManager.getInstance(context)
            .cancelUniqueWork("shiv-opt-in-pattern-learning")
        progress=if(on)"Charging-only hypothesis learning enabled" else "Passive learning disabled"
    }
    fun enablePostCallCheckIn(on:Boolean) {
        context.getSharedPreferences("shiv_call_check_in",Context.MODE_PRIVATE).edit()
            .putBoolean("owner_enabled",on).apply()
        progress=if(on)"Post-call check-in metadata enabled" else "Post-call checks disabled"
    }
    fun selectModel(uri:Uri?) {
        candidate=uri
        if(uri!=null)runCatching {
            context.contentResolver.takePersistableUriPermission(uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    fun cancelModel() { candidate=null }
    suspend fun reload() = withContext(Dispatchers.IO) {
        val stored=dao.models()
        val events=repository.upcoming(System.currentTimeMillis(),System.currentTimeMillis()+48*3600_000L)
        val storedBeliefs=dao.beliefs()
        val traces=dao.traces(30)
        val count=dao.pendingCount()
        withContext(Dispatchers.Main) {
            models.clear();models.addAll(stored)
            upcoming.clear();upcoming.addAll(events)
            beliefs.clear();beliefs.addAll(storedBeliefs)
            decisions.clear();decisions.addAll(traces)
            notificationCount=count
        }
    }
    suspend fun importModel(role:String) {
        val uri=candidate?:return
        loading=true; issue=null
        try {
            val record=ModelImporter(context).import(uri,role) { p ->
                progress="Copied "+(p.bytesCopied/(1024*1024))+" MB of "+(p.totalBytes/(1024*1024))+" MB"
            }
            progress="Imported "+record.name+" with SHA-256 "+record.sha256.take(12)+"…"
            candidate=null
            reload()
        } catch(e:Exception) { issue=e.message?:"Model import failed" }
        finally { loading=false }
    }
    suspend fun loadModel(record:ModelRecord) {
        loading=true;issue=null
        try {
            val lease=ModelGovernor(context).lease(record.role)
            require(lease.allowed) { lease.reason }
            // E2B and 1B cannot coexist when RAM pressure is high.
            if(record.role=="conversation" && lease.freeRamBytes<4_000_000_000L)
                hub.router.unload()
            val brain=if(record.role=="router")hub.router else hub.conversation
            brain.load(File(record.uri))
            progress="Model loaded: "+record.name
        } catch(e:Exception) { issue=e.message?:"LiteRT failed to load model" }
        finally { loading=false }
    }
    suspend fun removeModel(record:ModelRecord) {
        if(hub.router.status().modelName==File(record.uri).name)hub.router.unload()
        if(hub.conversation.status().modelName==File(record.uri).name)hub.conversation.unload()
        ModelImporter(context).remove(record)
        reload()
    }
    suspend fun submitSignal(source:String,text:String,priority:Int=4) {
        val id=repository.incoming(source,text,priority)
        if(id!=null)SignalWorker.schedule(context)
        reload()
    }
    suspend fun removeBelief(id:String) { repository.forgetBelief(id);reload() }
    fun modelStatus():String {
        val router=hub.router.status()
        val chat=hub.conversation.status()
        return "Router: "+(router.modelName?:"not loaded")+
            " | Conversation: "+(chat.modelName?:"not loaded")+
            " | Queue: "+hub.inference.pendingCount()+
            " | Load ms: "+(chat.lastLoadMs?.toString()?:"not measured")+
            " | Reply ms: "+(chat.lastReplyMs?.toString()?:"not measured")
    }
}

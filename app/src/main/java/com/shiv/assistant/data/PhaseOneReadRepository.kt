package com.shiv.assistant.data

import android.content.Context
import com.shiv.assistant.domain.AppSourceAccess
import com.shiv.assistant.domain.AppSourceMode
import com.shiv.assistant.domain.SourceExclusions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One immutable read snapshot; Compose state is updated only after this returns. */
data class PhaseOneSnapshot(
    val models:List<ModelRecord>,val memories:List<MemoryRecord>,
    val events:List<EventRecord>,val tasks:List<TaskRecord>,
    val beliefs:List<BeliefRecord>,val decisions:List<DecisionTraceRecord>,
    val pendingSignals:Int,val failedSignals:Int,
    val deniedPackages:List<String>,val appSourceModes:Map<String,AppSourceMode>
)

class PhaseOneReadRepository(private val context:Context) {
    private val dao=EncryptedDatabase.open(context).dao()
    suspend fun snapshot(now:Long=System.currentTimeMillis()):PhaseOneSnapshot = withContext(Dispatchers.IO) {
        val vault=ShivVault(context)
        PhaseOneSnapshot(
            models=dao.models(),memories=dao.recentMemories(now,100),
            events=dao.events(now,now+48*3600_000L),tasks=dao.pendingTasks(),
            beliefs=dao.beliefs(),decisions=dao.traces(30),
            pendingSignals=dao.pendingCount(),failedSignals=dao.failedSignalCount(),
            deniedPackages=SourceExclusions.fromString(vault.get("app_denied_packages")).sorted(),
            appSourceModes=AppSourceAccess.decode(vault.get(AppSourceAccess.VAULT_KEY))
        )
    }
}

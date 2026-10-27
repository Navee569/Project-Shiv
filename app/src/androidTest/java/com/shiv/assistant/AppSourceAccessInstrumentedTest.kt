package com.shiv.assistant

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shiv.assistant.data.ShivRepository
import com.shiv.assistant.data.ShivVault
import com.shiv.assistant.domain.AppSourceAccess
import com.shiv.assistant.domain.AppSourceMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppSourceAccessInstrumentedTest {
    @Test fun repositoryRejectsBlockedAppFactsBeforeEncryptedQueue() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val vault=ShivVault(context)
        val old=vault.get(AppSourceAccess.VAULT_KEY)
        val app="com.example.shivpolicy"
        val repo=ShivRepository(context)
        try {
            vault.put(AppSourceAccess.VAULT_KEY,
                AppSourceAccess.encode(mapOf(app to AppSourceMode.NOTIFICATIONS_ONLY)))
            assertNull(repo.incoming("accessibility","meeting tomorrow",3,app))
            assertNotNull(repo.incoming("notification","meeting tomorrow",3,app))
            vault.put(AppSourceAccess.VAULT_KEY,
                AppSourceAccess.encode(mapOf(app to AppSourceMode.EXCLUDE)))
            assertNull(repo.incoming("notification","meeting tomorrow",3,app))
            vault.put(AppSourceAccess.VAULT_KEY,"broken policy")
            assertNull(repo.incoming("notification","meeting tomorrow",3,app))
        } finally {
            vault.put(AppSourceAccess.VAULT_KEY,old ?: "")
        }
    }
}

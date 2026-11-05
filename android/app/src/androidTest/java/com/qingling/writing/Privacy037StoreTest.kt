package com.qingling.writing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.test.platform.app.InstrumentationRegistry
import com.qingling.writing.core.datastore.SettingsStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.UUID

class Privacy037StoreTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun scope() = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Test fun consentPersistsAndPreservesExistingPreferences() = runBlocking {
        val file = File(context.cacheDir, "privacy-test-" + UUID.randomUUID() + ".preferences_pb")
        val firstScope = scope()
        val secondScope = scope()
        try {
            val first = SettingsStore(context, PreferenceDataStoreFactory.create(scope = firstScope) { file })
            assertFalse(first.privacyAccepted.first())
            first.setLastAuthor("原有作者")
            first.acceptPrivacyPolicy()
            assertTrue(first.privacyAccepted.first())
            firstScope.coroutineContext[Job]!!.cancelAndJoin()
            val reopened = SettingsStore(context, PreferenceDataStoreFactory.create(scope = secondScope) { file })
            assertTrue(reopened.privacyAccepted.first())
            assertEquals("原有作者", reopened.lastAuthor.first())
        } finally {
            firstScope.coroutineContext[Job]!!.cancelAndJoin()
            secondScope.coroutineContext[Job]!!.cancelAndJoin()
            file.delete()
        }
    }
    @Test fun olderPolicyRequiresNewConsent() = runBlocking {
        val file = File(context.cacheDir, "privacy-test-" + UUID.randomUUID() + ".preferences_pb")
        val scope = scope()
        try {
            val data = PreferenceDataStoreFactory.create(scope = scope) { file }
            data.edit { it[stringPreferencesKey("privacy_consent_version")] = "older-policy" }
            val store = SettingsStore(context, data)
            assertFalse(store.privacyAccepted.first())
            store.acceptPrivacyPolicy()
            assertTrue(store.privacyAccepted.first())
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin(); file.delete() }
    }
    @Test fun unreadablePreferencesKeepGateClosed() = runBlocking {
        val broken = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { throw IOException("unreadable") }
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = throw IOException("unwritable")
        }
        assertFalse(SettingsStore(context, broken).privacyAccepted.first())
    }
    @Test fun failedWriteNeverGrantsConsent() = runBlocking {
        val values = MutableStateFlow<Preferences>(emptyPreferences())
        val broken = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = values
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = throw IOException("disk full")
        }
        val store = SettingsStore(context, broken)
        try { store.acceptPrivacyPolicy(); fail("Expected write failure") } catch (_: IOException) { }
        assertFalse(store.privacyAccepted.first())
    }
}

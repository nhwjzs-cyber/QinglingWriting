package com.qingling.writing.core.datastore

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.*
import com.qingling.writing.core.model.AutoBackupState
import kotlinx.coroutines.flow.*
import java.io.IOException

private val Context.autoBackupPreferences by preferencesDataStore(name = "auto_backup")

class AutoBackupStore(context: Context) {
    private val store = context.applicationContext.autoBackupPreferences
    val state = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map { p ->
        AutoBackupState(p[ENABLED] ?: false, p[FOLDER].orEmpty(), if (p[HOURS] == 24) 24 else 6,
            p[SUCCESS] ?: 0, p[ATTEMPT] ?: 0, p[ERROR].orEmpty(), p[SCHEDULE_ERROR].orEmpty())
    }
    suspend fun configure(folder: String, enabled: Boolean, hours: Int) = store.edit { p ->
        if (p[FOLDER] != folder) { p[SUCCESS] = 0; p[ATTEMPT] = 0 }
        p[FOLDER] = folder; p[ENABLED] = enabled; p[HOURS] = if (hours == 24) 24 else 6; p[ERROR] = ""
    }
    suspend fun attempt(time: Long) { store.edit { it[ATTEMPT] = time } }
    suspend fun success(time: Long) { store.edit { it[SUCCESS] = time; it[ERROR] = "" } }
    suspend fun failure(message: String) { store.edit { it[ERROR] = message } }
    suspend fun scheduleStatus(message: String) { store.edit { it[SCHEDULE_ERROR] = message } }
    private companion object {
        val ENABLED = booleanPreferencesKey("enabled")
        val FOLDER = stringPreferencesKey("folder")
        val HOURS = intPreferencesKey("hours")
        val SUCCESS = longPreferencesKey("success")
        val ATTEMPT = longPreferencesKey("attempt")
        val ERROR = stringPreferencesKey("error")
        val SCHEDULE_ERROR = stringPreferencesKey("schedule_error")
    }
}

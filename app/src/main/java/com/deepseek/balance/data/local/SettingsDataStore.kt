package com.deepseek.balance.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "deepseek_settings")

class SettingsDataStore(private val context: Context) {

    companion object {
        private val API_KEY = stringPreferencesKey("api_key")
        private val REFRESH_INTERVAL = stringPreferencesKey("refresh_interval")

        // Server proxy settings
        private val USE_SERVER_PROXY = stringPreferencesKey("use_server_proxy")
        private val SERVER_URL = stringPreferencesKey("server_url")
    }

    val apiKeyFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[API_KEY]
    }

    val useServerProxyFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[USE_SERVER_PROXY]?.toBoolean() ?: false
    }

    val serverUrlFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[SERVER_URL]
    }

    suspend fun saveApiKey(apiKey: String) {
        context.dataStore.edit { preferences ->
            preferences[API_KEY] = apiKey
        }
    }

    suspend fun clearApiKey() {
        context.dataStore.edit { preferences ->
            preferences.remove(API_KEY)
        }
    }

    suspend fun setServerProxy(useProxy: Boolean, serverUrl: String = "") {
        context.dataStore.edit { preferences ->
            preferences[USE_SERVER_PROXY] = useProxy.toString()
            preferences[SERVER_URL] = serverUrl
        }
    }
}

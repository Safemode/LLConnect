package com.safemode.llconnect.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "llconnect_settings")

/**
 * Persists the connection configuration. Secrets (API key and Basic password) are
 * encrypted with an Android Keystore key via [KeystoreCrypto] before being written to
 * DataStore, and decrypted on read; all other fields are stored as plain text.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val SCHEME = stringPreferencesKey("scheme")
        val HOST = stringPreferencesKey("host")
        val PORT = stringPreferencesKey("port")
        val API_KEY = stringPreferencesKey("api_key")
        val AUTH_MODE = stringPreferencesKey("auth_mode")
        val BASIC_USER = stringPreferencesKey("basic_user")
        val BASIC_PASS = stringPreferencesKey("basic_pass")
        val CULTURE_INVARIANT = booleanPreferencesKey("culture_invariant")
        val FUEL_UNIT = stringPreferencesKey("fuel_unit")
        val SORT_ORDER = stringPreferencesKey("record_sort_order")
        val CACHE_SIZE = stringPreferencesKey("cache_size")
    }

    val config: Flow<ConnectionConfig> = context.dataStore.data.map { prefs ->
        ConnectionConfig(
            scheme = prefs[Keys.SCHEME] ?: "http",
            host = prefs[Keys.HOST] ?: "",
            port = prefs[Keys.PORT] ?: "",
            apiKey = KeystoreCrypto.decrypt(prefs[Keys.API_KEY] ?: ""),
            authMode = runCatching { AuthMode.valueOf(prefs[Keys.AUTH_MODE] ?: "API_KEY") }
                .getOrDefault(AuthMode.API_KEY),
            basicUsername = prefs[Keys.BASIC_USER] ?: "",
            basicPassword = KeystoreCrypto.decrypt(prefs[Keys.BASIC_PASS] ?: ""),
            cultureInvariant = prefs[Keys.CULTURE_INVARIANT] ?: true,
            fuelEconomyUnit = runCatching {
                FuelEconomyUnit.valueOf(prefs[Keys.FUEL_UNIT] ?: "DEFAULT")
            }.getOrDefault(FuelEconomyUnit.DEFAULT),
            recordSortOrder = runCatching {
                RecordSortOrder.valueOf(prefs[Keys.SORT_ORDER] ?: "OLDEST_FIRST")
            }.getOrDefault(RecordSortOrder.OLDEST_FIRST),
            cacheSize = runCatching {
                CacheSize.valueOf(prefs[Keys.CACHE_SIZE] ?: "MB_50")
            }.getOrDefault(CacheSize.MB_50),
        )
    }

    suspend fun save(config: ConnectionConfig) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SCHEME] = config.scheme
            prefs[Keys.HOST] = config.host
            prefs[Keys.PORT] = config.port
            prefs[Keys.API_KEY] = KeystoreCrypto.encrypt(config.apiKey)
            prefs[Keys.AUTH_MODE] = config.authMode.name
            prefs[Keys.BASIC_USER] = config.basicUsername
            prefs[Keys.BASIC_PASS] = KeystoreCrypto.encrypt(config.basicPassword)
            prefs[Keys.CULTURE_INVARIANT] = config.cultureInvariant
            prefs[Keys.FUEL_UNIT] = config.fuelEconomyUnit.name
            prefs[Keys.SORT_ORDER] = config.recordSortOrder.name
            prefs[Keys.CACHE_SIZE] = config.cacheSize.name
        }
    }
}

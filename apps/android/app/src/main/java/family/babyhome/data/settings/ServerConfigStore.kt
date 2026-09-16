package family.babyhome.data.settings

import android.content.Context
import android.net.ConnectivityManager
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.serverSettingsDataStore by preferencesDataStore(name = "server_settings")

@Singleton
class ServerConfigStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val selectedBabyServerUrl = MutableStateFlow<String?>(null)

    private object Keys {
        val identity = stringPreferencesKey("family_username")
        val babyServerUrl = stringPreferencesKey("baby_server_url")
        val immichUrl = stringPreferencesKey("immich_url")
    }
    val identity: Flow<String?> = context.serverSettingsDataStore.data.map { it[Keys.identity] }
    suspend fun saveIdentity(username: String?) {
        context.serverSettingsDataStore.edit { if (username == null) it.remove(Keys.identity) else it[Keys.identity] = username }
    }

    val config: Flow<ServerConfig> = context.serverSettingsDataStore.data.map { preferences ->
        ServerConfig(
            babyServerUrl = migrateOldDefaults(
                preferences[Keys.babyServerUrl],
                oldDefaults = setOf(
                    "http://baby-server:8080",
                    "http://desktop-rcsbtks:8080",
                    "http://192.168.0.25:8080",
                    "http://192.168.0.40:8080",
                ),
                newDefault = DEFAULT_BABY_SERVER_URL,
            ),
            immichUrl = migrateOldDefaults(
                preferences[Keys.immichUrl],
                oldDefaults = setOf(
                    "http://baby-server:2283",
                    "http://desktop-rcsbtks:2283",
                    "http://192.168.0.25:2283",
                    "http://192.168.0.40:2283",
                ),
                newDefault = DEFAULT_IMMICH_URL,
            ),
        )
    }

    /**
     * Uses the fast local address on the home subnet and Tailscale MagicDNS
     * everywhere else. A fresh collection evaluates the current network, so
     * screen loads naturally switch after Wi-Fi/mobile-data changes.
     */
    val activeConfig: Flow<ServerConfig> = combine(config, selectedBabyServerUrl) { saved, selected ->
        when (selected) {
            saved.babyServerUrl -> saved
            TAILSCALE_BABY_SERVER_URL -> saved.copy(
                babyServerUrl = TAILSCALE_BABY_SERVER_URL,
                immichUrl = TAILSCALE_IMMICH_URL,
            )
            else -> if (isOnHomeLan()) saved else saved.copy(
                babyServerUrl = TAILSCALE_BABY_SERVER_URL,
                immichUrl = TAILSCALE_IMMICH_URL,
            )
        }
    }

    fun selectBabyServer(serverUrl: String) {
        selectedBabyServerUrl.value = serverUrl.trimEnd('/')
    }

    suspend fun save(config: ServerConfig) {
        context.serverSettingsDataStore.edit { preferences ->
            preferences[Keys.babyServerUrl] = config.babyServerUrl
            preferences[Keys.immichUrl] = config.immichUrl
        }
    }

    private fun migrateOldDefaults(value: String?, oldDefaults: Set<String>, newDefault: String): String =
        if (value.isNullOrBlank() || value.trimEnd('/') in oldDefaults) newDefault else value

    private fun isOnHomeLan(): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return false
        // A VPN or mobile network can be Android's "active" network even while
        // home Wi-Fi is connected. Inspect every connected network so the Wi-Fi
        // path wins whenever the phone has a 192.168.0.* home address.
        return connectivity.allNetworks.any { network ->
            connectivity.getLinkProperties(network)?.linkAddresses.orEmpty().any { address ->
                address.address.hostAddress?.startsWith("192.168.0.") == true
            }
        }
    }
}

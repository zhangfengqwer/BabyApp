package family.babyhome.domain.settings

import family.babyhome.data.settings.ServerConfig
import kotlinx.coroutines.flow.Flow

interface ServerSettingsRepository {
    val config: Flow<ServerConfig>
    suspend fun save(config: ServerConfig)
    suspend fun testConnection(serverUrl: String): ConnectionTestResult
}

sealed interface ConnectionTestResult {
    data class Success(
        val latencyMs: Long,
        val databaseStatus: String,
        val immichStatus: String,
    ) : ConnectionTestResult

    data class Error(val message: String) : ConnectionTestResult
}


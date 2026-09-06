package family.babyhome.data.settings

import family.babyhome.data.network.HealthApi
import family.babyhome.domain.settings.ConnectionTestResult
import family.babyhome.domain.settings.ServerSettingsRepository
import kotlinx.coroutines.flow.Flow
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultServerSettingsRepository @Inject constructor(
    private val store: ServerConfigStore,
    private val healthApi: HealthApi,
) : ServerSettingsRepository {
    override val config: Flow<ServerConfig> = store.config

    override suspend fun save(config: ServerConfig) = store.save(
        config.copy(
            babyServerUrl = normalizeServerUrl(config.babyServerUrl),
            immichUrl = normalizeServerUrl(config.immichUrl),
        ),
    )

    override suspend fun testConnection(serverUrl: String): ConnectionTestResult {
        val normalized = try {
            normalizeServerUrl(serverUrl)
        } catch (_: IllegalArgumentException) {
            return ConnectionTestResult.Error("服务器地址格式不正确，请填写 http:// 或 https:// 开头的地址")
        }

        return try {
            val response = healthApi.check("$normalized/health")
            if (!response.success) {
                ConnectionTestResult.Error("家庭服务器返回了异常结果")
            } else {
                ConnectionTestResult.Success(
                    latencyMs = response.data.latencyMs,
                    databaseStatus = response.data.services.database,
                    immichStatus = response.data.services.immich,
                )
            }
        } catch (_: UnknownHostException) {
            ConnectionTestResult.Error(connectionHelp)
        } catch (_: SocketTimeoutException) {
            ConnectionTestResult.Error(connectionHelp)
        } catch (_: Exception) {
            ConnectionTestResult.Error(connectionHelp)
        }
    }

    companion object {
        const val connectionHelp =
            "无法连接家庭服务器。请检查 Tailscale 是否已连接、家庭服务器是否开机，以及当前网络是否正常。"

        fun normalizeServerUrl(value: String): String {
            val trimmed = value.trim().trimEnd('/')
            val uri = runCatching { URI(trimmed) }.getOrElse { throw IllegalArgumentException() }
            require(uri.scheme == "http" || uri.scheme == "https")
            require(!uri.host.isNullOrBlank())
            require(uri.userInfo == null && uri.query == null && uri.fragment == null)
            return trimmed
        }
    }
}


package family.babyhome.data.network

import retrofit2.http.GET
import retrofit2.http.Url

interface HealthApi {
    @GET
    suspend fun check(@Url url: String): HealthResponse
}

data class HealthResponse(
    val success: Boolean,
    val data: HealthData,
)

data class HealthData(
    val status: String,
    val services: ServiceStatuses,
    val latencyMs: Long,
    val timestamp: String,
)

data class ServiceStatuses(
    val backend: String,
    val database: String,
    val immich: String,
)


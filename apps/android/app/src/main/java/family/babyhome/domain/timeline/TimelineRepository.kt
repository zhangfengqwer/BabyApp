package family.babyhome.domain.timeline

import family.babyhome.data.network.BabyDto
import family.babyhome.data.network.MomentDto

data class TimelinePage(
    val baby: BabyDto,
    val avatarUrl: String?,
    val moments: List<MomentDto>,
    val nextCursor: String?,
)

interface TimelineRepository {
    suspend fun load(cursor: String? = null, limit: Int = 20): Result<TimelinePage>
}

package family.babyhome.domain.home

import family.babyhome.data.network.BabyDto
import family.babyhome.data.network.MomentDto

data class HomeData(val baby: BabyDto, val moments: List<MomentDto>)

interface HomeRepository {
    suspend fun load(): Result<HomeData>
}


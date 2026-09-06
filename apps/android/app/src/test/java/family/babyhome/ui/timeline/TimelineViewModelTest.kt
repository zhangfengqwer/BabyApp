package family.babyhome.ui.timeline

import family.babyhome.data.network.BabyDto
import family.babyhome.data.network.MomentAuthorDto
import family.babyhome.data.network.MomentCountsDto
import family.babyhome.data.network.MomentDto
import family.babyhome.domain.timeline.TimelinePage
import family.babyhome.domain.timeline.TimelineRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TimelineViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `loads first page and appends next cursor page`() = runTest {
        val repository = object : TimelineRepository {
            override suspend fun load(cursor: String?, limit: Int): Result<TimelinePage> = Result.success(
                if (cursor == null) page(listOf(moment("one")), "next")
                else page(listOf(moment("two")), null),
            )
        }
        val viewModel = TimelineViewModel(repository)
        viewModel.loadInitial()
        advanceUntilIdle()
        assertEquals(listOf("one"), viewModel.state.value.moments.map { it.id })

        viewModel.loadMore()
        advanceUntilIdle()
        assertEquals(listOf("one", "two"), viewModel.state.value.moments.map { it.id })
        assertEquals(null, viewModel.state.value.nextCursor)
    }

    private fun page(items: List<MomentDto>, cursor: String?) = TimelinePage(
        baby = BabyDto("baby", "安安", null, "2024-05-09", null, null),
        avatarUrl = null,
        moments = items,
        nextCursor = cursor,
    )

    private fun moment(id: String) = MomentDto(
        id = id,
        content = id,
        eventDate = "2026-09-01T10:00:00.000Z",
        location = null,
        author = MomentAuthorDto("user", "爸爸", null),
        assets = emptyList(),
        _count = MomentCountsDto(0, 0),
        likedByMe = false,
    )
}

package family.babyhome.domain.publish

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class PublishDatePlannerTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    @Test fun `different capture dates become different groups`() {
        val dates = listOf(Instant.parse("2026-09-01T15:00:00Z"), Instant.parse("2026-09-01T16:00:00Z"), null)
            .map { PublishDatePlanner.date(it, "2026-09-03", true, zone) }.groupingBy { it }.eachCount()
        assertEquals(mapOf("2026-09-01" to 1, "2026-09-02" to 1, "2026-09-03" to 1), dates)
    }
    @Test fun `manual mode overrides media timestamps`() {
        assertEquals("2026-09-03", PublishDatePlanner.date(Instant.parse("2026-09-01T10:00:00Z"), "2026-09-03", false, zone))
    }
    @Test(expected = IllegalArgumentException::class) fun `future date is rejected`() {
        PublishDatePlanner.validate(listOf("2026-09-20"), LocalDate.parse("2026-09-16"))
    }
}

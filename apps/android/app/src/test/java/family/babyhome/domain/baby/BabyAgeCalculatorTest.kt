package family.babyhome.domain.baby

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class BabyAgeCalculatorTest {
    @Test
    fun `formats years months and days`() {
        assertEquals(
            "2岁3个月18天",
            BabyAgeCalculator.format(LocalDate.of(2024, 5, 9), LocalDate.of(2026, 8, 27)),
        )
    }

    @Test
    fun `formats newborn days`() {
        assertEquals(
            "出生18天",
            BabyAgeCalculator.format(LocalDate.of(2026, 8, 9), LocalDate.of(2026, 8, 27)),
        )
    }
}

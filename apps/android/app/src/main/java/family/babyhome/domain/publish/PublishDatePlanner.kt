package family.babyhome.domain.publish

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object PublishDatePlanner {
    fun date(capturedAt: Instant?, fallback: String, automatic: Boolean, zone: ZoneId = ZoneId.systemDefault()): String =
        if (automatic) MediaCaptureTime.usable(capturedAt)?.atZone(zone)?.toLocalDate()?.toString() ?: fallback else fallback

    fun validate(dates: Collection<String>, today: LocalDate = LocalDate.now()) {
        dates.forEach { require(!LocalDate.parse(it).isAfter(today)) }
    }
}

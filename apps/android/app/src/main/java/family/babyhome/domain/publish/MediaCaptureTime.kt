package family.babyhome.domain.publish

import java.time.Instant

object MediaCaptureTime {
    // A zero QuickTime creation timestamp is reported as 1904-01-01, not a real capture date.
    fun usable(value: Instant?, now: Instant = Instant.now()): Instant? = value?.takeIf {
        !it.isBefore(Instant.parse("1970-01-01T00:00:00Z")) && !it.isAfter(now.plusSeconds(86400))
    }
}

package family.babyhome.domain.baby

import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit

object BabyAgeCalculator {
    fun format(birthday: LocalDate, targetDate: LocalDate): String {
        if (targetDate.isBefore(birthday)) return "尚未出生"
        val days = ChronoUnit.DAYS.between(birthday, targetDate)
        if (days < 30) return "出生${days}天"
        val age = Period.between(birthday, targetDate)
        return buildList {
            if (age.years > 0) add("${age.years}岁")
            if (age.months > 0) add("${age.months}个月")
            if (age.days > 0) add("${age.days}天")
            if (isEmpty()) add("出生${days}天")
        }.joinToString("")
    }
}

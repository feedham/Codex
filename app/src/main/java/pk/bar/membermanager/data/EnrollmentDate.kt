package pk.bar.membermanager.data

import java.time.LocalDate

const val UNKNOWN_ENROLLMENT = "Missing / invalid D.O.E"
val ENROLLMENT_MONTHS = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

fun enrollmentParts(value: String?): List<String>? {
    val text = value?.trim() ?: return null
    val us = Regex("^(\\d{1,2})/(\\d{1,2})/(\\d{4})$").matchEntire(text)
    val iso = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$").matchEntire(text)
    if (us == null && iso == null) return null
    return runCatching {
        val year = (us?.groupValues?.get(3) ?: iso!!.groupValues[1]).toInt()
        val month = (us?.groupValues?.get(1) ?: iso!!.groupValues[2]).toInt()
        val day = (us?.groupValues?.get(2) ?: iso!!.groupValues[3]).toInt()
        require(year > 0)
        val date = LocalDate.of(year, month, day)
        listOf(date.year.toString().padStart(4, '0'), date.monthValue.toString().padStart(2, '0'), date.dayOfMonth.toString().padStart(2, '0'))
    }.getOrNull()
}

fun enrollmentRows(members: List<MemberEntity>, path: List<String>): List<MemberEntity> = members.filter { member ->
    val parts = enrollmentParts(member.enrollmentDate)
    !member.removed && if (path.firstOrNull() == UNKNOWN_ENROLLMENT) parts == null
    else path.withIndex().all { (index, part) -> parts?.getOrNull(index) == part }
}

fun enrollmentGroups(members: List<MemberEntity>, path: List<String>): List<Pair<String, Int>> =
    enrollmentRows(members, path).groupingBy { enrollmentParts(it.enrollmentDate)?.getOrNull(path.size) ?: UNKNOWN_ENROLLMENT }
        .eachCount().toList().sortedWith(compareBy<Pair<String, Int>> { it.first == UNKNOWN_ENROLLMENT }.thenByDescending { it.first })

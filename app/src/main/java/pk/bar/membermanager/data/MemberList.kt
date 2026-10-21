package pk.bar.membermanager.data

import java.util.Locale

val MEMBER_SORT_OPTIONS = listOf("Name (A–Z)", "D.O.E (oldest first)", "D.O.E (newest first)")

fun seniorMemberRows(members:List<MemberEntity>,limit:Int=50):List<MemberEntity> =
    memberListRows(members.filter{enrollmentParts(it.enrollmentDate)!=null},"",MEMBER_SORT_OPTIONS[1]).take(limit)

fun memberListRows(members: List<MemberEntity>, search: String, sort: String): List<MemberEntity> {
    val query = search.trim().lowercase(Locale.ROOT)
    val digits = query.filter(Char::isDigit)
    val dateKeys=HashMap<String,String?>()
    val phoneQuery=Regex("^[+\\d\\s()-]+$").matches(query) && digits.isNotEmpty()
    return members.filter { member ->
        if(member.removed)return@filter false
        val parts = if(query.isNotEmpty() || sort!=MEMBER_SORT_OPTIONS[0])enrollmentParts(member.enrollmentDate)else null
        dateKeys[member.id]=parts?.joinToString("-")
        val dates = listOfNotNull(parts?.joinToString("-"), parts?.let { "${it[2].toInt()}/${it[1].toInt()}/${it[0]}" }, parts?.let { "${it[2]}/${it[1]}/${it[0]}" })
        !member.removed && (query.isEmpty() || member.fullName().lowercase(Locale.ROOT).contains(query) || dates.any { it.lowercase(Locale.ROOT).contains(query) } ||
            (phoneQuery && member.cellNumber.orEmpty().filter(Char::isDigit).contains(digits)))
    }.sortedWith(Comparator { a, b ->
        val da = dateKeys[a.id]
        val db = dateKeys[b.id]
        val dateOrder = if (sort == MEMBER_SORT_OPTIONS[0]) 0 else when {
            da == null && db != null -> 1
            da != null && db == null -> -1
            da != null && db != null -> da.compareTo(db) * if (sort == MEMBER_SORT_OPTIONS[2]) -1 else 1
            else -> 0
        }
        if (dateOrder != 0) dateOrder else {
            val nameOrder = a.fullName().compareTo(b.fullName(), ignoreCase = true)
            if (nameOrder != 0) nameOrder else a.id.compareTo(b.id)
        }
    })
}

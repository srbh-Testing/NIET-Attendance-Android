package com.example.nietattendance

fun List<AttendanceSubject>.toDaySnapshot(): DaySnapshot {
    val subjects = mutableMapOf<String, SubjectSnapshot>()
    var totalPresent = 0
    var totalCount = 0

    for (s in this) {
        val code = s.subjectCode ?: continue
        val present = s.attendedLecture?.toIntOrNull() ?: 0
        val absent = s.absentLecture?.toIntOrNull() ?: 0
        val total = s.totalWithOutMakeupLectureCount?.toIntOrNull() ?: 0
        val percentage = s.percentageOfLec?.toDoubleOrNull() ?: 0.0

        subjects[code] = SubjectSnapshot(
            name = s.subjectName ?: code,
            present = present,
            absent = absent,
            total = total,
            percentage = percentage
        )
        totalPresent += present
        totalCount += total
    }

    val overallPct = if (totalCount > 0) (totalPresent.toDouble() / totalCount) * 100 else 0.0
    return DaySnapshot(
        subjects = subjects,
        overallPresent = totalPresent,
        overallTotal = totalCount,
        overallPercentage = Math.round(overallPct * 100) / 100.0
    )
}

/**
 * Actual attendance difference (classes attended), NOT a percentage-point change.
 * delta = current.present - previous.present
 *   > 0  -> attended that many more classes since the comparison day (green)
 *   < 0  -> present count went down since then, e.g. a faculty correction (red)
 *   null -> no prior data to compare against yet
 */
fun attendanceDelta(previous: DaySnapshot?, code: String, current: SubjectSnapshot): Int? {
    val prevSubject = previous?.subjects?.get(code) ?: return null
    return current.present - prevSubject.present
}

fun overallAttendanceDelta(previous: DaySnapshot?, current: DaySnapshot): Int? {
    if (previous == null) return null
    return current.overallPresent - previous.overallPresent
}

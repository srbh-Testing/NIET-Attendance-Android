package com.example.nietattendance

import kotlin.math.ceil
import kotlin.math.max

data class PlannerResult(
    val remainingLectures: Int,
    val projectedIfNoMoreMiss: Double, // final % if you attend every remaining class
    val maxCanMiss: Int,               // how many more you can afford to miss and stay >= threshold
    val achievable75: Boolean          // false if even perfect attendance from here can't reach threshold
)

/**
 * present     = classes attended so far (from live API data)
 * heldSoFar   = classes held so far (from live API data)
 * semesterTotal = manually entered: total classes planned for the whole semester
 */
fun computePlanner(present: Int, heldSoFar: Int, semesterTotal: Int, threshold: Double = 75.0): PlannerResult? {
    if (semesterTotal <= 0 || semesterTotal < heldSoFar) return null

    val remaining = semesterTotal - heldSoFar
    val projectedIfNoMoreMiss = ((present + remaining).toDouble() / semesterTotal) * 100

    if (projectedIfNoMoreMiss < threshold) {
        return PlannerResult(
            remainingLectures = remaining,
            projectedIfNoMoreMiss = Math.round(projectedIfNoMoreMiss * 100) / 100.0,
            maxCanMiss = -1,
            achievable75 = false
        )
    }

    val minAttendNeeded = max(0, ceil((threshold / 100.0) * semesterTotal - present).toInt())
    val maxCanMiss = (remaining - minAttendNeeded).coerceAtLeast(0)

    return PlannerResult(
        remainingLectures = remaining,
        projectedIfNoMoreMiss = Math.round(projectedIfNoMoreMiss * 100) / 100.0,
        maxCanMiss = maxCanMiss,
        achievable75 = true
    )
}

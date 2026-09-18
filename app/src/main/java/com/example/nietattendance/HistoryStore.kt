package com.example.nietattendance

import android.content.Context
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class SubjectSnapshot(
    val name: String,
    val present: Int,
    val absent: Int,
    val total: Int,
    val percentage: Double
)

data class DaySnapshot(
    val subjects: Map<String, SubjectSnapshot>, // keyed by subjectCode
    val overallPresent: Int,
    val overallTotal: Int,
    val overallPercentage: Double
)

/**
 * Day boundary is 9 AM, not midnight — a check at 8:59 AM still belongs to the
 * previous day's period. The first check after crossing 9 AM into a new period
 * becomes the new fixed baseline; every check within the same period compares
 * against that same baseline (so the delta grows through the day rather than
 * flipping to 0 between back-to-back checks).
 */
class HistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("attendance_history", Context.MODE_PRIVATE)
    private val gson = Gson()

    private fun computeDayKey(): String {
        val cal = Calendar.getInstance()
        if (cal.get(Calendar.HOUR_OF_DAY) < 9) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }

    /**
     * Returns the snapshot to compare the current one against, and updates
     * the stored baseline if a new day-period has started. Returns null only
     * when there is no baseline at all yet (very first check ever).
     */
    fun recordAndGetPrevious(snapshot: DaySnapshot): DaySnapshot? {
        val currentDayKey = computeDayKey()
        val storedDayKey = prefs.getString("baseline_day_key", null)
        val storedJson = prefs.getString("baseline_snapshot", null)
        val storedSnapshot = storedJson?.let {
            try {
                gson.fromJson(it, DaySnapshot::class.java)
            } catch (e: Exception) {
                null
            }
        }

        return if (storedDayKey == currentDayKey && storedSnapshot != null) {
            // Same day-period — compare against the fixed baseline, don't touch it.
            storedSnapshot
        } else {
            // New day-period (or first run ever). Whatever was stored becomes the
            // comparison point for this first check, then gets replaced as the new
            // baseline for the rest of today's period.
            val previous = storedSnapshot
            prefs.edit()
                .putString("baseline_day_key", currentDayKey)
                .putString("baseline_snapshot", gson.toJson(snapshot))
                .apply()
            previous
        }
    }
}

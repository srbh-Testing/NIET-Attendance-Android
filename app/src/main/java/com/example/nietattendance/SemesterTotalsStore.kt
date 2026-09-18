package com.example.nietattendance

import android.content.Context

class SemesterTotalsStore(context: Context) {
    private val prefs = context.getSharedPreferences("semester_totals", Context.MODE_PRIVATE)

    fun getOverallTotal(): Int? {
        val v = prefs.getInt("overall_total", -1)
        return if (v == -1) null else v
    }

    fun setOverallTotal(total: Int) {
        prefs.edit().putInt("overall_total", total).apply()
    }

    fun getTarget(): Double = prefs.getFloat("target_percent", 75f).toDouble()

    fun setTarget(target: Double) {
        prefs.edit().putFloat("target_percent", target.toFloat()).apply()
    }
}

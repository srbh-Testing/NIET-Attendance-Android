package com.example.nietattendance

import android.content.Context

class SemesterTotalsStore(context: Context) {
    private val prefs = context.getSharedPreferences("semester_totals", Context.MODE_PRIVATE)

    fun get(subjectCode: String): Int? {
        val v = prefs.getInt(subjectCode, -1)
        return if (v == -1) null else v
    }

    fun set(subjectCode: String, total: Int) {
        prefs.edit().putInt(subjectCode, total).apply()
    }
}

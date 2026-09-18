package com.example.nietattendance

data class ScheduleEntry(
    val subShortName: String? = null,
    val startTimeHM: String? = null,
    val endTimeHM: String? = null,
    val startTimeHHMMA: String? = null,
    val endTimeHHMMA: String? = null
)

data class TodayScheduleWrapper(
    val timetable: List<ScheduleEntry>? = null
)

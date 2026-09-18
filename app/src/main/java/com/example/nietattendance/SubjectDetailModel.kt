package com.example.nietattendance

import com.google.gson.annotations.SerializedName

data class SubjectAttendanceRecord(
    @SerializedName("Date") val date: String? = null,
    val presenty: String? = null,
    val stime: String? = null,
    val etime: String? = null,
    val sessionNo: String? = null,
    val noOfConsicativeLectur: String? = null,
    val isMackupClass: String? = null
)

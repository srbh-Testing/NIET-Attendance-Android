package com.example.nietattendance

data class AttendanceSubject(
    val encoSubjectwiseStudentId: String? = null,
    val attendedLecture: String?,
    val totalWithOutMakeupLectureCount: String?,
    val subjectCode: String?,
    val subjectName: String?,
    val absentLecture: String?,
    val percentageOfLec: String?
)

package com.example.nietattendance

import android.content.Context

data class AttendanceParams(
    val batchsemestercapacity: String,
    val subejctwiseBatchIds: String,
    val subjectwisestudentids: String
)

class AttendanceParamsStore(context: Context) {
    private val prefs = context.getSharedPreferences("attendance_params", Context.MODE_PRIVATE)

    // Prefilled with the values already captured for this account — editable via Settings
    // if you ever need to switch accounts or a new semester changes these IDs.
    private val defaultCapacity = "1234"
    private val defaultBatchIds = "13787,13791,13795,13799,13803,13807,13811,13815,16897,16945,16961,16965"
    private val defaultStudentIds = "923539,1043607,1043608,1043609,1043610,1043611,1043612,1043613,1047485,1061018,1065738,1065999"

    fun load(): AttendanceParams = AttendanceParams(
        batchsemestercapacity = prefs.getString("capacity", defaultCapacity) ?: defaultCapacity,
        subejctwiseBatchIds = prefs.getString("batch_ids", defaultBatchIds) ?: defaultBatchIds,
        subjectwisestudentids = prefs.getString("student_ids", defaultStudentIds) ?: defaultStudentIds
    )

    fun save(params: AttendanceParams) {
        prefs.edit()
            .putString("capacity", params.batchsemestercapacity)
            .putString("batch_ids", params.subejctwiseBatchIds)
            .putString("student_ids", params.subjectwisestudentids)
            .apply()
    }
}

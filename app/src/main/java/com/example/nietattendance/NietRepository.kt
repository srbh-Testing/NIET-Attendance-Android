package com.example.nietattendance

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import java.io.IOException
import java.util.concurrent.TimeUnit

class NietRepository(context: Context) {

    private val cookieJar = PersistentCookieJar(context)

    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val userAgent = "Mozilla/5.0 (Linux; arm_64; Android 11; RMX3085) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.7871.121 YaBrowser/26.8.2.121.00 Mobile Safari/537.36"
    private val baseUrl = "https://nietcloud.niet.co.in"

    suspend fun login(user: String, pass: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val req1 = Request.Builder()
                .url("$baseUrl/login.htm")
                .addHeader("User-Agent", userAgent)
                .build()
            client.newCall(req1).execute().close()

            val formBody = FormBody.Builder()
                .add("j_username", user)
                .add("j_password", pass)
                .build()

            val req2 = Request.Builder()
                .url("$baseUrl/j_spring_security_check")
                .post(formBody)
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .addHeader("User-Agent", userAgent)
                .addHeader("Referer", "$baseUrl/login.htm")
                .addHeader("Origin", baseUrl)
                .build()

            val response2 = client.newCall(req2).execute()
            val finalUrl = response2.request.url.toString()
            response2.close()

            if (!finalUrl.contains("home.htm")) {
                throw SecurityException("Login failed - check credentials")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchAttendance(params: AttendanceParams): Result<List<AttendanceSubject>> = withContext(Dispatchers.IO) {
        try {
            val url = HttpUrl.Builder()
                .scheme("https")
                .host("nietcloud.niet.co.in")
                .addPathSegment("stu_getStudentBatchCourseAttendanceList.json")
                .addQueryParameter("batchsemestercapacity", params.batchsemestercapacity)
                .addQueryParameter("subejctwiseBatchIds", params.subejctwiseBatchIds)
                .addQueryParameter("subjectwisestudentids", params.subjectwisestudentids)
                .build()

            val request = Request.Builder()
                .url(url)
                .addHeader("X-Requested-With", "XMLHttpRequest")
                .addHeader("User-Agent", userAgent)
                .addHeader("Accept", "application/json, text/javascript, */*; q=0.01")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: throw IOException("Empty response body")
            val bodyStr = body.trim()

            if (bodyStr.startsWith("<") || bodyStr.contains("login.htm")) {
                throw SecurityException("Session expired")
            }

            try {
                val listType = object : TypeToken<List<AttendanceSubject>>() {}.type
                val list: List<AttendanceSubject> = gson.fromJson(bodyStr, listType)
                Result.success(list)
            } catch (e: Exception) {
                throw SecurityException("Format error/Session expired. Body: ${bodyStr.take(100)}")
            }

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchSubjectDetail(encoSubjectwiseStudentId: String): Result<List<SubjectAttendanceRecord>> = withContext(Dispatchers.IO) {
        try {
            val url = HttpUrl.Builder()
                .scheme("https")
                .host("nietcloud.niet.co.in")
                .addPathSegment("stu_getSubjectWiseStudentAttendance.json")
                .addQueryParameter("encoSubjectwiseStudentId", encoSubjectwiseStudentId)
                .build()

            val request = Request.Builder()
                .url(url)
                .addHeader("X-Requested-With", "XMLHttpRequest")
                .addHeader("User-Agent", userAgent)
                .addHeader("Accept", "application/json, text/javascript, */*; q=0.01")
                .addHeader("Referer", "$baseUrl/studentCourses.htm?shwA=%2700A%27")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: throw IOException("Empty response body")
            val bodyStr = body.trim()

            if (bodyStr.isEmpty()) {
                throw SecurityException("Empty response - ID may be stale, refetch attendance first")
            }
            if (bodyStr.startsWith("<") || bodyStr.contains("login.htm")) {
                throw SecurityException("Session expired")
            }

            val listType = object : TypeToken<List<SubjectAttendanceRecord>>() {}.type
            val list: List<SubjectAttendanceRecord> = gson.fromJson(bodyStr, listType)
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchTodayScheduleSubjectCodes(): Result<Set<String>> = withContext(Dispatchers.IO) {
        try {
            val dateStr = java.text.SimpleDateFormat("MMM d,yyyy", java.util.Locale.US).format(java.util.Date())
            val url = HttpUrl.Builder()
                .scheme("https")
                .host("nietcloud.niet.co.in")
                .addPathSegment("stu_getTodaysScheduleForStudentLoggedIn.json")
                .addQueryParameter("date", dateStr)
                .build()

            val request = Request.Builder()
                .url(url)
                .addHeader("X-Requested-With", "XMLHttpRequest")
                .addHeader("User-Agent", userAgent)
                .addHeader("Accept", "application/json, text/javascript, */*; q=0.01")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: throw IOException("Empty response body")
            val bodyStr = body.trim()

            if (bodyStr.startsWith("<") || bodyStr.contains("login.htm")) {
                throw SecurityException("Session expired")
            }

            val listType = object : TypeToken<List<TodayScheduleWrapper>>() {}.type
            val wrappers: List<TodayScheduleWrapper> = gson.fromJson(bodyStr, listType)
            val codes = wrappers.flatMap { it.timetable ?: emptyList() }
                .mapNotNull { it.subShortName }
                .toSet()
            Result.success(codes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchTodaySchedule(): Result<Map<String, List<ScheduleEntry>>> = withContext(Dispatchers.IO) {
        try {
            val dateStr = java.text.SimpleDateFormat("MMM d,yyyy", java.util.Locale.US).format(java.util.Date())
            val url = HttpUrl.Builder()
                .scheme("https")
                .host("nietcloud.niet.co.in")
                .addPathSegment("stu_getTodaysScheduleForStudentLoggedIn.json")
                .addQueryParameter("date", dateStr)
                .build()

            val request = Request.Builder()
                .url(url)
                .addHeader("X-Requested-With", "XMLHttpRequest")
                .addHeader("User-Agent", userAgent)
                .addHeader("Accept", "application/json, text/javascript, */*; q=0.01")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: throw IOException("Empty response body")
            val bodyStr = body.trim()

            if (bodyStr.startsWith("<") || bodyStr.contains("login.htm")) {
                throw SecurityException("Session expired")
            }

            val listType = object : TypeToken<List<TodayScheduleWrapper>>() {}.type
            val wrappers: List<TodayScheduleWrapper> = gson.fromJson(bodyStr, listType)
            val allEntries = wrappers.flatMap { it.timetable ?: emptyList() }
            val grouped = allEntries
                .filter { it.subShortName != null }
                .groupBy { it.subShortName!! }
            Result.success(grouped)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun logout() {
        cookieJar.clear()
    }
}

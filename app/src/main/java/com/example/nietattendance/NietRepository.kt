package com.example.nietattendance

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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

    /**
     * Shared GET helper for every authenticated JSON endpoint: builds the request,
     * checks for the common failure signatures (redirected-to-login HTML, empty
     * body), and returns the raw trimmed body on success. Every fetch* method below
     * just supplies its path/params and parses this body — avoids repeating the
     * same session-expiry/empty-body checks in each method separately.
     */
    private suspend fun authenticatedGet(
        pathSegment: String,
        queryParams: Map<String, String> = emptyMap(),
        referer: String? = null,
        allowEmptyBody: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val urlBuilder = HttpUrl.Builder()
                .scheme("https")
                .host("nietcloud.niet.co.in")
                .addPathSegment(pathSegment)
            for ((k, v) in queryParams) {
                urlBuilder.addQueryParameter(k, v)
            }

            val requestBuilder = Request.Builder()
                .url(urlBuilder.build())
                .addHeader("X-Requested-With", "XMLHttpRequest")
                .addHeader("User-Agent", userAgent)
                .addHeader("Accept", "application/json, text/javascript, */*; q=0.01")
            if (referer != null) {
                requestBuilder.addHeader("Referer", referer)
            }

            val response = client.newCall(requestBuilder.build()).execute()
            val body = response.body?.string() ?: throw IOException("Empty response body")
            val bodyStr = body.trim()

            if (bodyStr.isEmpty() && !allowEmptyBody) {
                throw SecurityException("Empty response - session/ID may be stale")
            }
            if (bodyStr.startsWith("<") || bodyStr.contains("login.htm")) {
                throw SecurityException("Session expired")
            }

            Result.success(bodyStr)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchAttendance(params: AttendanceParams): Result<List<AttendanceSubject>> {
        val body = authenticatedGet(
            "stu_getStudentBatchCourseAttendanceList.json",
            mapOf(
                "batchsemestercapacity" to params.batchsemestercapacity,
                "subejctwiseBatchIds" to params.subejctwiseBatchIds,
                "subjectwisestudentids" to params.subjectwisestudentids
            )
        ).getOrElse { return Result.failure(it) }

        return try {
            val listType = object : TypeToken<List<AttendanceSubject>>() {}.type
            Result.success(gson.fromJson<List<AttendanceSubject>>(body, listType))
        } catch (e: Exception) {
            Result.failure(SecurityException("Format error/Session expired. Body: ${body.take(100)}"))
        }
    }

    suspend fun fetchSubjectDetail(encoSubjectwiseStudentId: String): Result<List<SubjectAttendanceRecord>> {
        val body = authenticatedGet(
            "stu_getSubjectWiseStudentAttendance.json",
            mapOf("encoSubjectwiseStudentId" to encoSubjectwiseStudentId),
            referer = "$baseUrl/studentCourses.htm?shwA=%2700A%27"
        ).getOrElse { return Result.failure(it) }

        return try {
            val listType = object : TypeToken<List<SubjectAttendanceRecord>>() {}.type
            Result.success(gson.fromJson<List<SubjectAttendanceRecord>>(body, listType))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchTodaySchedule(): Result<Map<String, List<ScheduleEntry>>> {
        val dateStr = SimpleDateFormat("MMM d,yyyy", Locale.US).format(Date())
        val body = authenticatedGet(
            "stu_getTodaysScheduleForStudentLoggedIn.json",
            mapOf("date" to dateStr)
        ).getOrElse { return Result.failure(it) }

        return try {
            val listType = object : TypeToken<List<TodayScheduleWrapper>>() {}.type
            val wrappers: List<TodayScheduleWrapper> = gson.fromJson(body, listType)
            val grouped = wrappers.flatMap { it.timetable ?: emptyList() }
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

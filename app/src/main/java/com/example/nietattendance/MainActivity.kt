package com.example.nietattendance

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

enum class TodayClassStatus { RUNNING, NEXT, UPCOMING, DONE }

private data class ScheduleSlot(val code: String, val start: Date, val end: Date)

private fun parseTimeToday(timeStr: String?): Date? {
    if (timeStr.isNullOrBlank()) return null
    val cleaned = timeStr.trim().uppercase(Locale.US)
        .replace(Regex("\\s+"), " ")
        .replace(Regex("(?i)(\\d)(A\\.?M\\.?|P\\.?M\\.?)"), "$1 $2")
        .replace(".", "")

    val formats = listOf(
        "hh:mm a",
        "h:mm a",
        "hh:mma",
        "h:mma",
        "HH:mm"
    )

    for (format in formats) {
        try {
            val parser = SimpleDateFormat(format, Locale.US)
            parser.isLenient = true
            val parsed = parser.parse(cleaned)
            if (parsed != null) {
                val timeCal = Calendar.getInstance().apply { time = parsed }
                val cal = Calendar.getInstance()
                cal.set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY))
                cal.set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE))
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                return cal.time
            }
        } catch (e: Exception) {
            // Try next format
        }
    }
    return null
}

private fun computeTodayStatuses(todayScheduleMap: Map<String, List<ScheduleEntry>>): Map<String, TodayClassStatus> {
    val now = Date()
    val slots = mutableListOf<ScheduleSlot>()
    for ((code, entries) in todayScheduleMap) {
        for (e in entries) {
            val startStr = e.startTimeHM?.takeIf { it.isNotBlank() } ?: e.startTimeHHMMA
            val endStr = e.endTimeHM?.takeIf { it.isNotBlank() } ?: e.endTimeHHMMA
            
            val start = parseTimeToday(startStr) ?: continue
            val end = parseTimeToday(endStr) ?: Date(start.time + 50L * 60 * 1000)
            
            val actualEnd = if (end < start) Date(start.time + 50L * 60 * 1000) else end
            
            slots.add(ScheduleSlot(code, start, actualEnd))
        }
    }
    
    val nextSlotTime = slots.filter { it.start > now }.minByOrNull { it.start }?.start

    val result = mutableMapOf<String, TodayClassStatus>()
    for (code in todayScheduleMap.keys) {
        val codeSlots = slots.filter { it.code == code }
        result[code] = when {
            codeSlots.any { it.start <= now && now <= it.end } -> TodayClassStatus.RUNNING
            nextSlotTime != null && codeSlots.any { it.start == nextSlotTime } -> TodayClassStatus.NEXT
            codeSlots.isNotEmpty() && codeSlots.all { it.end < now } -> TodayClassStatus.DONE
            else -> TodayClassStatus.UPCOMING
        }
    }
    return result
}

private data class PeriodSlot(val hour: Int, val minute: Int, val label: String)

private val periodSlots = listOf(
    PeriodSlot(9, 10, "1"),
    PeriodSlot(10, 0, "2"),
    PeriodSlot(10, 50, "3"),
    PeriodSlot(11, 40, "4"),
    PeriodSlot(13, 30, "5"),
    PeriodSlot(14, 30, "6"),
    PeriodSlot(15, 20, "7"),
    PeriodSlot(16, 10, "8")
)

private fun periodNumberForStime(stime: String?): String {
    val parsedDate = parseTimeToday(stime) ?: return "-"
    val cal = Calendar.getInstance().apply { time = parsedDate }
    val hour = cal.get(Calendar.HOUR_OF_DAY)
    val minute = cal.get(Calendar.MINUTE)
    return periodSlots.firstOrNull { it.hour == hour && it.minute == minute }?.label ?: "-"
}

private fun todayPeriodLabels(code: String, todayScheduleMap: Map<String, List<ScheduleEntry>>): String {
    val entries = todayScheduleMap[code] ?: return ""
    val labels = entries
        .mapNotNull { 
            val stime = it.startTimeHM?.takeIf { s -> s.isNotBlank() } ?: it.startTimeHHMMA
            periodNumberForStime(stime).takeIf { p -> p != "-" } 
        }
        .distinct()
    return labels.joinToString(", ")
}

private fun lecNumberFor(record: SubjectAttendanceRecord): String {
    val fromServer = record.sessionNo?.trim()
    if (!fromServer.isNullOrBlank()) return fromServer
    return periodNumberForStime(record.stime)
}

private const val PC_METHOD_TEXT = "PC method:\n" +
    "1. Open nietcloud.niet.co.in and log in.\n" +
    "2. Press F12 to open DevTools, go to the Network tab, tick 'Preserve log'.\n" +
    "3. Click the Attendance tab inside the student portal.\n" +
    "4. Find the request named stu_getStudentBatchCourseAttendanceList.json in the list.\n" +
    "5. Click it, open Headers, scroll to 'Query String Parameters'.\n" +
    "6. Copy the values of batchsemestercapacity, subejctwiseBatchIds, and subjectwisestudentids into the fields below."

private const val MOBILE_METHOD_TEXT = "Mobile method (no PC needed):\n" +
    "1. In a Chromium browser that supports extensions (e.g. Yandex Browser, Kiwi Browser), install a network-capture extension such as 'Grab cURL' from the Chrome Web Store.\n" +
    "2. Turn on 'Desktop site' for nietcloud.niet.co.in.\n" +
    "3. Start the extension's capture, then log in and open the Attendance tab.\n" +
    "4. In the extension's captured list, find stu_getStudentBatchCourseAttendanceList.json and copy it as cURL.\n" +
    "5. In that copied text, find batchsemestercapacity=, subejctwiseBatchIds=, and subjectwisestudentids= in the URL and copy each value after the equals sign."

class MainActivity : ComponentActivity() {

    private lateinit var secureStorage: SecureStorage
    private lateinit var paramsStore: AttendanceParamsStore
    private lateinit var historyStore: HistoryStore
    private lateinit var repository: NietRepository
    private lateinit var semesterTotalsStore: SemesterTotalsStore
override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        secureStorage = SecureStorage(this)
        paramsStore = AttendanceParamsStore(this)
        historyStore = HistoryStore(this)
        repository = NietRepository(this)
        semesterTotalsStore = SemesterTotalsStore(this)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppContent()
                }
            }
        }
    }

    @Composable
    fun AppContent() {
        var isLoggedIn by remember { mutableStateOf(secureStorage.getUsername() != null) }

        if (isLoggedIn) {
            MainScreen(onLogout = {
                secureStorage.clear()
                repository.logout()
                isLoggedIn = false
            })
        } else {
            LoginScreen(onLoginSuccess = { user, pass ->
                secureStorage.saveCredentials(user, pass)
                isLoggedIn = true
            })
        }
    }

    @Composable
    fun LoginScreen(onLoginSuccess: (String, String) -> Unit) {
        val coroutineScope = rememberCoroutineScope()
        var username by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(false) }
        var errorMsg by remember { mutableStateOf<String?>(null) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("NIET Attendance", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("ERP Username") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("ERP Password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
            Spacer(modifier = Modifier.height(24.dp))

            if (isLoading) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = {
                        isLoading = true
                        errorMsg = null
                        coroutineScope.launch {
                            val result = repository.login(username.trim(), password)
                            if (result.isSuccess) {
                                onLoginSuccess(username.trim(), password)
                            } else {
                                errorMsg = "Login failed: ${result.exceptionOrNull()?.message}"
                            }
                            isLoading = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Login")
                }
            }

            if (errorMsg != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(errorMsg!!, color = MaterialTheme.colorScheme.error)
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
    @Composable
    fun MainScreen(onLogout: () -> Unit) {
        val coroutineScope = rememberCoroutineScope()
        var attendanceData by remember { mutableStateOf<List<AttendanceSubject>?>(null) }
        var previousSnapshot by remember { mutableStateOf<DaySnapshot?>(null) }
        var currentSnapshot by remember { mutableStateOf<DaySnapshot?>(null) }
        var isLoading by remember { mutableStateOf(false) }
        var errorMsg by remember { mutableStateOf<String?>(null) }
        var lastUpdated by remember { mutableStateOf("Never") }
        var showLogoutDialog by remember { mutableStateOf(false) }
        var showSettings by remember { mutableStateOf(false) }
        var selectedSubject by remember { mutableStateOf<AttendanceSubject?>(null) }
        var showPlanner by remember { mutableStateOf(false) }
        var todayScheduleMap by remember { mutableStateOf<Map<String, List<ScheduleEntry>>>(emptyMap()) }

        suspend fun applyAttendanceResult(subjects: List<AttendanceSubject>) {
            attendanceData = subjects
            val snapshot = subjects.toDaySnapshot()
            val prev = historyStore.recordAndGetPrevious(snapshot)
            currentSnapshot = snapshot
            previousSnapshot = prev
            lastUpdated = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
            val scheduleResult = repository.fetchTodaySchedule()
            if (scheduleResult.isSuccess) {
                todayScheduleMap = scheduleResult.getOrNull() ?: emptyMap()
            }
        }

        fun fetchWrapper() {
            isLoading = true
            errorMsg = null
            val params = paramsStore.load()
            coroutineScope.launch {
                val result = repository.fetchAttendance(params)
                if (result.isSuccess) {
                    result.getOrNull()?.let { applyAttendanceResult(it) }
                } else {
                    val ex = result.exceptionOrNull()
                    if (ex is SecurityException) {
                        val u = secureStorage.getUsername()
                        val p = secureStorage.getPassword()
                        if (u != null && p != null) {
                            val loginRes = repository.login(u, p)
                            if (loginRes.isSuccess) {
                                val retry = repository.fetchAttendance(params)
                                if (retry.isSuccess) {
                                    retry.getOrNull()?.let { applyAttendanceResult(it) }
                                } else {
                                    errorMsg = "Auto-login succeeded, but fetch failed: ${retry.exceptionOrNull()?.message}"
                                }
                            } else {
                                errorMsg = "Auto-login failed: ${loginRes.exceptionOrNull()?.message}"
                                onLogout()
                            }
                        } else {
                            onLogout()
                        }
                    } else {
                        errorMsg = "Network Error: ${ex?.message}"
                    }
                }
                isLoading = false
            }
        }

        if (showSettings) {
            BackHandler { showSettings = false }
            SettingsScreen(
                onBack = { showSettings = false },
                onSaved = {
                    showSettings = false
                    fetchWrapper()
                }
            )
            return
        }

        if (showPlanner) {
            BackHandler { showPlanner = false }
            PlannerScreen(
                data = attendanceData ?: emptyList(),
                onBack = { showPlanner = false }
            )
            return
        }

        selectedSubject?.let { subject ->
            BackHandler { selectedSubject = null }
            SubjectDetailScreen(
                subject = subject,
                todaySchedule = todayScheduleMap[subject.subjectCode] ?: emptyList(),
                onBack = { selectedSubject = null }
            )
            return
        }

        LaunchedEffect(Unit) {
            fetchWrapper()
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("NIET Attendance") },
                    actions = {
                        IconButton(onClick = { showPlanner = true }) {
                            Icon(
                                Icons.Default.DateRange,
                                contentDescription = "Planner"
                            )
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                        TextButton(onClick = { showLogoutDialog = true }) {
                            Text("Logout", color = MaterialTheme.colorScheme.error)
                        }
                    }
                )
            },
        ) { padding ->
            val pullRefreshState = rememberPullRefreshState(refreshing = isLoading, onRefresh = { fetchWrapper() })
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .pullRefresh(pullRefreshState)
            ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                if (isLoading && attendanceData == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Fetching attendance...")
                        }
                    }
                } else {
                    if (errorMsg != null) {
                        Text(errorMsg!!, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    attendanceData?.let { data ->
                        val snapshot = currentSnapshot
                        val overallDelta = if (snapshot != null) overallAttendanceDelta(previousSnapshot, snapshot) else null

                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            shape = MaterialTheme.shapes.medium,
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "Overall Attendance",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("${snapshot?.overallPresent ?: 0} / ${snapshot?.overallTotal ?: 0}", fontSize = 14.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        DeltaBadge(overallDelta ?: 0)
                                    }
                                    Text(
                                        String.format(Locale.US, "%.2f%%", snapshot?.overallPercentage ?: 0.0),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        Text("Last Updated: $lastUpdated", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (previousSnapshot == null) {
                            Text(
                                "First check recorded — differences will show from the next check onward.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
val todayStatuses = remember(todayScheduleMap) { computeTodayStatuses(todayScheduleMap) }

                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 80.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(data) { subject ->
                                val code = subject.subjectCode ?: ""
                                val sub = snapshot?.subjects?.get(code)
                                val delta = if (sub != null) attendanceDelta(previousSnapshot, code, sub) else null

                                val status = todayStatuses[code]
                                val statusColor = when (status) {
                                    TodayClassStatus.RUNNING -> Color(0xFF42A5F5)
                                    TodayClassStatus.NEXT -> Color(0xFFEF5350)
                                    TodayClassStatus.UPCOMING -> Color(0xFFFFEB3B)
                                    TodayClassStatus.DONE -> Color(0xFF66BB6A)
                                    null -> null
                                }
                                val periodLabels = todayPeriodLabels(code, todayScheduleMap)
                                val statusLabel = when (status) {
                                    TodayClassStatus.RUNNING -> "Running"
                                    TodayClassStatus.NEXT -> "Next"
                                    TodayClassStatus.UPCOMING -> "Later"
                                    TodayClassStatus.DONE -> "Done"
                                    null -> null
                                }?.let { label ->
                                    if (periodLabels.isNotEmpty()) "$label \u2022 Lec $periodLabels" else label
                                }
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (statusColor != null) Modifier.border(
                                                BorderStroke(2.dp, statusColor),
                                                MaterialTheme.shapes.medium
                                            ) else Modifier
                                        )
                                        .clickable { selectedSubject = subject },
                                    shape = MaterialTheme.shapes.medium,
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                subject.subjectName ?: "Unknown Subject",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (statusColor != null && statusLabel != null) {
                                                Surface(
                                                    color = statusColor.copy(alpha = 0.2f),
                                                    shape = MaterialTheme.shapes.small
                                                ) {
                                                    Text(
                                                        statusLabel,
                                                        color = statusColor,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("${subject.attendedLecture ?: "0"} / ${subject.totalWithOutMakeupLectureCount ?: "0"}", fontSize = 14.sp)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                DeltaBadge(delta ?: 0)
                                            }
                                            Text("${subject.percentageOfLec ?: "0.00"}%", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            PullRefreshIndicator(
                refreshing = isLoading,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
            }
        }

        if (showLogoutDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutDialog = false },
                title = { Text("Logout") },
                text = { Text("Are you sure you want to log out?") },
                confirmButton = {
                    TextButton(onClick = {
                        showLogoutDialog = false
                        onLogout()
                    }) {
                        Text("Logout")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }

    @Composable
    fun DeltaBadge(delta: Int) {
        val (color, text) = when {
            delta > 0 -> Color(0xFF2E7D32) to "+$delta"
            delta < 0 -> Color(0xFFC62828) to "$delta"
            else -> Color.Gray to "0"
        }
        Surface(
            color = color.copy(alpha = 0.15f),
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                text,
                color = color,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun SubjectDetailScreen(subject: AttendanceSubject, todaySchedule: List<ScheduleEntry> = emptyList(), onBack: () -> Unit) {
        val coroutineScope = rememberCoroutineScope()
        var records by remember { mutableStateOf<List<SubjectAttendanceRecord>?>(null) }
        var isLoading by remember { mutableStateOf(true) }
        var errorMsg by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(subject.encoSubjectwiseStudentId) {
            val encoId = subject.encoSubjectwiseStudentId
            if (encoId == null) {
                errorMsg = "No detail ID available for this subject"
                isLoading = false
                return@LaunchedEffect
            }
            val result = repository.fetchSubjectDetail(encoId)
            if (result.isSuccess) {
                records = result.getOrNull()
            } else {
                errorMsg = "Failed to load: ${result.exceptionOrNull()?.message}"
            }
            isLoading = false
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(subject.subjectName ?: "Subject Detail") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
            ) {
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (errorMsg != null) {
                    Text(errorMsg!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 16.dp))
                } else {
                    if (todaySchedule.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Today", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFFFA726))
                                todaySchedule.forEach { entry ->
                                    Text(
                                        "${entry.startTimeHHMMA ?: entry.startTimeHM ?: "?"} - ${entry.endTimeHHMMA ?: entry.endTimeHM ?: "?"}",
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Sr", fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(0.4f))
                        Text("Lec", fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(0.6f))
                        Text("Date", fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1.3f))
                        Text("Time", fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1.6f))
                        Text("Status", fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(0.9f))
                    }
                    Divider()

                    val displayRecords = remember(records) { records?.reversed() ?: emptyList() }
                    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                        itemsIndexed(displayRecords) { index, record ->
                            val srNumber = displayRecords.size - index
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("$srNumber", fontSize = 13.sp, modifier = Modifier.weight(0.4f))
                                Text(lecNumberFor(record), fontSize = 13.sp, modifier = Modifier.weight(0.6f))
                                Text(record.date ?: "-", fontSize = 13.sp, modifier = Modifier.weight(1.3f))
                                Text("${record.stime ?: ""}-${record.etime ?: ""}", fontSize = 12.sp, modifier = Modifier.weight(1.6f))
                                val isPresent = record.presenty == "P"
                                Text(
                                    record.presenty ?: "-",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isPresent) Color(0xFF66BB6A) else Color(0xFFEF5350),
                                    modifier = Modifier.weight(0.9f)
                                )
                            }
                            Divider()
                        }
                    }
                }
            }
        }
    }
  @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun PlannerScreen(
        data: List<AttendanceSubject>,
        onBack: () -> Unit
    ) {
        val overallPresent =
            data.sumOf { it.attendedLecture?.toIntOrNull() ?: 0 }

        val overallHeldSoFar =
            data.sumOf {
                it.totalWithOutMakeupLectureCount?.toIntOrNull() ?: 0
            }

        var totalText by remember {
            mutableStateOf(
                semesterTotalsStore.getOverallTotal()?.toString() ?: ""
            )
        }

        var committedTotal by remember {
            mutableStateOf(
                semesterTotalsStore.getOverallTotal()
            )
        }

        var targetText by remember {
            mutableStateOf(
                semesterTotalsStore.getTarget().let {
                    if (it == it.toLong().toDouble()) {
                        it.toLong().toString()
                    } else {
                        it.toString()
                    }
                }
            )
        }

        val target = targetText.toDoubleOrNull() ?: 75.0

        val result = committedTotal?.let {
            computePlanner(
                overallPresent,
                overallHeldSoFar,
                it,
                target
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Semester Planner") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                )
            }
        ) { padding ->

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {

                val overallPct =
                    if (overallHeldSoFar > 0) {
                        overallPresent * 100.0 / overallHeldSoFar
                    } else {
                        0.0
                    }

                Text(
                    "So far overall: $overallPresent / $overallHeldSoFar (" +
                        String.format(
                            Locale.US,
                            "%.2f",
                            overallPct
                        ) +
                        "%)",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = totalText,
                        onValueChange = { value ->
                            totalText = value.filter { it.isDigit() }
                        },
                        label = {
                            Text("Total lectures this semester")
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            val parsed = totalText.toIntOrNull()

                            if (parsed != null && parsed > 0) {
                                semesterTotalsStore.setOverallTotal(parsed)
                                committedTotal = parsed
                            }
                        }
                    ) {
                        Text("Submit")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = targetText,
                    onValueChange = { value ->
                        val filtered =
                            value.filter {
                                it.isDigit() || it == '.'
                            }

                        targetText = filtered

                        filtered.toDoubleOrNull()?.let {
                            if (it in 0.0..100.0) {
                                semesterTotalsStore.setTarget(it)
                            }
                        }
                    },
                    label = {
                        Text("Target attendance % (default 75)")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(20.dp))

                when {
                    committedTotal == null -> {
                        Text(
                            "Enter total lectures for the semester and tap Submit to see your planner.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    committedTotal!! < overallHeldSoFar -> {
                        Text(
                            "Total must be at least $overallHeldSoFar (lectures already held).",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    result != null -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {

                                Text(
                                    "Remaining lectures: ${result.remainingLectures}",
                                    fontSize = 14.sp
                                )

                                Spacer(
                                    modifier = Modifier.height(6.dp)
                                )

                                Text(
                                    "If you don't miss any more: " +
                                        String.format(
                                            Locale.US,
                                            "%.2f",
                                            result.projectedIfNoMoreMiss
                                        ) +
                                        "%",
                                    fontSize = 14.sp
                                )

                                Spacer(
                                    modifier = Modifier.height(6.dp)
                                )

                                val targetLabel =
                                    if (target == target.toLong().toDouble()) {
                                        target.toLong().toString()
                                    } else {
                                        target.toString()
                                    }

                                if (result.achievable75) {
                                    Text(
                                        "You can still miss up to " +
                                            "${result.maxCanMiss} lecture(s) " +
                                            "and stay ≥$targetLabel%",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else {
                                    Text(
                                        "Even attending every remaining class, " +
                                            "you'll end at " +
                                            String.format(
                                                Locale.US,
                                                "%.2f",
                                                result.projectedIfNoMoreMiss
                                            ) +
                                            "% — below target",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
@OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun SettingsScreen(onBack: () -> Unit, onSaved: () -> Unit) {
        val context = LocalContext.current
        val current = remember { paramsStore.load() }
        var capacity by remember { mutableStateOf(current.batchsemestercapacity) }
        var batchIds by remember { mutableStateOf(current.subejctwiseBatchIds) }
        var studentIds by remember { mutableStateOf(current.subjectwisestudentids) }
        var showHelp by remember { mutableStateOf(false) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Attendance Settings") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                Text(
                    "These IDs identify which subjects/batches to fetch attendance for.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { showHelp = !showHelp }) {
                    Text(if (showHelp) "Hide instructions ▲" else "How do I find these values? ▼")
                }

                if (showHelp) {
                    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp), shape = MaterialTheme.shapes.medium) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("PC method", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(PC_METHOD_TEXT, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { copyToClipboard(context, "PC method", PC_METHOD_TEXT) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Copy PC method")
                            }
                        }
                    }

                    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), shape = MaterialTheme.shapes.medium) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Mobile method", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(MOBILE_METHOD_TEXT, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { copyToClipboard(context, "Mobile method", MOBILE_METHOD_TEXT) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Copy Mobile method")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = capacity,
                    onValueChange = { capacity = it },
                    label = { Text("batchsemestercapacity") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = batchIds,
                    onValueChange = { batchIds = it },
                    label = { Text("subejctwiseBatchIds") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = studentIds,
                    onValueChange = { studentIds = it },
                    label = { Text("subjectwisestudentids") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        paramsStore.save(
                            AttendanceParams(
                                batchsemestercapacity = capacity.trim(),
                                subejctwiseBatchIds = batchIds.trim(),
                                subjectwisestudentids = studentIds.trim()
                            )
                        )
                        onSaved()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save & Refresh")
                }
            }
        }
    }
}

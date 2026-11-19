package com.ultron.fitlog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.UUID

private data class Workout(
    val id: String, val activity: String, val date: LocalDate, val minutes: Int, val notes: String
)

class MainActivity : ComponentActivity() {
    private val storage by lazy { getSharedPreferences("fitlog", MODE_PRIVATE) }

    private fun load(): List<Workout> {
        val entries = JSONArray(storage.getString("workouts", "[]") ?: "[]")
        return List(entries.length()) { index ->
            val entry = entries.getJSONObject(index)
            Workout(entry.getString("id"), entry.getString("activity"),
                LocalDate.parse(entry.getString("date")), entry.getInt("minutes"),
                entry.getString("notes"))
        }
    }

    private fun save(workouts: List<Workout>): Boolean {
        val entries = JSONArray()
        workouts.forEach {
            entries.put(JSONObject().put("id", it.id).put("activity", it.activity)
                .put("date", it.date.toString()).put("minutes", it.minutes).put("notes", it.notes))
        }
        return storage.edit().putString("workouts", entries.toString()).commit()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initial = load()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFFB6F370), background = Color(0xFF101610),
                surface = Color(0xFF172017), surfaceVariant = Color(0xFF253125)
            )) {
                var workouts by remember { mutableStateOf(initial) }
                FitLog(workouts) { next ->
                    save(next).also { if (it) workouts = next }
                }
            }
        }
    }
}

@Composable
private fun FitLog(workouts: List<Workout>, onSave: (List<Workout>) -> Boolean) {
    var editorId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var error by rememberSaveable { mutableStateOf(false) }
    val today = LocalDate.now()
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val weekly = workouts.filter { it.date in monday..today }
    Surface(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.safeDrawingPadding().fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Text("FitLog", style = MaterialTheme.typography.headlineLarge) }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("This week", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            Text("${weekly.size} workouts")
                            Text("${weekly.sumOf { it.minutes.toLong() }} active min")
                        }
                    }
                }
            }
            item {
                Button(onClick = { editorId = "" }, modifier = Modifier.fillMaxWidth()) {
                    Text("Log workout")
                }
            }
            if (error) item { Text("Couldn’t save changes. Please try again.", color = MaterialTheme.colorScheme.error) }
            item { Text("Workout history", style = MaterialTheme.typography.titleLarge) }
            if (workouts.isEmpty()) item { Text("Your logged workouts will appear here.") }
            items(workouts.sortedByDescending { it.date }, key = { it.id }) { workout ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(workout.activity, style = MaterialTheme.typography.titleMedium)
                        Text("${workout.date.format(DateTimeFormatter.ofPattern("d MMM yyyy"))} · ${workout.minutes} min")
                        if (workout.notes.isNotBlank()) Text(workout.notes)
                        Row {
                            TextButton(onClick = { editorId = workout.id }) { Text("Edit") }
                            TextButton(onClick = { deleteId = workout.id }) { Text("Delete") }
                        }
                    }
                }
            }
        }
    }
    editorId?.let { id ->
        val existing = workouts.firstOrNull { it.id == id }
        WorkoutEditor(existing, { editorId = null }) { entry ->
            val next = if (existing == null) listOf(entry) + workouts else
                workouts.map { if (it.id == entry.id) entry else it }
            onSave(next)
        }
    }
    deleteId?.let { id ->
        AlertDialog(onDismissRequest = { deleteId = null }, title = { Text("Delete workout?") },
            confirmButton = {
                TextButton(onClick = {
                    error = !onSave(workouts.filterNot { it.id == id })
                    deleteId = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Cancel") } })
    }
}

@Composable
private fun WorkoutEditor(existing: Workout?, dismiss: () -> Unit, save: (Workout) -> Boolean) {
    var activity by rememberSaveable { mutableStateOf(existing?.activity.orEmpty()) }
    var date by rememberSaveable { mutableStateOf((existing?.date ?: LocalDate.now()).toString()) }
    var duration by rememberSaveable { mutableStateOf(existing?.minutes?.toString().orEmpty()) }
    var notes by rememberSaveable { mutableStateOf(existing?.notes.orEmpty()) }
    var error by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = dismiss, title = {
        Text(if (existing == null) "Log workout" else "Edit workout")
    }, text = {
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(activity, { activity = it }, label = { Text("Activity type") }, singleLine = true)
            OutlinedTextField(date, { date = it }, label = { Text("Date (YYYY-MM-DD)") }, singleLine = true)
            OutlinedTextField(duration, { duration = it }, label = { Text("Duration in minutes") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") }, maxLines = 3)
            if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = {
        TextButton(onClick = {
            val day = runCatching { LocalDate.parse(date.trim()) }.getOrNull()
            val minutes = duration.trim().toIntOrNull()
            error = when {
                activity.isBlank() -> "Enter an activity type."
                day == null || day > LocalDate.now() -> "Enter a valid date up to today."
                minutes == null || minutes !in 1..1440 -> "Enter a duration from 1 to 1,440 minutes."
                else -> {
                    val entry = Workout(existing?.id ?: UUID.randomUUID().toString(),
                        activity.trim(), day, minutes, notes.trim())
                    if (save(entry)) { dismiss(); "" } else "Couldn’t save the workout. Please try again."
                }
            }
        }) { Text("Save") }
    }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

package com.ultron.assistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit

private val Background = Color(0xFF090E1C)
private val Panel = Color(0xFF182039)
private val Cyan = Color(0xFF53E1F1)
private val Purple = Color(0xFF9A78FF)

data class ChatMessage(val role: String, val text: String, val imageBase64: String? = null)

class ChatModel : ViewModel() {
    var messages by mutableStateOf(listOf(ChatMessage("assistant", "Hello! I'm ULTRON. Ask me anything, use the microphone, or attach an image.")))
        private set
    var busy by mutableStateOf(false)
        private set
    var webEnabled by mutableStateOf(false)
    var selectedImage by mutableStateOf<String?>(null)
    var error by mutableStateOf<String?>(null)
    private val client = OkHttpClient.Builder().callTimeout(90, TimeUnit.SECONDS).build()

    fun send(text: String, onReply: (String) -> Unit) {
        if (busy || (text.isBlank() && selectedImage == null)) return
        val image = selectedImage
        selectedImage = null
        messages = messages + ChatMessage("user", text.ifBlank { "Describe this image." }, image)
        busy = true
        error = null
        viewModelScope.launch {
            try {
                val reply = withContext(Dispatchers.IO) {
                    val history = JSONArray()
                    messages.takeLast(16).forEach { m ->
                        val item = JSONObject().put("role", m.role).put("content", m.text)
                        if (m.imageBase64 != null) item.put("image", m.imageBase64)
                        history.put(item)
                    }
                    val payload = JSONObject().put("messages", history).put("web", webEnabled)
                    val request = Request.Builder().url(BuildConfig.BACKEND_URL.trimEnd('/') + "/api/chat")
                        .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()
                    client.newCall(request).execute().use { response ->
                        val raw = response.body?.string() ?: ""
                        val json = JSONObject(raw.ifBlank { "{}" })
                        if (!response.isSuccessful) throw Exception(json.optString("error", "HTTP ${response.code}"))
                        json.optString("reply").ifBlank { "No response returned." }
                    }
                }
                messages = messages + ChatMessage("assistant", reply)
                onReply(reply)
            } catch (e: Exception) {
                error = e.message ?: "Unable to connect to the server"
            } finally { busy = false }
        }
    }
    fun clear() { messages = emptyList(); error = null }
}

class MainActivity : ComponentActivity() {
    private var tts: TextToSpeech? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this) { status -> if (status == TextToSpeech.SUCCESS) tts?.language = Locale.getDefault() }
        setContent { UltronScreen(onSpeak = { text -> tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ultron") }) }
    }
    override fun onDestroy() { tts?.stop(); tts?.shutdown(); super.onDestroy() }
}

@Composable
fun UltronScreen(onSpeak: (String) -> Unit, model: ChatModel = viewModel()) {
    val context = LocalContext.current
    var draft by remember { mutableStateOf("") }
    var speakReplies by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) draft = spoken
        }
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchSpeech { voiceLauncher.launch(it) }
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val bitmap = BitmapFactory.decodeStream(input)
                    if (bitmap != null) {
                        val ratio = minOf(1f, 1024f / maxOf(bitmap.width, bitmap.height))
                        val scaled = Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt().coerceAtLeast(1), (bitmap.height * ratio).toInt().coerceAtLeast(1), true)
                        val out = ByteArrayOutputStream()
                        scaled.compress(Bitmap.CompressFormat.JPEG, 75, out)
                        model.selectedImage = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                    }
                }
            } catch (e: Exception) { model.error = "Image error: ${e.message}" }
        }
    }
    LaunchedEffect(model.messages.size) {
        if (model.messages.isNotEmpty()) listState.animateScrollToItem(model.messages.lastIndex)
    }
    MaterialTheme(colorScheme = darkColorScheme(primary = Cyan, secondary = Purple, background = Background, surface = Panel)) {
        Column(Modifier.fillMaxSize().background(Background).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, null, tint = Cyan, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("ULTRON", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Your AI companion", color = Purple, style = MaterialTheme.typography.labelMedium)
                }
                IconButton(onClick = { model.clear() }) { Icon(Icons.Default.DeleteOutline, "Clear conversation", tint = Color.LightGray) }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Web research", color = Color.White, modifier = Modifier.weight(1f))
                Switch(checked = model.webEnabled, onCheckedChange = { model.webEnabled = it })
                Spacer(Modifier.width(14.dp))
                Text("Speak", color = Color.White)
                Switch(checked = speakReplies, onCheckedChange = { speakReplies = it })
            }
            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(model.messages) { m ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.role == "user") Arrangement.End else Arrangement.Start) {
                        Column(Modifier.fillMaxWidth(.88f).background(if (m.role == "user") Color(0xFF29375C) else Panel, RoundedCornerShape(18.dp)).padding(14.dp)) {
                            Text(if (m.role == "user") "YOU" else "ULTRON", color = if (m.role == "user") Purple else Cyan, style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.height(6.dp))
                            Text(m.text, color = Color.White)
                            if (m.imageBase64 != null) Text("📎 Image attached", color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                if (model.busy) item { CircularProgressIndicator(color = Cyan, modifier = Modifier.padding(12.dp).size(24.dp)) }
            }
            if (model.error != null) Text(model.error!!, color = Color(0xFFFF8D8D), modifier = Modifier.padding(horizontal = 16.dp))
            if (model.selectedImage != null) Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Image ready to send", color = Cyan, modifier = Modifier.weight(1f))
                TextButton(onClick = { model.selectedImage = null }) { Text("Remove") }
            }
            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.Bottom) {
                IconButton(onClick = { imagePicker.launch("image/*") }) { Icon(Icons.Default.AddPhotoAlternate, "Attach image", tint = Purple) }
                IconButton(onClick = {
                    if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) launchSpeech { voiceLauncher.launch(it) }
                    else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                }) { Icon(Icons.Default.Mic, "Voice input", tint = Cyan) }
                OutlinedTextField(value = draft, onValueChange = { draft = it }, modifier = Modifier.weight(1f), placeholder = { Text("Message ULTRON...") }, maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = {
                        model.send(draft) { if (speakReplies) onSpeak(it) }; draft = ""
                    }), shape = RoundedCornerShape(22.dp))
                IconButton(enabled = !model.busy, onClick = { model.send(draft) { if (speakReplies) onSpeak(it) }; draft = "" }) {
                    Icon(Icons.Default.Send, "Send", tint = Cyan)
                }
            }
        }
    }
}

private fun launchSpeech(launch: (Intent) -> Unit) {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_PROMPT, "Talk to ULTRON")
    }
    try { launch(intent) } catch (_: Exception) { /* Device may not have a speech recognition app */ }
}

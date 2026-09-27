package com.example.khmersubtitle

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SubtitleScreen()
                }
            }
        }
    }
}

@Composable
fun SubtitleScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf("សូមជ្រើសរើសវីដេអូ") }
    var isLoading by remember { mutableStateOf(false) }
    var srtResult by remember { mutableStateOf<String?>(null) }
    var detectedLang by remember { mutableStateOf<String?>(null) }
    var srtFile by remember { mutableStateOf<File?>(null) }

    val pickVideoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        isLoading = true
        srtResult = null
        status = "កំពុងចម្លងវីដេអូ..."

        scope.launch {
            try {
                val (localFile, mimeType) = withContext(Dispatchers.IO) {
                    val name = "input_video_${System.currentTimeMillis()}.mp4"
                    val temp = File(context.cacheDir, name)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        temp.outputStream().use { output -> input.copyTo(output) }
                    }
                    Pair(temp, SubtitleUtils.guessMimeType(uri.lastPathSegment ?: "video.mp4"))
                }

                val apiKey = BuildConfig.GEMINI_API_KEY
                if (apiKey.isBlank()) {
                    status = "សូមកំណត់ GEMINI_API_KEY ក្នុង local.properties សិន"
                    isLoading = false
                    return@launch
                }
                val service = GeminiApiService(apiKey)

                status = "កំពុង Upload វីដេអូទៅ Gemini..."
                val (fileUri, fileName) = withContext(Dispatchers.IO) {
                    service.uploadVideo(localFile, mimeType)
                }

                status = "កំពុងដំណើរការវីដេអូ (Gemini កំពុងវិភាគ)..."
                withContext(Dispatchers.IO) { service.waitUntilActive(fileName) }

                status = "កំពុងស្តាប់សំឡេង ស្គាល់ភាសា និងបកប្រែជាខ្មែរ..."
                val result = withContext(Dispatchers.IO) {
                    service.generateKhmerSubtitles(fileUri, mimeType)
                }

                srtResult = result.srtContent
                detectedLang = result.detectedLanguage
                srtFile = withContext(Dispatchers.IO) {
                    SubtitleUtils.saveSrt(context, result.srtContent, "khmer_subtitle")
                }
                status = "រួចរាល់! ភាសាដើមរកឃើញ៖ ${result.detectedLanguage}"
            } catch (e: Exception) {
                status = "កំហុស៖ ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Video → ខ្មែរ Subtitle (Gemini AI)", style = MaterialTheme.typography.titleLarge)

        Button(
            onClick = { pickVideoLauncher.launch(arrayOf("video/*")) },
            enabled = !isLoading
        ) {
            Text("ជ្រើសរើសវីដេអូ")
        }

        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Text(status)

        detectedLang?.let {
            Text("ភាសាដើម: $it", style = MaterialTheme.typography.bodyMedium)
        }

        srtResult?.let { srt ->
            Divider()
            Text("Subtitle (SRT):", style = MaterialTheme.typography.titleMedium)
            Text(srt)

            Button(onClick = {
                srtFile?.let { file ->
                    val uri = FileProvider.getUriForFile(
                        context, "${context.packageName}.fileprovider", file
                    )
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "ចែករំលែក .srt"))
                }
            }) {
                Text("ចែករំលែក / រក្សាទុកជា .srt")
            }
        }
    }
}

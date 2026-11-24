package com.example.linkdl

import android.content.ContentValues
import android.os.Bundle
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.ffmpeg.FFmpeg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val Purple = Color(0xFF9575CD)
private val PurpleLight = Color(0xFFEDE7F6)
private val Bg = Color(0xFFF6F0FF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val shared = intent?.getStringExtra(android.content.Intent.EXTRA_TEXT) ?: ""
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme(
                    colorScheme = lightColorScheme(
                        primary = Purple, onPrimary = Color.White,
                        primaryContainer = PurpleLight, background = Bg, surface = Bg
                    )
                ) { Screen(shared) }
            }
        }
    }
}

@Composable
fun Screen(initialUrl: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(initialUrl) }
    var audio by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var status by remember { mutableStateOf("جاهز") }
    var busy by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                YoutubeDL.getInstance().init(ctx.applicationContext)
                FFmpeg.getInstance().init(ctx.applicationContext)
                ready = true
            } catch (e: Exception) { status = "تعذر التهيئة: ${e.message}" }
        }
    }

    Surface(Modifier.fillMaxSize(), color = Bg) {
        Column(
            Modifier.padding(24.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("منزّل الوسائط", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Purple)
            OutlinedTextField(
                value = url, onValueChange = { url = it },
                label = { Text("الصق الرابط هنا") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(!audio, { audio = false }, { Text("فيديو") })
                FilterChip(audio, { audio = true }, { Text("صوت MP3") })
            }
            Button(
                onClick = {
                    busy = true; progress = 0f; status = "جارٍ التنزيل..."
                    scope.launch {
                        status = withContext(Dispatchers.IO) { download(ctx, url.trim(), audio) { progress = it / 100f } }
                        busy = false
                    }
                },
                enabled = ready && !busy && url.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) { Text("تنزيل", fontSize = 18.sp) }
            if (busy) LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth())
            Text(status)
        }
    }
}

private fun download(ctx: android.content.Context, link: String, audio: Boolean, onProgress: (Float) -> Unit): String {
    return try {
        val tmp = File(ctx.cacheDir, "dl").apply { deleteRecursively(); mkdirs() }
        val req = YoutubeDLRequest(link).apply {
            addOption("-o", "${tmp.absolutePath}/%(title).80s.%(ext)s")
            addOption("--no-playlist")
            if (audio) { addOption("-x"); addOption("--audio-format", "mp3") }
            else { addOption("-f", "bv*+ba/b"); addOption("--merge-output-format", "mp4") }
        }
        YoutubeDL.getInstance().execute(req) { p, _, _ -> onProgress(p) }
        val files = tmp.listFiles().orEmpty().filter { it.isFile }
        if (files.isEmpty()) return "لم يتم العثور على ملف"
        files.forEach { f ->
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension) ?: "application/octet-stream"
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, f.name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
            }
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
            ctx.contentResolver.openOutputStream(uri)!!.use { out -> f.inputStream().use { it.copyTo(out) } }
            f.delete()
        }
        "تم الحفظ في مجلد التنزيلات ✅"
    } catch (e: Exception) {
        "فشل التنزيل: ${e.message?.take(120)}"
    }
}

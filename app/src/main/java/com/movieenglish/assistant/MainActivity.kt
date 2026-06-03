package com.movieenglish.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.movieenglish.assistant.floating.FloatingWindowService
import com.movieenglish.assistant.llama.PreprocessPipeline
import com.movieenglish.assistant.subtitle.SubtitleRepository
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val subtitleRepository by lazy { SubtitleRepository(this) }
    private var currentMovieId: String? = null

    private val mediaProjectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                startFloatingService(result.resultCode, result.data!!)
            } else {
                Toast.makeText(this, "需要屏幕录制权限才能捕获字幕", Toast.LENGTH_SHORT).show()
            }
        }

    private val overlayPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (Settings.canDrawOverlays(this)) {
                checkNotificationAndStart()
            }
        }

    private val subtitlePickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { importSubtitle(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MainScreen(
                    onStartOverlay = { startOverlayFlow() },
                    onImportSubtitle = { subtitlePickerLauncher.launch(arrayOf("*/*")) }
                )
            }
        }
    }

    private fun importSubtitle(uri: Uri) {
        lifecycleScope.launch {
            try {
                val movieId = uri.lastPathSegment ?: "unknown_${System.currentTimeMillis()}"
                currentMovieId = movieId
                val title = movieId

                Toast.makeText(this@MainActivity, "正在导入字幕...", Toast.LENGTH_SHORT).show()
                val parsed = subtitleRepository.importSubtitle(movieId, title, uri)
                Toast.makeText(
                    this@MainActivity,
                    "已导入 ${parsed.size} 条字幕，开始预处理...",
                    Toast.LENGTH_SHORT
                ).show()

                val pipeline = PreprocessPipeline(this@MainActivity, subtitleRepository)
                pipeline.preprocessMovie(movieId) { progress ->
                    // Progress updates will be wired with a progress dialog in full implementation
                    if (progress.current % 100 == 0 || progress.current == progress.total) {
                        Toast.makeText(
                            this@MainActivity,
                            "预处理: ${progress.current}/${progress.total}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                Toast.makeText(this@MainActivity, "预处理完成！可以启动悬浮窗了", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    "导入失败: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun startOverlayFlow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        } else {
            checkNotificationAndStart()
        }
    }

    private fun checkNotificationAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATION_PERMISSION
            )
        } else {
            requestMediaProjection()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
            requestMediaProjection()
        }
    }

    private fun requestMediaProjection() {
        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = projectionManager.createScreenCaptureIntent()
        mediaProjectionLauncher.launch(intent)
    }

    private fun startFloatingService(resultCode: Int, data: Intent) {
        val intent = Intent(this, FloatingWindowService::class.java).apply {
            putExtra("resultCode", resultCode)
            putExtra("data", data)
            currentMovieId?.let { putExtra("movieId", it) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        }
    }

    companion object {
        private const val REQUEST_NOTIFICATION_PERMISSION = 1001
    }
}

@Composable
fun MainScreen(onStartOverlay: () -> Unit, onImportSubtitle: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("电影英语助手", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onStartOverlay) {
            Text("启动悬浮窗")
        }
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(onClick = onImportSubtitle) {
            Text("导入字幕文件")
        }
    }
}

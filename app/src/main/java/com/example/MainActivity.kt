package com.example

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.ui.theme.BloodRed
import com.example.ui.theme.BouBhutTheme
import com.example.ui.theme.HorrorBg
import com.example.ui.theme.HorrorMuted
import com.example.ui.theme.HorrorSurface
import com.example.ui.theme.OnHorrorSurface

class MainActivity : ComponentActivity() {

    private var webViewInstance: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Keep screen on during gameplay
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Hide system bars for complete horror immersion
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())

        setContent {
            BouBhutTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("game_surface"),
                    color = HorrorBg
                ) {
                    BouBhutGameContainer(
                        onSetWebView = { webViewInstance = it },
                        onExitApp = { finish() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        webViewInstance?.onResume()
        // Re-hide system bars when returning to app
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onPause() {
        super.onPause()
        webViewInstance?.onPause()
    }

    override fun onDestroy() {
        webViewInstance?.destroy()
        webViewInstance = null
        super.onDestroy()
    }
}

class AndroidBridge(
    private val context: Context,
    private val prefs: SharedPreferences
) {
    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    @JavascriptInterface
    fun vibrate(milliseconds: Long) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val duration = milliseconds.coerceIn(10L, 1000L)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(
                VibrationEffect.createOneShot(
                    duration,
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
            )
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(duration)
        }
    }

    @JavascriptInterface
    fun triggerHeartbeatVibe(intensity: Float) {
        val v = vibrator ?: return
        if (!v.hasVibrator() || intensity < 0.25f) return
        val duration = (25 + intensity * 35).toLong()
        val amplitude = (50 + intensity * 180).toInt().coerceIn(1, 255)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(duration, amplitude))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(duration)
        }
    }

    @JavascriptInterface
    fun triggerJumpscareVibe() {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val pattern = longArrayOf(0, 110, 40, 140, 40, 260)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255)
            v.vibrate(VibrationEffect.createWaveform(pattern, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(pattern, -1)
        }
    }

    @JavascriptInterface
    fun onGameWin(time: String, difficulty: String) {
        val wins = prefs.getInt("total_wins", 0) + 1
        prefs.edit().putInt("total_wins", wins).apply()
    }

    @JavascriptInterface
    fun onGameOver(time: String, difficulty: String) {
        val losses = prefs.getInt("total_losses", 0) + 1
        prefs.edit().putInt("total_losses", losses).apply()
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BouBhutGameContainer(
    onSetWebView: (WebView) -> Unit,
    onExitApp: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("boubhut_save_data", Context.MODE_PRIVATE)
    }
    var showExitDialog by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Intercept Back button: pause game if playing, or confirm exit
    BackHandler {
        webViewRef?.let { wv ->
            wv.evaluateJavascript("window.AndroidGameBridge ? window.AndroidGameBridge.isGamePlaying() : false") { result ->
                val isPlaying = result == "true"
                if (isPlaying) {
                    wv.evaluateJavascript("if(window.AndroidGameBridge) window.AndroidGameBridge.pauseGame();", null)
                } else {
                    showExitDialog = true
                }
            }
        } ?: run {
            showExitDialog = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HorrorBg)
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .testTag("game_webview"),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(0xFF000000.toInt())
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        allowFileAccess = true
                        allowContentAccess = true
                        @Suppress("DEPRECATION")
                        allowFileAccessFromFileURLs = true
                        @Suppress("DEPRECATION")
                        allowUniversalAccessFromFileURLs = true
                        mediaPlaybackRequiresUserGesture = false
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        setSupportZoom(false)
                        displayZoomControls = false
                        cacheMode = WebSettings.LOAD_DEFAULT
                    }

                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            view?.requestFocus()
                        }
                    }

                    addJavascriptInterface(AndroidBridge(ctx, prefs), "AndroidBridge")
                    loadUrl("file:///android_asset/game/index.html")

                    webViewRef = this
                    onSetWebView(this)
                }
            },
            update = { wv ->
                webViewRef = wv
                onSetWebView(wv)
            }
        )

        DisposableEffect(Unit) {
            onDispose {
                webViewRef?.destroy()
            }
        }

        if (showExitDialog) {
            AlertDialog(
                onDismissRequest = { showExitDialog = false },
                containerColor = HorrorSurface,
                title = {
                    Text(
                        text = "খেলা থেকে বের হবেন?",
                        color = BloodRed
                    )
                },
                text = {
                    Text(
                        text = "আপনি কি নিশ্চিতভাবে বৌ ভুত 3D খেলা বন্ধ করতে চান?",
                        color = OnHorrorSurface
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showExitDialog = false
                            onExitApp()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BloodRed),
                        modifier = Modifier.testTag("confirm_exit_button")
                    ) {
                        Text("হ্যাঁ, বের হও", color = HorrorBg)
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { showExitDialog = false },
                        modifier = Modifier.testTag("cancel_exit_button")
                    ) {
                        Text("না, খেলবো", color = HorrorMuted)
                    }
                }
            )
        }
    }
}

package com.parraanimation.parraamp

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color as AColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.delay
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val state = PlayerState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT)
        )
        FidelidacFx.init(this)
        state.connect(this)
        setContent {
            ParraTheme {
                var showSplash by rememberSaveable { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    if (showSplash) {
                        delay(1800)
                        showSplash = false
                    }
                }
                Crossfade(
                    targetState = showSplash,
                    animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
                    label = "splash"
                ) { splash ->
                    if (splash) SplashScreen() else Root(state)
                }
            }
        }
    }

    override fun onDestroy() {
        state.release()
        super.onDestroy()
    }
}

@Composable
private fun Root(state: PlayerState) {
    val ctx = LocalContext.current
    val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        granted = res[perm] == true
    }
    val request = {
        val list = mutableListOf(perm)
        if (Build.VERSION.SDK_INT >= 33) list += Manifest.permission.POST_NOTIFICATIONS
        launcher.launch(list.toTypedArray())
    }
    LaunchedEffect(Unit) { if (!granted) request() }

    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    LaunchedEffect(granted) {
        if (granted) tracks = withContext(Dispatchers.IO) { loadTracks(ctx) }
    }
    HiFiApp(state, tracks, granted, onRequestPermission = { request() })
}

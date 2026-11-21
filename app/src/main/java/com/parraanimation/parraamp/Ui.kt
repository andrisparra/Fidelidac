package com.parraanimation.parraamp

import android.content.ContentUris
import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// ---- Paleta estilo Walkman: negro profundo, naranja de acento, dorado Hi-Res ----
val Bg = Color(0xFF0A0A0B)
val Panel = Color(0xFF151517)
val Accent = Color(0xFFFF6A00)
val Gold = Color(0xFFE6C068)
val Muted = Color(0xFF8E8E93)

@Composable
fun ParraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent, background = Bg, surface = Panel,
            onSurface = Color.White, onBackground = Color.White
        ),
        content = content
    )
}

@Composable
fun HiFiApp(state: PlayerState, tracks: List<Track>, granted: Boolean, onRequestPermission: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }

    LaunchedEffect(state.isPlaying) {
        while (state.isPlaying) { state.refreshPosition(); delay(250) }
        state.refreshPosition()
    }
    BackHandler(enabled = tab == 1) { tab = 0 }

    val navColors = NavigationBarItemDefaults.colors(
        selectedIconColor = Accent, selectedTextColor = Accent,
        unselectedIconColor = Muted, unselectedTextColor = Muted,
        indicatorColor = Color.Transparent
    )

    Column(Modifier.fillMaxSize().background(Bg)) {
        Box(Modifier.weight(1f)) {
            if (tab == 0) {
                LibraryScreen(tracks, granted, state, onRequestPermission) { index ->
                    state.play(tracks, index); tab = 1
                }
            } else {
                NowPlayingScreen(state)
            }
        }
        if (tab == 0 && state.item != null) MiniPlayer(state) { tab = 1 }
        NavigationBar(containerColor = Panel) {
            NavigationBarItem(
                selected = tab == 0, onClick = { tab = 0 },
                icon = { Icon(Icons.Rounded.LibraryMusic, null) },
                label = { Text("Biblioteca") }, colors = navColors
            )
            NavigationBarItem(
                selected = tab == 1, onClick = { tab = 1 },
                icon = { Icon(Icons.Rounded.GraphicEq, null) },
                label = { Text("Reproduciendo") }, colors = navColors
            )
        }
    }
}

// ------------------------------- Biblioteca -------------------------------

@Composable
private fun LibraryScreen(
    tracks: List<Track>, granted: Boolean, state: PlayerState,
    onRequestPermission: () -> Unit, onPlay: (Int) -> Unit
) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)) {
            Text("ParraAmp", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text("Biblioteca", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
            if (granted) Text("${tracks.size} canciones", color = Muted, fontSize = 13.sp)
        }
        when {
            !granted -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Necesito acceso a tu música", color = Muted)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onRequestPermission, colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                        Text("Dar permiso", color = Color.Black)
                    }
                }
            }
            tracks.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No se encontró música en el dispositivo", color = Muted)
            }
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 12.dp)) {
                itemsIndexed(tracks, key = { _, t -> t.id }) { i, t ->
                    TrackRow(t, selected = state.item?.mediaId == t.id.toString()) { onPlay(i) }
                }
            }
        }
    }
}

@Composable
private fun TrackRow(t: Track, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(Panel), contentAlignment = Alignment.Center) {
            Icon(
                if (selected) Icons.Rounded.GraphicEq else Icons.Rounded.MusicNote, null,
                tint = if (selected) Accent else Muted
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                t.title, color = if (selected) Accent else Color.White, fontSize = 15.sp,
                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text("${t.artist} · ${t.album}", color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text(fmt(t.durationMs), color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun MiniPlayer(state: PlayerState, onOpen: () -> Unit) {
    val md = state.item?.mediaMetadata
    Row(
        Modifier.fillMaxWidth().background(Panel).clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(md?.title?.toString() ?: "", color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(md?.artist?.toString() ?: "", color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = { state.togglePlay() }) {
            Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Accent)
        }
    }
}

// ------------------------------ Reproduciendo ------------------------------

@Composable
private fun NowPlayingScreen(state: PlayerState) {
    val id = state.item?.mediaId?.toLongOrNull()
    val uri = id?.let { ContentUris.withAppendedId(audioCollection(), it) }
    val art = rememberArt(uri)
    val bits = rememberBitDepth(uri)
    val md = state.item?.mediaMetadata

    if (state.item == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Elige una canción de la biblioteca", color = Muted)
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        if (art != null) {
            Image(art, null, Modifier.fillMaxSize().blur(70.dp).alpha(0.35f), contentScale = ContentScale.Crop)
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x99000000), Bg))))

        Column(
            Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(20.dp))
            Text("REPRODUCIENDO", color = Muted, fontSize = 11.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(20.dp))

            Box(
                Modifier.fillMaxWidth().aspectRatio(1f)
                    .shadow(28.dp, RoundedCornerShape(22.dp))
                    .clip(RoundedCornerShape(22.dp)).background(Panel),
                contentAlignment = Alignment.Center
            ) {
                if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(Icons.Rounded.MusicNote, null, tint = Muted, modifier = Modifier.size(96.dp))
            }

            Spacer(Modifier.height(24.dp))
            Column(Modifier.fillMaxWidth()) {
                Text(
                    md?.title?.toString() ?: "", color = Color.White, fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    md?.artist?.toString() ?: "", color = Muted, fontSize = 16.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(10.dp))
                QualityTags(state, bits)
            }

            Spacer(Modifier.weight(1f))
            SeekBar(state)
            Spacer(Modifier.height(8.dp))
            Controls(state)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun QualityTags(state: PlayerState, bits: Int?) {
    val f = state.format
    val codec = codecName(f?.sampleMimeType)
    val lossless = codec in setOf("FLAC", "ALAC", "WAV")
    val sr = f?.sampleRate ?: -1
    val hiRes = lossless && (sr >= 88200 || (bits ?: 0) >= 24)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (hiRes) Tag("Hi-Res", Gold, filled = true)
        else if (lossless) Tag("Lossless", Gold)
        Tag(codec, Muted)
        if (sr > 0) {
            val khz = if (sr % 1000 == 0) "${sr / 1000} kHz" else "%.1f kHz".format(sr / 1000.0)
            Tag(if (bits != null && bits > 0) "$khz · $bits bit" else khz, Muted)
        }
        val br = f?.bitrate ?: -1
        if (!lossless && br > 0) Tag("${br / 1000} kbps", Muted)
    }
}

@Composable
private fun Tag(text: String, color: Color, filled: Boolean = false) {
    val shape = RoundedCornerShape(6.dp)
    Text(
        text, color = if (filled) Color.Black else color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(shape)
            .then(if (filled) Modifier.background(color) else Modifier.border(1.dp, color.copy(alpha = 0.6f), shape))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    )
}

@Composable
private fun SeekBar(state: PlayerState) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val dur = state.durationMs.coerceAtLeast(1L)
    val frac = (drag ?: (state.positionMs.toFloat() / dur)).coerceIn(0f, 1f)
    Slider(
        value = frac,
        onValueChange = { drag = it },
        onValueChangeFinished = { drag?.let { state.seekTo((it * dur).toLong()) }; drag = null },
        colors = SliderDefaults.colors(
            thumbColor = Color.White, activeTrackColor = Accent,
            inactiveTrackColor = Color.White.copy(alpha = 0.18f)
        )
    )
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(fmt((frac * dur).toLong()), color = Muted, fontSize = 12.sp)
        Text(fmt(state.durationMs), color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun Controls(state: PlayerState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { state.toggleShuffle() }) {
            Icon(Icons.Rounded.Shuffle, null, tint = if (state.shuffle) Accent else Muted)
        }
        IconButton(onClick = { state.previous() }, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Rounded.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(36.dp))
        }
        Box(
            Modifier.size(78.dp).clip(CircleShape).background(Accent).clickable { state.togglePlay() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null,
                tint = Color.Black, modifier = Modifier.size(42.dp)
            )
        }
        IconButton(onClick = { state.next() }, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Rounded.SkipNext, null, tint = Color.White, modifier = Modifier.size(36.dp))
        }
        IconButton(onClick = { state.cycleRepeat() }) {
            Icon(
                if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, null,
                tint = if (state.repeatMode == Player.REPEAT_MODE_OFF) Muted else Accent
            )
        }
    }
}

// ------------------------------- Utilidades -------------------------------

fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

fun codecName(mime: String?): String = when (mime) {
    null -> "—"
    "audio/flac" -> "FLAC"
    "audio/mpeg" -> "MP3"
    "audio/mp4a-latm" -> "AAC"
    "audio/alac" -> "ALAC"
    "audio/raw" -> "WAV"
    "audio/vorbis" -> "OGG"
    "audio/opus" -> "OPUS"
    else -> mime.substringAfter('/').uppercase()
}

@Composable
private fun rememberArt(uri: Uri?): ImageBitmap? {
    val ctx = LocalContext.current
    return produceState<ImageBitmap?>(null, uri) {
        value = if (uri == null) null else withContext(Dispatchers.IO) { loadArt(ctx, uri) }
    }.value
}

private fun loadArt(ctx: Context, uri: Uri): ImageBitmap? = runCatching {
    if (Build.VERSION.SDK_INT >= 29) {
        ctx.contentResolver.loadThumbnail(uri, Size(1000, 1000), null).asImageBitmap()
    } else {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, uri)
            r.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size).asImageBitmap() }
        } finally { r.release() }
    }
}.getOrNull()

@Composable
private fun rememberBitDepth(uri: Uri?): Int? {
    val ctx = LocalContext.current
    return produceState<Int?>(null, uri) {
        value = if (uri == null || Build.VERSION.SDK_INT < 31) null else withContext(Dispatchers.IO) {
            runCatching {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(ctx, uri)
                    r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)?.toIntOrNull()
                } finally { r.release() }
            }.getOrNull()
        }
    }.value
}

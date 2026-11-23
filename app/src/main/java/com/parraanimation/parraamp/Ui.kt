package com.parraanimation.parraamp

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.widget.Toast
import android.util.LruCache
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.sin

// ---- Paleta: negro profundo, naranja de acento, dorado Hi-Res ----
val Bg = Color(0xFF0A0A0B)
val Panel = Color(0xFF151517)
val Accent = Color(0xFFFF6A00)
val Gold = Color(0xFFE6C068)
val Muted = Color(0xFF8E8E93)
val Light = Color(0xFFD0D0D6)

@Composable
fun ParraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Light, background = Bg, surface = Panel,
            onSurface = Color.White, onBackground = Color.White
        ),
        content = content
    )
}

private enum class Screen { HOME, SONGS, PLAYER }

@Composable
fun HiFiApp(state: PlayerState, tracks: List<Track>, granted: Boolean, onRequestPermission: () -> Unit) {
    val ctx = LocalContext.current
    val stack = remember { mutableStateListOf(Screen.HOME) }
    val screen = stack.last()
    val go: (Screen) -> Unit = { s -> if (stack.last() != s) stack.add(s) }
    val toast: (String) -> Unit = { msg -> Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() }

    LaunchedEffect(state.isPlaying) {
        while (state.isPlaying) { state.refreshPosition(); delay(250) }
        state.refreshPosition()
    }
    // Volver atrás: con la barra o el gesto de retroceso del sistema
    BackHandler(enabled = stack.size > 1) { stack.removeAt(stack.lastIndex) }

    Column(Modifier.fillMaxSize().background(Bg)) {
        Box(Modifier.weight(1f)) {
            when (screen) {
                Screen.HOME -> HomeScreen(
                    state = state, tracks = tracks,
                    onAllSongs = { go(Screen.SONGS) },
                    onQueue = { if (state.item != null) go(Screen.PLAYER) else toast("No hay música en cola") },
                    onRandomFm = {
                        if (tracks.isEmpty()) toast("No hay canciones para reproducir")
                        else { state.playRandom(tracks); go(Screen.PLAYER) }
                    },
                    onSoon = { name -> toast("$name: disponible en la próxima actualización") }
                )
                Screen.SONGS -> LibraryScreen(tracks, granted, state, onRequestPermission) { index ->
                    state.play(tracks, index); go(Screen.PLAYER)
                }
                Screen.PLAYER -> NowPlayingScreen(state)
            }
        }
        // El mini reproductor solo aparece si hay algo cargado
        if (screen != Screen.PLAYER && state.item != null) MiniPlayer(state) { go(Screen.PLAYER) }
    }
}

// ------------------------------- Biblioteca -------------------------------

@Composable
private fun LibraryScreen(
    tracks: List<Track>, granted: Boolean, state: PlayerState,
    onRequestPermission: () -> Unit, onPlay: (Int) -> Unit
) {
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)) {
            Text("FIDELIDAC", color = Light, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            Text("Todas las canciones", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
            if (granted) Text("${tracks.size} canciones", color = Muted, fontSize = 13.sp)
        }
        when {
            !granted -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Necesito acceso a tu música", color = Muted)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onRequestPermission, colors = ButtonDefaults.buttonColors(containerColor = Light)) {
                        Text("Dar permiso", color = Color.Black)
                    }
                }
            }
            tracks.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No se encontró música en el dispositivo", color = Muted)
            }
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 12.dp + (if (state.item == null) navBottom else 0.dp))) {
                itemsIndexed(tracks, key = { _, t -> t.id }) { i, t ->
                    TrackRow(t, selected = state.item?.mediaId == t.id.toString()) { onPlay(i) }
                }
            }
        }
    }
}

@Composable
private fun TrackRow(t: Track, selected: Boolean, onClick: () -> Unit) {
    val info = rememberAudioInfo(t.id, t.mime)
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
        QualityBadge(info, 11.sp)
        Spacer(Modifier.width(10.dp))
        Text(fmt(t.durationMs), color = Muted, fontSize = 12.sp)
    }
}

@Composable
internal fun MiniPlayer(state: PlayerState, onOpen: () -> Unit) {
    val md = state.item?.mediaMetadata
    val uri = state.item?.mediaId?.toLongOrNull()?.let { ContentUris.withAppendedId(audioCollection(), it) }
    val thumb = rememberArt(uri, 160)
    val frac = (state.positionMs.toFloat() / state.durationMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
            .background(Color(0xFF2A2A2A))
            .clickable(onClick = onOpen)
    ) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(Color.White.copy(alpha = 0.08f))) {
            Box(Modifier.fillMaxWidth(frac).height(2.dp).background(BadgeText))
        }
        Row(
            Modifier.navigationBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(Panel), contentAlignment = Alignment.Center) {
                if (thumb != null) Image(thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(Icons.Rounded.MusicNote, null, tint = Muted)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(md?.title?.toString() ?: "", color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(md?.artist?.toString() ?: "", color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { state.togglePlay() }) {
                Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Light)
            }
            IconButton(onClick = { state.next() }) {
                Icon(Icons.Rounded.SkipNext, null, tint = Light)
            }
        }
    }
}

// ------------------------------ Reproduciendo ------------------------------

private val PlayerBg = Color(0xFF161616)
private val SoftGray = Color(0xFF8E8E93)

@Composable
private fun NowPlayingScreen(state: PlayerState) {
    val ctx = LocalContext.current
    val uri = state.item?.mediaId?.toLongOrNull()?.let { ContentUris.withAppendedId(audioCollection(), it) }
    val art = rememberArt(uri, 1000)
    val bits = rememberBitDepth(uri)
    val favs = remember { Favorites(ctx) }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    if (state.item == null) {
        Box(Modifier.fillMaxSize().background(PlayerBg), contentAlignment = Alignment.Center) {
            Text("Elige una canción de la biblioteca", color = Muted)
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize().background(PlayerBg),
        contentPadding = PaddingValues(bottom = 24.dp + navBottom)
    ) {
        item { PlayerTop(state, art, bits, favs) }
        itemsIndexed(state.queue) { i, q ->
            QueueRow(
                item = q, current = i == state.index,
                onPlay = { state.skipTo(i) }, onRemove = { state.removeAt(i) }
            )
        }
    }
}

@Composable
private fun PlayerTop(state: PlayerState, art: ImageBitmap?, bits: Int?, favs: Favorites) {
    val md = state.item?.mediaMetadata
    val mediaId = state.item?.mediaId ?: ""
    val isFav = mediaId in favs.ids
    val probed = rememberAudioInfo(state.item?.mediaId?.toLongOrNull(), null)
    val live = state.format
    val liveMime = live?.sampleMimeType
    val info: AudioInfo? = if (live != null && liveMime != null)
        AudioInfo(codecName(liveMime), losslessMime(liveMime) ?: false, live.sampleRate, probed?.bits ?: bits, live.bitrate)
    else probed

    var drag by remember { mutableStateOf<Float?>(null) }
    val dur = state.durationMs.coerceAtLeast(1L)
    val frac = (drag ?: (state.positionMs.toFloat() / dur)).coerceIn(0f, 1f)
    val timeColor = Color(0xFFCFC9BE)

    Column(
        Modifier.fillMaxWidth().statusBarsPadding().padding(top = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Carátula
        Box(
            Modifier.padding(horizontal = 40.dp).fillMaxWidth().aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp)).background(Panel)
        ) {
            if (art != null) {
                Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Icon(Icons.Rounded.MusicNote, null, tint = Muted, modifier = Modifier.align(Alignment.Center).size(96.dp))
            }
        }

        // Etiqueta de calidad (izquierda) y datos del archivo (derecha)
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 40.dp).heightIn(min = 36.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (info != null) {
                QualityBadge(info, 15.sp)
                Text(
                    detailText(info),
                    color = if (info.quality == Quality.LOSSY) Color(0xFFBDBDBD) else BadgeText,
                    fontSize = 14.sp, fontWeight = FontWeight.Medium
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        // Tiempos y posición en la cola
        Row(Modifier.fillMaxWidth().padding(horizontal = 40.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmt((frac * dur).toLong()), color = timeColor, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Text("${state.index + 1}/${state.queue.size}", color = timeColor, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Text(fmt(state.durationMs), color = timeColor, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        }
        SeekBar(frac, onDrag = { drag = it }, onSeek = { state.seekToFraction(it) })

        // Título, artista, álbum y corazón
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().padding(horizontal = 40.dp)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    md?.title?.toString() ?: "", color = Color.White, fontSize = 24.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    md?.artist?.toString() ?: "", color = SoftGray, fontSize = 20.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
                )
                Text(
                    md?.albumTitle?.toString() ?: "", color = Color(0xFF7C7C80), fontSize = 18.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
                )
            }
            IconButton(onClick = { favs.toggle(mediaId) }, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(
                    if (isFav) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder, null,
                    tint = if (isFav) BadgeText else Color(0xFFE6E6E6),
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        // Controles
        Spacer(Modifier.height(28.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 30.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { state.toggleShuffle() }, modifier = Modifier.size(44.dp)) {
                Icon(
                    Icons.Outlined.Shuffle, null,
                    tint = if (state.shuffle) BadgeText else Color(0xFFB8B8B8), modifier = Modifier.size(28.dp)
                )
            }
            CircleButton(Icons.Outlined.SkipPrevious) { state.previous() }
            PlayButton(state.isPlaying) { state.togglePlay() }
            CircleButton(Icons.Outlined.SkipNext) { state.next() }
            IconButton(onClick = { state.cycleRepeat() }, modifier = Modifier.size(44.dp)) {
                Icon(
                    if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Outlined.RepeatOne else Icons.Outlined.Repeat, null,
                    tint = if (state.repeatMode == Player.REPEAT_MODE_OFF) Color(0xFFB8B8B8) else BadgeText,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun CircleButton(icon: ImageVector, onClick: () -> Unit) {
    Box(
        Modifier.size(52.dp).clip(CircleShape).background(Color(0xFF262626))
            .border(1.dp, Color(0xFF3E3E3E), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = Color(0xFFCFCFCF), modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun PlayButton(playing: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(80.dp)
            .shadow(14.dp, CircleShape, ambientColor = BadgeGold.copy(alpha = 0.6f), spotColor = BadgeGold.copy(alpha = 0.6f))
            .clip(CircleShape).background(Color(0xFF1F1D1A))
            .border(1.dp, Color(0xFF4A4233), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.size(64.dp).clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color(0xFF3A362E), Color(0xFF1C1B19))))
                .border(2.dp, Brush.verticalGradient(listOf(Color(0xFFEAD8A4), Color(0xFFA6874A))), CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (playing) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(2) {
                        Box(Modifier.size(width = 5.dp, height = 22.dp).clip(RoundedCornerShape(3.dp)).background(BadgeText))
                    }
                }
            } else {
                Icon(Icons.Rounded.PlayArrow, null, tint = BadgeText, modifier = Modifier.size(34.dp))
            }
        }
    }
}

private fun seekFraction(x: Float, width: Int, pad: Float): Float =
    if (width <= 2 * pad) 0f else ((x - pad) / (width - 2 * pad)).coerceIn(0f, 1f)

@Composable
private fun SeekBar(frac: Float, onDrag: (Float?) -> Unit, onSeek: (Float) -> Unit) {
    Canvas(
        Modifier.fillMaxWidth().padding(horizontal = 30.dp).height(36.dp)
            .pointerInput(Unit) {
                val pad = 10.dp.toPx()
                detectTapGestures { o -> onSeek(seekFraction(o.x, size.width, pad)) }
            }
            .pointerInput(Unit) {
                val pad = 10.dp.toPx()
                var last = 0f
                detectHorizontalDragGestures(
                    onDragStart = { o -> last = seekFraction(o.x, size.width, pad); onDrag(last) },
                    onDragEnd = { onSeek(last); onDrag(null) },
                    onDragCancel = { onDrag(null) },
                    onHorizontalDrag = { change, _ ->
                        last = seekFraction(change.position.x, size.width, pad)
                        onDrag(last)
                    }
                )
            }
    ) {
        val pad = 10.dp.toPx()
        val w = size.width - 2 * pad
        val y = size.height / 2
        val x = pad + w * frac
        drawLine(Color.White.copy(alpha = 0.14f), Offset(pad, y), Offset(pad + w, y), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        if (x > pad + 1f) {
            drawLine(
                brush = Brush.horizontalGradient(listOf(Color(0xFF8C7A4E), Color(0xFFF1E3B6)), startX = pad, endX = x),
                start = Offset(pad, y), end = Offset(x, y), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round
            )
        }
        drawCircle(Color(0x33E6CB86), radius = 15.dp.toPx(), center = Offset(x, y))
        drawCircle(Color(0xFFD9D9D9), radius = 8.dp.toPx(), center = Offset(x, y))
        drawCircle(Color.White.copy(alpha = 0.55f), radius = 8.dp.toPx(), center = Offset(x, y), style = Stroke(width = 1.dp.toPx()))
    }
}

@Composable
private fun QueueRow(item: MediaItem, current: Boolean, onPlay: () -> Unit, onRemove: () -> Unit) {
    val uri = item.mediaId.toLongOrNull()?.let { ContentUris.withAppendedId(audioCollection(), it) }
    val thumb = rememberArt(uri, 200)
    var menu by remember { mutableStateOf(false) }
    val md = item.mediaMetadata
    val info = rememberAudioInfo(item.mediaId.toLongOrNull(), null)
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(Panel), contentAlignment = Alignment.Center) {
                if (thumb != null) Image(thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(Icons.Rounded.MusicNote, null, tint = Muted)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    md.title?.toString() ?: "", color = if (current) BadgeText else Color.White, fontSize = 18.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(md.artist?.toString() ?: "", color = SoftGray, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            QualityBadge(info, 12.sp)
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Rounded.MoreVert, null, tint = Color.White.copy(alpha = 0.85f))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Reproducir") }, onClick = { menu = false; onPlay() })
                    DropdownMenuItem(text = { Text("Quitar de la cola") }, onClick = { menu = false; onRemove() })
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f)))
    }
}

// ------------------------------- Favoritos -------------------------------

class Favorites(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("favorites", Context.MODE_PRIVATE)
    var ids by mutableStateOf<Set<String>>(prefs.getStringSet("ids", emptySet())?.toSet() ?: emptySet())
        private set

    fun toggle(id: String) {
        val n = ids.toMutableSet()
        if (!n.add(id)) n.remove(id)
        ids = n
        prefs.edit().putStringSet("ids", n).apply()
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
    "audio/raw", "audio/wav", "audio/x-wav" -> "WAV"
    "audio/aiff", "audio/x-aiff" -> "AIFF"
    "audio/x-flac" -> "FLAC"
    "audio/aac" -> "AAC"
    "audio/ogg" -> "OGG"
    "audio/vorbis" -> "OGG"
    "audio/opus" -> "OPUS"
    else -> mime.substringAfter('/').uppercase()
}

private fun formatLine(sr: Int, br: Int, bits: Int?, codec: String, lossless: Boolean): String {
    val rate = if (sr > 0) (if (sr % 1000 == 0) "${sr / 1000} kHz" else "%.1f kHz".format(sr / 1000.0)) else null
    val depth = when {
        lossless && bits != null && bits > 0 -> "$bits bit"
        !lossless && br > 0 -> "${br / 1000} kbps"
        else -> null
    }
    val left = listOfNotNull(rate, depth).joinToString(" / ")
    return listOf(left, codec.takeIf { it != "—" } ?: "").filter { it.isNotEmpty() }.joinToString(" ")
}

// ------------------------- Calidad del audio -------------------------

data class AudioInfo(val codec: String, val lossless: Boolean, val sampleRate: Int, val bits: Int?, val bitrate: Int) {
    // Hi-Res: sin pérdida y por encima de calidad CD (24 bit o 88.2 kHz o más)
    val hiRes: Boolean get() = lossless && (sampleRate >= 88200 || (bits ?: 0) >= 24)
}

enum class Quality { HIRES, LOSSLESS, LOSSY }

val AudioInfo.quality: Quality
    get() = when {
        hiRes -> Quality.HIRES
        lossless -> Quality.LOSSLESS
        else -> Quality.LOSSY
    }

private val TagGold = Color(0xFFF0D46E)

fun losslessMime(mime: String?): Boolean? = when (mime) {
    null -> null
    "audio/flac", "audio/x-flac", "audio/alac", "audio/raw", "audio/wav", "audio/x-wav",
    "audio/aiff", "audio/x-aiff" -> true
    "audio/mpeg", "audio/mp4a-latm", "audio/aac", "audio/vorbis", "audio/opus", "audio/ogg",
    "audio/x-ms-wma", "audio/amr" -> false
    else -> null
}

private fun hintInfo(mime: String?): AudioInfo? {
    val l = losslessMime(mime) ?: return null
    return AudioInfo(codecName(mime), l, -1, null, -1)
}

private val infoCache = LruCache<Long, AudioInfo>(3000)

@Composable
private fun rememberAudioInfo(id: Long?, hintMime: String?): AudioInfo? {
    val ctx = LocalContext.current
    return produceState<AudioInfo?>(id?.let { infoCache.get(it) } ?: hintInfo(hintMime), id) {
        if (id != null) {
            val cached = infoCache.get(id)
            if (cached != null) {
                value = cached
            } else {
                val loaded = withContext(Dispatchers.IO) {
                    probeAudio(ctx, ContentUris.withAppendedId(audioCollection(), id))
                }
                if (loaded != null) {
                    infoCache.put(id, loaded)
                    value = loaded
                }
            }
        }
    }.value
}

private fun probeBits(ctx: Context, uri: Uri): Int? {
    if (Build.VERSION.SDK_INT < 31) return null
    return runCatching {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, uri)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)?.toIntOrNull()
        } finally { r.release() }
    }.getOrNull()
}

private fun probeAudio(ctx: Context, uri: Uri): AudioInfo? = runCatching {
    val ex = MediaExtractor()
    try {
        ex.setDataSource(ctx, uri, null)
        var fmt: MediaFormat? = null
        for (i in 0 until ex.trackCount) {
            val f = ex.getTrackFormat(i)
            if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) { fmt = f; break }
        }
        val f = fmt
        if (f == null) null else {
            val mime = f.getString(MediaFormat.KEY_MIME)
            val sr = if (f.containsKey(MediaFormat.KEY_SAMPLE_RATE)) f.getInteger(MediaFormat.KEY_SAMPLE_RATE) else -1
            val br = if (f.containsKey(MediaFormat.KEY_BIT_RATE)) f.getInteger(MediaFormat.KEY_BIT_RATE) else -1
            AudioInfo(codecName(mime), losslessMime(mime) ?: false, sr, probeBits(ctx, uri), br)
        }
    } finally { ex.release() }
}.getOrNull()

private fun rateText(sr: Int): String? = when {
    sr <= 0 -> null
    sr % 1000 == 0 -> "${sr / 1000} kHz"
    else -> String.format(java.util.Locale.US, "%.1f kHz", sr / 1000.0)
}

private val BadgeGold = Color(0xFFD8B866)
private val BadgeText = Color(0xFFE6CB86)
private val BadgeFill = Color(0xFF3B3322)

private fun detailText(info: AudioInfo): String {
    val bitsText = info.bits?.takeIf { it > 0 && info.lossless }?.let { "$it bit" }
    return (listOfNotNull(rateText(info.sampleRate), bitsText).joinToString(" / ") + " " + info.codec).trim()
}

// HiRes y Lossless: píldora dorada. Lossy: solo texto gris.
@Composable
private fun QualityBadge(info: AudioInfo?, size: TextUnit) {
    if (info == null) return
    when (info.quality) {
        Quality.HIRES -> BadgePill("HiRes", size)
        Quality.LOSSLESS -> BadgePill("Lossless", size)
        Quality.LOSSY -> Text(
            "Lossy", color = Color(0xFFBDBDBD), fontSize = size,
            fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false
        )
    }
}

@Composable
private fun BadgePill(text: String, size: TextUnit) {
    val shape = RoundedCornerShape(8.dp)
    Text(
        text, color = BadgeText, fontSize = size, fontWeight = FontWeight.Medium,
        maxLines = 1, softWrap = false,
        modifier = Modifier.clip(shape).background(BadgeFill)
            .border(1.5.dp, BadgeGold, shape)
            .padding(horizontal = (size.value * 0.7f).dp, vertical = (size.value * 0.25f).dp)
    )
}

private val artCache = LruCache<String, ImageBitmap>(80)

@Composable
private fun rememberArt(uri: Uri?, px: Int): ImageBitmap? {
    val ctx = LocalContext.current
    return produceState<ImageBitmap?>(uri?.let { artCache.get("$it@$px") }, uri, px) {
        value = if (uri == null) null else {
            val key = "$uri@$px"
            val cached = artCache.get(key)
            if (cached != null) cached else {
                val loaded = withContext(Dispatchers.IO) { loadArt(ctx, uri, px) }
                if (loaded != null && px <= 256) artCache.put(key, loaded)
                loaded
            }
        }
    }.value
}

internal fun loadArt(ctx: Context, uri: Uri, px: Int): ImageBitmap? = runCatching {
    if (Build.VERSION.SDK_INT >= 29) {
        ctx.contentResolver.loadThumbnail(uri, Size(px, px), null).asImageBitmap()
    } else {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, uri)
            r.embeddedPicture?.let {
                var bmp: Bitmap = BitmapFactory.decodeByteArray(it, 0, it.size)
                if (bmp.width > px * 2) bmp = Bitmap.createScaledBitmap(bmp, px, px, true)
                bmp.asImageBitmap()
            }
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

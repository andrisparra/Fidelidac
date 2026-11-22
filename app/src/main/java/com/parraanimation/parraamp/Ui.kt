package com.parraanimation.parraamp

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
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
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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

@Composable
fun HiFiApp(state: PlayerState, tracks: List<Track>, granted: Boolean, onRequestPermission: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }

    LaunchedEffect(state.isPlaying) {
        while (state.isPlaying) { state.refreshPosition(); delay(250) }
        state.refreshPosition()
    }
    BackHandler(enabled = tab == 1) { tab = 0 }

    val navColors = NavigationBarItemDefaults.colors(
        selectedIconColor = Light, selectedTextColor = Light,
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
            Text("FIDELIDAC", color = Light, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            Text("Biblioteca", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
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
            Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Light)
        }
    }
}

// ------------------------------ Reproduciendo ------------------------------

@Composable
private fun NowPlayingScreen(state: PlayerState) {
    val ctx = LocalContext.current
    val uri = state.item?.mediaId?.toLongOrNull()?.let { ContentUris.withAppendedId(audioCollection(), it) }
    val art = rememberArt(uri, 1000)
    val bits = rememberBitDepth(uri)
    val favs = remember { Favorites(ctx) }

    if (state.item == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Elige una canción de la biblioteca", color = Muted)
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        if (art != null) {
            Image(art, null, Modifier.fillMaxSize().blur(70.dp).alpha(0.22f), contentScale = ContentScale.Crop)
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { PlayerTop(state, art, bits, favs) }
            itemsIndexed(state.queue) { i, q ->
                QueueRow(
                    item = q, current = i == state.index,
                    onPlay = { state.skipTo(i) }, onRemove = { state.removeAt(i) }
                )
            }
        }
    }
}

@Composable
private fun PlayerTop(state: PlayerState, art: ImageBitmap?, bits: Int?, favs: Favorites) {
    val md = state.item?.mediaMetadata
    val mediaId = state.item?.mediaId ?: ""
    val isFav = mediaId in favs.ids
    val f = state.format
    val codec = codecName(f?.sampleMimeType)
    val lossless = codec in setOf("FLAC", "ALAC", "WAV")
    val sr = f?.sampleRate ?: -1
    val br = f?.bitrate ?: -1
    val hiRes = lossless && (sr >= 88200 || (bits ?: 0) >= 24)

    var drag by remember { mutableStateOf<Float?>(null) }
    val dur = state.durationMs.coerceAtLeast(1L)
    val frac = (drag ?: (state.positionMs.toFloat() / dur)).coerceIn(0f, 1f)

    Column(
        Modifier.fillMaxWidth().statusBarsPadding().padding(top = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Carátula con etiqueta Hi-Res
        Box(
            Modifier.padding(horizontal = 26.dp).fillMaxWidth().aspectRatio(1f)
                .clip(RoundedCornerShape(4.dp)).background(Panel)
        ) {
            if (art != null) {
                Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Icon(Icons.Rounded.MusicNote, null, tint = Muted, modifier = Modifier.align(Alignment.Center).size(96.dp))
            }
            if (hiRes) {
                Text(
                    "Hi-Res", color = Color.Black, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.BottomStart).padding(14.dp)
                        .clip(RoundedCornerShape(6.dp)).background(Gold)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // Tiempos y posición en la cola
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 26.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmt((frac * dur).toLong()), color = Color.White.copy(alpha = 0.9f), fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Text("${state.index + 1}/${state.queue.size}", color = Color.White.copy(alpha = 0.9f), fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Text(fmt(state.durationMs), color = Color.White.copy(alpha = 0.9f), fontSize = 17.sp, fontWeight = FontWeight.Medium)
        }
        SeekBar(frac, state.isPlaying, mediaId.hashCode(), onDrag = { drag = it }, onSeek = { state.seekToFraction(it) })

        // Título, artista, álbum y corazón
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().padding(horizontal = 26.dp)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    md?.title?.toString() ?: "", color = Color.White, fontSize = 28.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    md?.artist?.toString() ?: "", color = Color.White.copy(alpha = 0.78f), fontSize = 21.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    md?.albumTitle?.toString() ?: "", color = Color.White.copy(alpha = 0.55f), fontSize = 19.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
                )
            }
            IconButton(onClick = { favs.toggle(mediaId) }, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(
                    if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null,
                    tint = if (isFav) Light else Light.copy(alpha = 0.8f),
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        // Controles
        Spacer(Modifier.height(22.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { state.toggleShuffle() }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Rounded.Shuffle, null, tint = if (state.shuffle) Light else Light.copy(alpha = 0.4f))
            }
            RingButton(Icons.Rounded.SkipPrevious, 64.dp, 1.5.dp, Light.copy(alpha = 0.5f)) { state.previous() }
            RingButton(
                if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                90.dp, 2.5.dp, Light, iconSize = 48.dp
            ) { state.togglePlay() }
            RingButton(Icons.Rounded.SkipNext, 64.dp, 1.5.dp, Light.copy(alpha = 0.5f)) { state.next() }
            IconButton(onClick = { state.cycleRepeat() }, modifier = Modifier.size(40.dp)) {
                Icon(
                    if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, null,
                    tint = if (state.repeatMode == Player.REPEAT_MODE_OFF) Light.copy(alpha = 0.4f) else Light
                )
            }
        }

        // Formato del archivo
        Spacer(Modifier.height(24.dp))
        Text(formatLine(sr, br, bits, codec, lossless), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RingButton(
    icon: ImageVector, size: Dp, ring: Dp, ringColor: Color,
    iconSize: Dp = size * 0.45f, onClick: () -> Unit
) {
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(Color.White.copy(alpha = 0.06f))
            .border(ring, ringColor, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = Light, modifier = Modifier.size(iconSize))
    }
}

private fun seekFraction(x: Float, width: Int, pad: Float): Float =
    if (width <= 2 * pad) 0f else ((x - pad) / (width - 2 * pad)).coerceIn(0f, 1f)

@Composable
private fun SeekBar(frac: Float, playing: Boolean, seed: Int, onDrag: (Float?) -> Unit, onSeek: (Float) -> Unit) {
    // Forma de la onda: distinta por canción, pero fija (decorativa)
    val base = remember(seed) {
        val r = kotlin.random.Random(seed)
        val raw = FloatArray(202) { r.nextFloat() }
        FloatArray(200) { i -> 0.22f + 0.78f * ((raw[i] + 2f * raw[i + 1] + raw[i + 2]) / 4f) }
    }
    // Reloj de la animación: solo corre mientras suena
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing) {
        if (playing) {
            while (true) withFrameNanos { t -> phase = t / 1_000_000_000f }
        }
    }
    Canvas(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(56.dp)
            .pointerInput(Unit) {
                val pad = 8.dp.toPx()
                detectTapGestures { o -> onSeek(seekFraction(o.x, size.width, pad)) }
            }
            .pointerInput(Unit) {
                val pad = 8.dp.toPx()
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
        val pad = 8.dp.toPx()
        val w = size.width - 2 * pad
        val count = (w / 6.dp.toPx()).toInt().coerceAtLeast(10)
        val gap = w / count
        val cy = size.height / 2
        val maxH = size.height * 0.8f
        val headPos = frac * count
        for (i in 0 until count) {
            val x = pad + gap * (i + 0.5f)
            var h = base[(i * base.size) / count]
            val played = (i + 0.5f) <= headPos
            if (playing && played) {
                val d = headPos - i
                if (d <= 10f) h *= 1f + 0.35f * (1f - d / 10f) * sin(phase * 7f + i * 0.9f)
            }
            val half = (maxH * h.coerceAtMost(1.2f) / 2f).coerceAtLeast(2.dp.toPx())
            drawLine(
                if (played) Light else Color.White.copy(alpha = 0.25f),
                Offset(x, cy - half), Offset(x, cy + half),
                strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round
            )
        }
        val hx = pad + w * frac
        drawLine(
            Color.White, Offset(hx, size.height * 0.08f), Offset(hx, size.height * 0.92f),
            strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round
        )
    }
}

@Composable
private fun QueueRow(item: MediaItem, current: Boolean, onPlay: () -> Unit, onRemove: () -> Unit) {
    val uri = item.mediaId.toLongOrNull()?.let { ContentUris.withAppendedId(audioCollection(), it) }
    val thumb = rememberArt(uri, 160)
    var menu by remember { mutableStateOf(false) }
    val md = item.mediaMetadata
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(58.dp).clip(RoundedCornerShape(8.dp)).background(Panel), contentAlignment = Alignment.Center) {
                if (thumb != null) Image(thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(Icons.Rounded.MusicNote, null, tint = Muted)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    md.title?.toString() ?: "", color = if (current) Accent else Color.White, fontSize = 17.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(md.artist?.toString() ?: "", color = Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
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
    "audio/raw" -> "WAV"
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

private fun loadArt(ctx: Context, uri: Uri, px: Int): ImageBitmap? = runCatching {
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

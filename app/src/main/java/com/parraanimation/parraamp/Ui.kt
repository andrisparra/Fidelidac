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
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import kotlin.math.absoluteValue
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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

private enum class Screen { SONGS, PLAYER }

@Composable
fun HiFiApp(state: PlayerState, tracks: List<Track>, granted: Boolean, onRequestPermission: () -> Unit) {
    // La app abre en el reproductor. Con el gesto/barra "atrás" se pasa a la lista de canciones y otro "atrás" sale.
    val stack = remember { mutableStateListOf(Screen.SONGS, Screen.PLAYER) }
    val screen = stack.last()
    val go: (Screen) -> Unit = { s -> if (stack.last() != s) stack.add(s) }
    val openLibrary: () -> Unit = {
        if (stack.size > 1 && stack[stack.lastIndex - 1] == Screen.SONGS) stack.removeAt(stack.lastIndex)
        else go(Screen.SONGS)
    }

    LaunchedEffect(state.isPlaying) {
        while (state.isPlaying) { state.refreshPosition(); delay(250) }
        state.refreshPosition()
    }
    BackHandler(enabled = stack.size > 1) { stack.removeAt(stack.lastIndex) }

    Column(Modifier.fillMaxSize().background(Bg)) {
        Box(Modifier.weight(1f)) {
            when (screen) {
                Screen.SONGS -> LibraryScreen(tracks, granted, state, onRequestPermission) { index ->
                    state.play(tracks, index); go(Screen.PLAYER)
                }
                Screen.PLAYER -> NowPlayingScreen(state, openLibrary)
            }
        }
        // El mini reproductor solo aparece en la lista, y solo si hay algo cargado
        if (screen == Screen.SONGS && state.item != null) MiniPlayer(state) { go(Screen.PLAYER) }
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

private val PlayerBg = Color(0xFF000000)
private val LogoGold = Color(0xFFD4B14E)
private val GoldStops = listOf(Color(0xFFFFE08A), Color(0xFFF3C94E), Color(0xFFFFF0B3), Color(0xFFE2B33C))
private val SilverStops = listOf(Color(0xFFF1F3F5), Color(0xFFB9BFC6), Color(0xFFFFFFFF), Color(0xFFA2A9B1))

private data class TrackText(val title: String, val artist: String, val album: String)
private enum class InfoKind { GOLD, SILVER, GRAY }
private data class InfoState(val line: String, val kind: InfoKind)

// Pulsación con efecto de rebote suave
@Composable
private fun Modifier.pressable(onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale = animateFloatAsState(
        if (pressed) 0.88f else 1f,
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "press"
    )
    return this
        .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
        .clickable(interactionSource = src, indication = null, onClick = onClick)
}

// ---------------------------- Carrusel vertical ----------------------------

// Posición continua del carrusel (en "pistas"): 0.0 = primera, 1.0 = segunda, etc. Es infinito: da la vuelta.
// La posición se guarda en un estado que solo se lee al colocar y dibujar, así que no recompone la pantalla.
private class CarouselState(initial: Float) {
    var pos by mutableFloatStateOf(initial)
        private set
    private val anim = Animatable(initial)
    private var job: Job? = null

    fun dragBy(items: Float) { pos += items }
    fun cancel() { job?.cancel(); job = null }
    fun jump(target: Float) { cancel(); pos = target }
    fun settle(scope: CoroutineScope, target: Float, velocity: Float) {
        cancel()
        job = scope.launch {
            anim.snapTo(pos)
            anim.animateTo(target, spring(dampingRatio = 0.82f, stiffness = 140f), velocity) { pos = value }
            pos = target
        }
    }
}

@Composable
private fun NowPlayingScreen(state: PlayerState, onOpenLibrary: () -> Unit) {
    val ctx = LocalContext.current
    val favs = remember { Favorites(ctx) }

    if (state.item == null) {
        Box(Modifier.fillMaxSize().background(PlayerBg).statusBarsPadding(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(R.drawable.home_logo),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(LogoGold),
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.width(96.dp)
                )
                Spacer(Modifier.height(28.dp))
                Text("No hay nada reproduciéndose", color = Muted, fontSize = 15.sp)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onOpenLibrary, colors = ButtonDefaults.buttonColors(containerColor = Light)) {
                    Text("Abrir biblioteca", color = Color.Black)
                }
            }
        }
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(PlayerBg)) {
        PlayerScreen(state, favs, maxWidth, maxHeight)
    }
}

@Composable
private fun PlayerScreen(state: PlayerState, favs: Favorites, screenW: Dp, screenH: Dp) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val n = state.queue.size
    val md = state.item?.mediaMetadata
    val mediaId = state.item?.mediaId ?: ""
    val isFav = mediaId in favs.ids

    // Calidad del archivo
    val probed = rememberAudioInfo(state.item?.mediaId?.toLongOrNull(), null)
    val live = state.format
    val liveMime = live?.sampleMimeType
    val info: AudioInfo? = if (live != null && liveMime != null)
        AudioInfo(codecName(liveMime), losslessMime(liveMime) ?: false, live.sampleRate, probed?.bits, live.bitrate)
    else probed
    val q = info?.quality
    val hiRes = info?.hiRes == true
    val infoState = InfoState(
        if (info != null) qualityLine(info) else "",
        when (q) {
            Quality.HIRES -> InfoKind.GOLD
            Quality.LOSSLESS -> InfoKind.SILVER
            else -> InfoKind.GRAY
        }
    )

    // Carrusel y su sincronía con el reproductor
    val carousel = remember { CarouselState(state.index.coerceAtLeast(0).toFloat()) }
    var commanded by remember { mutableIntStateOf(state.index) }
    var forward by remember { mutableStateOf(true) }

    // Cambios que no vienen del dedo (termina una canción, notificación...): el carrusel se mueve solo
    LaunchedEffect(state.index, n) {
        if (n > 1 && state.index != commanded) {
            commanded = state.index
            val cur = Math.floorMod(round(carousel.pos).toInt(), n)
            val d = Math.floorMod(state.index - cur + n / 2, n) - n / 2
            forward = d >= 0
            val target = round(carousel.pos) + d
            if (abs(d) <= 3) carousel.settle(scope, target, 0f) else carousel.jump(target)
        }
    }

    // Al soltar el dedo: se cambia de pista y el carrusel se asienta con un rebote suave
    val onSettle: (Int) -> Unit = { targetAbs ->
        if (n > 1) {
            val idx = Math.floorMod(targetAbs, n)
            forward = targetAbs >= round(carousel.pos).toInt()
            if (idx != state.index) {
                commanded = idx
                state.jumpTo(idx)
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    // Pausa: la portada de adelante se encoge un poco
    val pauseScale = animateFloatAsState(
        if (state.isPlaying) 1f else 0.93f,
        spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "pauseScale"
    )
    // Destello del icono al tocar la portada
    val pulseAlpha = remember { Animatable(0f) }
    val pulseScale = remember { Animatable(0.8f) }
    var pulsePlay by remember { mutableStateOf(false) }
    val onTapFront: () -> Unit = {
        pulsePlay = !state.isPlaying
        state.togglePlay()
        scope.launch {
            pulseAlpha.snapTo(1f)
            pulseScale.snapTo(0.8f)
            launch { pulseScale.animateTo(1.3f, tween(450, easing = FastOutSlowInEasing)) }
            pulseAlpha.animateTo(0f, tween(450))
        }
    }

    // El logo solo aparece cuando el carrusel está quieto
    val settled by remember { derivedStateOf { abs(carousel.pos - round(carousel.pos)) < 0.03f } }

    val coverS = screenW * 0.79f
    val cy = coverS * 0.95f
    val carouselH = screenH * 0.74f

    Box(Modifier.fillMaxSize()) {
        // Carrusel de portadas: la de adelante es la que suena
        Box(Modifier.fillMaxWidth().height(carouselH)) {
            CoverStack(state.queue, carousel, pauseScale, onSettle, onTapFront, scope, Modifier.fillMaxSize())
            // Fundido inferior hacia el negro
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(carouselH * 0.25f)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            )
            // Logo Hi-Res en la esquina de la portada
            HiResCornerLogo(
                visible = hiRes && settled,
                coverSize = coverS,
                modifier = Modifier.offset(x = (screenW - coverS) / 2, y = cy - coverS / 2)
                    .graphicsLayer { scaleX = pauseScale.value; scaleY = pauseScale.value }
            )
            // Destello de pausa o reproducción
            Box(
                Modifier.offset(x = screenW / 2 - 38.dp, y = cy - 38.dp).size(76.dp)
                    .graphicsLayer {
                        alpha = pulseAlpha.value
                        scaleX = pulseScale.value
                        scaleY = pulseScale.value
                    }
                    .clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (pulsePlay) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, null,
                    tint = Color.White, modifier = Modifier.size(40.dp)
                )
            }
        }

        // Datos, título y barra
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            QualityInfo(infoState)
            Spacer(Modifier.height(4.dp))
            val text = TrackText(md?.title?.toString() ?: "", md?.artist?.toString() ?: "", md?.albumTitle?.toString() ?: "")
            Box(Modifier.fillMaxWidth().padding(horizontal = 36.dp)) {
                AnimatedContent(
                    targetState = text,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp),
                    transitionSpec = {
                        val dir = if (forward) 1 else -1
                        (fadeIn(tween(520, delayMillis = 120)) +
                            slideInVertically(tween(520, easing = FastOutSlowInEasing)) { h -> dir * h / 3 }) togetherWith
                            (fadeOut(tween(260)) + slideOutVertically(tween(260)) { h -> -dir * h / 3 })
                    },
                    label = "text"
                ) { t ->
                    val titleSize = when {
                        t.title.length <= 16 -> 27.sp
                        t.title.length <= 24 -> 23.sp
                        else -> 20.sp
                    }
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            t.title, color = Color.White, fontSize = titleSize, fontWeight = FontWeight.Bold,
                            lineHeight = 31.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
                        )
                        Text(
                            t.artist, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold,
                            lineHeight = 25.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
                        )
                        Text(
                            t.album, color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp,
                            lineHeight = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
                        )
                    }
                }
                IconButton(onClick = { favs.toggle(mediaId) }, modifier = Modifier.align(Alignment.TopEnd)) {
                    Icon(
                        if (isFav) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder, null,
                        tint = if (isFav) Color(0xFFF3D468) else Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            ProgressBar(state)
            Spacer(Modifier.height(8.dp))
            // Aleatorio y repetir: solo aparecen en pausa
            PausedControls(!state.isPlaying, state)
        }
    }
}

// Pila vertical de portadas, estilo coverflow: la de adelante es grande y las vecinas se achican, se oscurecen
// y quedan detrás. Se coloca con una Layout propia para leer la posición solo al colocar y dibujar.
@Composable
private fun CoverStack(
    queue: List<MediaItem>,
    carousel: CarouselState,
    pauseScale: State<Float>,
    onSettle: (Int) -> Unit,
    onTapFront: () -> Unit,
    scope: CoroutineScope,
    modifier: Modifier
) {
    val n = queue.size
    if (n == 0) return
    val settleU = rememberUpdatedState(onSettle)
    val tapU = rememberUpdatedState(onTapFront)
    val base by remember { derivedStateOf { floor(carousel.pos).toInt() } }
    val count = if (n >= 2) 10 else 1
    val first = if (n >= 2) base - 4 else 0

    Layout(
        modifier = modifier.pointerInput(n) {
            val tracker = VelocityTracker()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                carousel.cancel()
                tracker.resetTracking()
                tracker.addPosition(down.uptimeMillis, down.position)
                val s = size.width * 0.79f
                val pitch = 0.392f * s
                var moved = 0f
                val ok = verticalDrag(down.id) { change ->
                    tracker.addPosition(change.uptimeMillis, change.position)
                    val dy = change.positionChange().y
                    moved += abs(dy)
                    if (n > 1) carousel.dragBy(-dy / pitch)
                    change.consume()
                }
                if (moved < viewConfiguration.touchSlop) {
                    // Toque: pausa o reanuda si fue sobre la portada de adelante
                    val p = down.position
                    if (abs(p.x - size.width / 2f) <= s / 2f && abs(p.y - s * 0.95f) <= s / 2f) tapU.value()
                    if (n > 1) {
                        val target = round(carousel.pos)
                        settleU.value(target.toInt())
                        carousel.settle(scope, target, 0f)
                    }
                } else if (n > 1) {
                    val v = if (ok) -tracker.calculateVelocity().y / pitch else 0f
                    val c0 = round(carousel.pos)
                    val target = round(carousel.pos + v * 0.22f).coerceIn(c0 - 6f, c0 + 6f)
                    settleU.value(target.toInt())
                    carousel.settle(scope, target, v)
                }
            }
        },
        content = {
            for (i in first until first + count) {
                key(i) { StackCover(queue[Math.floorMod(i, n)], i, carousel, pauseScale) }
            }
        }
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val s = (w * 0.79f).roundToInt()
        val cyPx = s * 0.95f
        val placeables = measurables.map { it.measure(Constraints.fixed(s, s)) }
        layout(w, h) {
            val pos = carousel.pos
            val ps = pauseScale.value
            placeables.forEachIndexed { idx, pl ->
                val p = (first + idx) - pos
                val ap = abs(p)
                if (ap < 4.8f) {
                    val sc = 1f / (1f + 0.38f * ap)
                    val dy = sign(p) * s * (0.392f * ap / (1f + 0.1536f * ap))
                    val front = max(0f, 1f - ap)
                    val k = sc * (1f - (1f - ps) * front)
                    pl.placeWithLayer((w - s) / 2, (cyPx - s / 2f + dy).roundToInt(), -ap) {
                        scaleX = k
                        scaleY = k
                        alpha = ((4.8f - ap) / 1.4f).coerceIn(0f, 1f)
                    }
                }
            }
        }
    }
}

@Composable
private fun StackCover(item: MediaItem, index: Int, carousel: CarouselState, pauseScale: State<Float>) {
    val uri = item.mediaId.toLongOrNull()?.let { ContentUris.withAppendedId(audioCollection(), it) }
    val bmp = rememberArt(uri, 800)
    Box(
        Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)).background(Panel)
            .drawWithContent {
                drawContent()
                val ap = abs(index - carousel.pos)
                // Las vecinas se oscurecen con la distancia; en pausa la de adelante también baja un poco
                val paused = (1f - pauseScale.value) / 0.07f * 0.18f * max(0f, 1f - ap)
                val dim = (0.28f * min(ap, 3.2f) + paused).coerceIn(0f, 0.9f)
                if (dim > 0.005f) drawRect(Color.Black.copy(alpha = dim))
            }
    ) {
        if (bmp != null) {
            Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.MusicNote, null, tint = Muted, modifier = Modifier.size(64.dp))
            }
        }
    }
}

// Logo Hi-Res en la esquina inferior izquierda de la portada de adelante: entra suave y sale suave
@Composable
private fun HiResCornerLogo(visible: Boolean, coverSize: Dp, modifier: Modifier) {
    Box(modifier.size(coverSize)) {
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomStart)
                .padding(start = coverSize * 0.045f, bottom = coverSize * 0.052f),
            enter = fadeIn(tween(600, easing = FastOutSlowInEasing)) +
                scaleIn(
                    initialScale = 0.6f,
                    animationSpec = tween(650, easing = FastOutSlowInEasing),
                    transformOrigin = TransformOrigin(0f, 1f)
                ) +
                slideInVertically(tween(650, easing = FastOutSlowInEasing)) { it / 3 },
            exit = fadeOut(tween(260)) +
                scaleOut(targetScale = 0.85f, animationSpec = tween(260), transformOrigin = TransformOrigin(0f, 1f))
        ) {
            Image(
                painter = painterResource(R.drawable.hires_logo),
                contentDescription = null,
                modifier = Modifier.size(coverSize * 0.126f)
            )
        }
    }
}

// ---------------------------- Información de calidad ----------------------------

private fun qualityLine(info: AudioInfo): String {
    val sr = info.sampleRate
    val rate = when {
        sr <= 0 -> null
        sr >= 100000 -> "${(sr / 1000.0).roundToInt()} kHz"
        else -> rateText(sr)
    }
    val bitsText = info.bits?.takeIf { it > 0 && info.lossless }?.let { "$it bit" }
    val tail = listOfNotNull(rate, bitsText).joinToString(" / ")
    return listOf(info.codec.takeIf { it != "—" } ?: "", tail).filter { it.isNotEmpty() }.joinToString(" ")
}

// Oro (HiRes) y plata (calidad CD) aparecen con un fundido y un leve ascenso; con pérdida, solo gris y sin animación
@Composable
private fun QualityInfo(info: InfoState) {
    Box(Modifier.height(18.dp), contentAlignment = Alignment.Center) {
        AnimatedContent(
            targetState = info,
            transitionSpec = {
                if (targetState.kind == InfoKind.GRAY) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    (fadeIn(tween(600, delayMillis = 150)) +
                        slideInVertically(tween(600, easing = FastOutSlowInEasing)) { it / 2 }) togetherWith
                        fadeOut(tween(250))
                }
            },
            label = "quality"
        ) { st ->
            when (st.kind) {
                InfoKind.GOLD -> Text(
                    st.line,
                    style = TextStyle(brush = Brush.horizontalGradient(GoldStops), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                )
                InfoKind.SILVER -> Text(
                    st.line,
                    style = TextStyle(brush = Brush.horizontalGradient(SilverStops), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                )
                InfoKind.GRAY -> Text(st.line, color = Color(0xFF8E8E93), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

// ---------------------------- Barra de progreso ----------------------------

// Solo esta función se actualiza con la posición; el resto de la pantalla no se recompone
@Composable
private fun ProgressBar(state: PlayerState) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val dur = state.durationMs.coerceAtLeast(1L)
    val target = (drag ?: (state.positionMs.toFloat() / dur)).coerceIn(0f, 1f)
    val frac = animateFloatAsState(
        target,
        if (drag != null) snap<Float>() else tween<Float>(260, easing = LinearEasing),
        label = "frac"
    )
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        DragTimeLabel(drag != null, "${fmt((target * dur).toLong())} / ${fmt(state.durationMs)}")
        ProgressTrack(frac, onDrag = { drag = it }, onSeek = { state.seekToFraction(it) })
    }
}

// Tiempo que aparece solo mientras se arrastra la barra
@Composable
private fun DragTimeLabel(visible: Boolean, text: String) {
    Box(Modifier.fillMaxWidth().height(18.dp), contentAlignment = Alignment.Center) {
        AnimatedVisibility(visible = visible, enter = fadeIn(tween(150)), exit = fadeOut(tween(250))) {
            Text(text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

private fun seekFraction(x: Float, width: Int, pad: Float): Float =
    if (width <= 2 * pad) 0f else ((x - pad) / (width - 2 * pad)).coerceIn(0f, 1f)

@Composable
private fun ProgressTrack(frac: State<Float>, onDrag: (Float?) -> Unit, onSeek: (Float) -> Unit) {
    Canvas(
        Modifier.fillMaxWidth(0.59f).height(24.dp)
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
        val y = size.height / 2
        val x = pad + w * frac.value
        drawLine(Color(0xFF7D7D7D), Offset(pad, y), Offset(pad + w, y), strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
        drawLine(Color.White, Offset(pad, y), Offset(x, y), strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(Color.White, radius = 6.5.dp.toPx(), center = Offset(x, y))
    }
}

// ---------------------------- Aleatorio y repetir (solo en pausa) ----------------------------

@Composable
private fun PausedControls(visible: Boolean, state: PlayerState) {
    Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(350)) +
                slideInVertically(tween(450, easing = FastOutSlowInEasing)) { it / 2 } +
                scaleIn(initialScale = 0.9f, animationSpec = tween(450, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(220)) + slideOutVertically(tween(300, easing = FastOutSlowInEasing)) { it / 3 }
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                ControlIcon(
                    Icons.Outlined.Shuffle, 30.dp,
                    if (state.shuffle) Color.White else Color.White.copy(alpha = 0.6f)
                ) { state.toggleShuffle() }
                ControlIcon(
                    if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Outlined.RepeatOne else Icons.Outlined.Repeat, 30.dp,
                    if (state.repeatMode == Player.REPEAT_MODE_OFF) Color.White.copy(alpha = 0.6f) else Color.White
                ) { state.cycleRepeat() }
            }
        }
    }
}

// Icono de control sin marco, con rebote al tocarlo
@Composable
private fun ControlIcon(icon: ImageVector, size: Dp, tint: Color, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).pressable(onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size))
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
    val rate = rateText(info.sampleRate)
    if (!info.lossless) {
        return listOfNotNull(rate, info.codec.takeIf { it != "—" }).joinToString(" / ")
    }
    val bitsText = info.bits?.takeIf { it > 0 }?.let { "$it bit" }
    return (listOfNotNull(rate, bitsText).joinToString(" / ") + " " + info.codec).trim()
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

private val artCache = object : LruCache<String, ImageBitmap>(48 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
}

@Composable
private fun rememberArt(uri: Uri?, px: Int): ImageBitmap? {
    val ctx = LocalContext.current
    return produceState<ImageBitmap?>(uri?.let { artCache.get("$it@$px") }, uri, px) {
        value = if (uri == null) null else {
            val key = "$uri@$px"
            val cached = artCache.get(key)
            if (cached != null) cached else {
                val loaded = withContext(Dispatchers.IO) { loadArt(ctx, uri, px) }
                if (loaded != null) artCache.put(key, loaded)
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

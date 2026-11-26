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

private val PlayerBg = Color(0xFF060606)
private val SoftGray = Color(0xFF9B9B9B)
private val InfoGold = Color(0xFFF3D468)
private val PillFill = Color(0xFF9A6334)
private val PillBorder = Color(0xFFE8A65C)
private val PillText = Color(0xFFF6DA83)
private val BannerOrange = Color(0xFFF0A957)
private val LogoGold = Color(0xFFD4B14E)

private data class TrackText(val title: String, val artist: String, val album: String)

private fun Color.scaled(f: Float): Color = Color(red * f, green * f, blue * f, 1f)

// Color medio de la parte baja de la portada: de ahí sale el degradado en el que se funde
private fun coverEdgeColor(art: ImageBitmap?): Color {
    if (art == null) return Color(0xFF1A1A1A)
    return runCatching {
        var bmp = art.asAndroidBitmap()
        if (bmp.config == Bitmap.Config.HARDWARE) bmp = bmp.copy(Bitmap.Config.ARGB_8888, false)
        val h = bmp.height
        val top = (h * 0.72f).toInt()
        val band = Bitmap.createBitmap(bmp, 0, top, bmp.width, (h - top).coerceAtLeast(1))
        Color(Bitmap.createScaledBitmap(band, 1, 1, true).getPixel(0, 0))
    }.getOrDefault(Color(0xFF1A1A1A))
}

// Pulsación con efecto de rebote suave
@Composable
private fun Modifier.pressable(onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.88f else 1f,
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "press"
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = src, indication = null, onClick = onClick)
}

@Composable
private fun NowPlayingScreen(state: PlayerState, onOpenLibrary: () -> Unit) {
    val ctx = LocalContext.current
    val uri = state.item?.mediaId?.toLongOrNull()?.let { ContentUris.withAppendedId(audioCollection(), it) }
    val art = rememberArt(uri, 1000)
    val bits = rememberBitDepth(uri)
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
        ImmersivePlayer(state, art, bits, favs, maxHeight)
    }
}

@Composable
private fun ImmersivePlayer(state: PlayerState, art: ImageBitmap?, bits: Int?, favs: Favorites, screenH: Dp) {
    val md = state.item?.mediaMetadata
    val mediaId = state.item?.mediaId ?: ""
    val isFav = mediaId in favs.ids
    val probed = rememberAudioInfo(state.item?.mediaId?.toLongOrNull(), null)
    val live = state.format
    val liveMime = live?.sampleMimeType
    val info: AudioInfo? = if (live != null && liveMime != null)
        AudioInfo(codecName(liveMime), losslessMime(liveMime) ?: false, live.sampleRate, probed?.bits ?: bits, live.bitrate)
    else probed
    val q = info?.quality
    val premium = q == Quality.HIRES || q == Quality.LOSSLESS
    val hiRes = info?.hiRes == true

    var drag by remember { mutableStateOf<Float?>(null) }
    val dur = state.durationMs.coerceAtLeast(1L)
    val frac = (drag ?: (state.positionMs.toFloat() / dur)).coerceIn(0f, 1f)
    // La barra avanza sin saltos entre actualizaciones; al arrastrar sigue al dedo
    val animFrac by animateFloatAsState(
        frac,
        if (drag != null) snap<Float>() else tween<Float>(260, easing = LinearEasing),
        label = "frac"
    )

    // Aviso "Disponible en Alta Resolución": aparece unos segundos y se oculta
    var showBanner by remember { mutableStateOf(false) }
    LaunchedEffect(mediaId, hiRes) {
        if (hiRes) {
            delay(600)
            showBanner = true
            delay(4200)
            showBanner = false
        } else {
            showBanner = false
        }
    }

    // Sentido del cambio de pista: siguiente (true) o anterior (false)
    var prevIndex by remember { mutableIntStateOf(state.index) }
    val forward = state.index >= prevIndex
    SideEffect { prevIndex = state.index }

    // Animaciones suaves: color del fondo, acercamiento al reproducir y atenuación al pausar
    val edge = remember(art) { coverEdgeColor(art) }
    val bg by animateColorAsState(edge, tween(900), label = "bg")
    val playZoom by animateFloatAsState(
        if (state.isPlaying) 1.03f else 1f,
        spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
        label = "zoom"
    )
    val dim by animateFloatAsState(if (state.isPlaying) 0f else 0.22f, tween(450), label = "dim")

    Box(Modifier.fillMaxSize()) {
        // Fondo: el color de la portada se oscurece hacia abajo
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.48f to bg,
                    0.58f to bg.scaled(0.72f),
                    0.68f to bg.scaled(0.50f),
                    0.78f to bg.scaled(0.26f),
                    0.90f to bg.scaled(0.09f),
                    1f to Color(0xFF050505)
                )
            )
        )

        // Portada a todo el ancho, fundida por abajo con el fondo
        Box(
            Modifier.fillMaxWidth().height(screenH * 0.55f)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Black,
                            0.60f to Color.Black,
                            0.88f to Color.Black.copy(alpha = 0.5f),
                            1f to Color.Transparent
                        ),
                        blendMode = BlendMode.DstIn
                    )
                }
        ) {
            AnimatedContent(
                targetState = art,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    (fadeIn(tween(600, easing = FastOutSlowInEasing)) +
                        slideInHorizontally(tween(600, easing = FastOutSlowInEasing)) { w -> if (forward) w / 8 else -w / 8 }) togetherWith
                        (fadeOut(tween(600)) +
                            slideOutHorizontally(tween(600, easing = FastOutSlowInEasing)) { w -> if (forward) -w / 8 else w / 8 })
                },
                label = "cover"
            ) { a ->
                if (a != null) {
                    Image(
                        a, null,
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            scaleX = playZoom
                            scaleY = playZoom
                            transformOrigin = TransformOrigin(0.5f, 0f)
                        },
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(Panel), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.MusicNote, null, tint = Muted, modifier = Modifier.size(96.dp))
                    }
                }
            }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
            Box(
                Modifier.fillMaxWidth().height(110.dp)
                    .background(Brush.verticalGradient(listOf(Color(0x40000000), Color.Transparent)))
            )
        }

        // Información, barra y controles sobre el degradado
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Etiqueta de calidad (HiRes o Lossless; con pérdida no hay etiqueta)
            Box(Modifier.height(20.dp), contentAlignment = Alignment.Center) {
                Crossfade(targetState = q, animationSpec = tween(400), label = "badge") { qq ->
                    when (qq) {
                        Quality.HIRES -> QualityPill("HiRes")
                        Quality.LOSSLESS -> QualityPill("Lossless")
                        else -> Spacer(Modifier.height(1.dp))
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            // Datos del archivo
            val line = if (info != null) infoLine(info) else ""
            Box(Modifier.height(16.dp), contentAlignment = Alignment.Center) {
                Crossfade(targetState = line, animationSpec = tween(400), label = "info") { l ->
                    Text(
                        l, color = if (premium) InfoGold else Color(0xFFB5B5B5),
                        fontSize = 11.sp, fontWeight = FontWeight.Medium
                    )
                }
            }
            Spacer(Modifier.height(2.dp))

            // Título, artista y álbum (con corazón de favoritos)
            val text = TrackText(md?.title?.toString() ?: "", md?.artist?.toString() ?: "", md?.albumTitle?.toString() ?: "")
            Box(Modifier.fillMaxWidth().padding(horizontal = 36.dp)) {
                AnimatedContent(
                    targetState = text,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp),
                    transitionSpec = {
                        (fadeIn(tween(520, delayMillis = 120)) +
                            slideInHorizontally(tween(520, easing = FastOutSlowInEasing)) { w -> if (forward) w / 10 else -w / 10 }) togetherWith
                            (fadeOut(tween(260)) +
                                slideOutHorizontally(tween(260)) { w -> if (forward) -w / 10 else w / 10 })
                    },
                    label = "text"
                ) { t ->
                    val titleSize = when {
                        t.title.length <= 16 -> 30.sp
                        t.title.length <= 24 -> 25.sp
                        else -> 21.sp
                    }
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            t.title, color = Color.White, fontSize = titleSize, fontWeight = FontWeight.Bold,
                            lineHeight = 34.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
                        )
                        Text(
                            t.artist, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                            lineHeight = 26.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
                        )
                        Text(
                            t.album, color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp,
                            lineHeight = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
                        )
                    }
                }
                IconButton(onClick = { favs.toggle(mediaId) }, modifier = Modifier.align(Alignment.TopEnd)) {
                    Icon(
                        if (isFav) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder, null,
                        tint = if (isFav) InfoGold else Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Al arrastrar la barra se muestra el tiempo; en reposo no hay números
            Box(Modifier.fillMaxWidth().height(18.dp), contentAlignment = Alignment.Center) {
                AnimatedVisibility(visible = drag != null, enter = fadeIn(tween(150)), exit = fadeOut(tween(250))) {
                    Text(
                        "${fmt((frac * dur).toLong())} / ${fmt(state.durationMs)}",
                        color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium
                    )
                }
            }
            SeekBar(animFrac, onDrag = { drag = it }, onSeek = { state.seekToFraction(it) })

            // Controles
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(0.62f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ControlIcon(
                    Icons.Rounded.Shuffle, 30.dp,
                    if (state.shuffle) Color.White else Color.White.copy(alpha = 0.6f)
                ) { state.toggleShuffle() }
                ControlIcon(Icons.Rounded.FastRewind, 40.dp, Color.White, nudge = -10f) { state.previous() }
                PlayPause(state.isPlaying) { state.togglePlay() }
                ControlIcon(Icons.Rounded.FastForward, 40.dp, Color.White, nudge = 10f) { state.next() }
                ControlIcon(
                    if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, 30.dp,
                    if (state.repeatMode == Player.REPEAT_MODE_OFF) Color.White.copy(alpha = 0.6f) else Color.White
                ) { state.cycleRepeat() }
            }

            // Aviso de alta resolución
            Spacer(Modifier.height(10.dp))
            HiResBanner(showBanner)
        }
    }
}

private fun infoLine(info: AudioInfo): String {
    val rate = rateText(info.sampleRate)
    val bitsText = info.bits?.takeIf { it > 0 && info.lossless }?.let { "$it bits" }
    val tail = listOfNotNull(rate, bitsText).joinToString(" / ")
    return listOf(info.codec.takeIf { it != "—" } ?: "", tail).filter { it.isNotEmpty() }.joinToString(" ")
}

@Composable
private fun QualityPill(text: String) {
    val shape = RoundedCornerShape(6.dp)
    Text(
        text, color = PillText, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        maxLines = 1, softWrap = false,
        modifier = Modifier.clip(shape).background(PillFill)
            .border(1.5.dp, PillBorder, shape)
            .padding(horizontal = 9.dp, vertical = 2.dp)
    )
}

// Aviso que entra desde abajo con un fundido y un leve zoom, y se retira solo
@Composable
private fun HiResBanner(visible: Boolean) {
    Box(Modifier.fillMaxWidth().height(36.dp), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(450)) +
                slideInVertically(tween(550, easing = FastOutSlowInEasing)) { it } +
                scaleIn(initialScale = 0.9f, animationSpec = tween(550, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(380)) +
                slideOutVertically(tween(450, easing = FastOutSlowInEasing)) { it / 2 } +
                scaleOut(targetScale = 0.95f, animationSpec = tween(380))
        ) {
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(BannerOrange)
                    .padding(horizontal = 18.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("💿", fontSize = 15.sp)
                Spacer(Modifier.width(8.dp))
                Text("Disponible en Alta Resolución", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// Icono de control sin marco. Los de anterior y siguiente dan un pequeño empujón al tocarlos.
@Composable
private fun ControlIcon(icon: ImageVector, size: Dp, tint: Color, nudge: Float = 0f, onClick: () -> Unit) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current.density
    val offset = remember { Animatable(0f) }
    Box(
        Modifier.size(48.dp).pressable {
            onClick()
            if (nudge != 0f) {
                scope.launch {
                    offset.animateTo(nudge * density, tween(110))
                    offset.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
                }
            }
        },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size).graphicsLayer { translationX = offset.value })
    }
}

// Pausa y reproducir: el icono cambia con un fundido y un pequeño zoom
@Composable
private fun PlayPause(playing: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(56.dp).pressable(onClick), contentAlignment = Alignment.Center) {
        AnimatedContent(
            targetState = playing,
            transitionSpec = {
                (fadeIn(tween(260)) + scaleIn(initialScale = 0.5f, animationSpec = tween(260, easing = FastOutSlowInEasing))) togetherWith
                    (fadeOut(tween(160)) + scaleOut(targetScale = 0.5f, animationSpec = tween(160)))
            },
            label = "playpause"
        ) { p ->
            if (p) {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    repeat(2) {
                        Box(Modifier.size(width = 8.dp, height = 28.dp).clip(RoundedCornerShape(2.dp)).background(Color.White))
                    }
                }
            } else {
                Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(48.dp))
            }
        }
    }
}

private fun seekFraction(x: Float, width: Int, pad: Float): Float =
    if (width <= 2 * pad) 0f else ((x - pad) / (width - 2 * pad)).coerceIn(0f, 1f)

// Barra gruesa y redondeada, como en la captura
@Composable
private fun SeekBar(frac: Float, onDrag: (Float?) -> Unit, onSeek: (Float) -> Unit) {
    Canvas(
        Modifier.fillMaxWidth().padding(horizontal = 50.dp).height(24.dp)
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
        val x = pad + w * frac
        drawLine(Color(0xFF6B7078), Offset(pad, y), Offset(pad + w, y), strokeWidth = 7.dp.toPx(), cap = StrokeCap.Round)
        drawLine(Color.White, Offset(pad, y), Offset(x, y), strokeWidth = 7.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(Color.White, radius = 7.dp.toPx(), center = Offset(x, y))
    }
}

// Pista de la lista (se usará cuando vuelva la lista a esta pantalla)
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueueRow(item: MediaItem, current: Boolean, onPlay: () -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 1.5.dp)) {
        Text(
            item.mediaMetadata.title?.toString() ?: "", color = Color.White, fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (current) Color(0xFF262628) else Color.Transparent)
                .combinedClickable(onClick = onPlay, onLongClick = { menu = true })
                .padding(horizontal = 13.dp, vertical = 9.dp)
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Reproducir") }, onClick = { menu = false; onPlay() })
            DropdownMenuItem(text = { Text("Quitar de la cola") }, onClick = { menu = false; onRemove() })
        }
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

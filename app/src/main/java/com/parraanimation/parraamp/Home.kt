package com.parraanimation.parraamp

import android.content.ContentUris
import android.content.Context
import android.util.LruCache
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val HomeBg = Color(0xFF1F1F1F)
private val HomeGold = Color(0xFFF7DE6F)
private val TileBg = Color(0xFF2A2A2A)

// Una portada candidata: un álbum, representado por una de sus canciones
private data class CoverItem(val key: Long, val trackId: Long, val year: Int)
private data class Shown(val item: CoverItem, val bitmap: ImageBitmap)
private class TileSpec(val label: String, val pool: List<CoverItem>, val showYear: Boolean, val onClick: () -> Unit)

private fun coverKey(t: Track): Long = if (t.albumId > 0) t.albumId else -t.id

// Una portada por álbum, para no repetir la misma imagen aunque el álbum tenga muchas canciones
private fun buildCoverItems(tracks: List<Track>): List<CoverItem> =
    tracks.groupBy { coverKey(it) }.map { (k, ts) ->
        CoverItem(k, ts.first().id, ts.firstOrNull { it.year > 0 }?.year ?: 0)
    }

private val noArtKeys: MutableSet<Long> = java.util.Collections.synchronizedSet(HashSet<Long>())

private val tileCache = object : LruCache<Long, ImageBitmap>(32 * 1024 * 1024) {
    override fun sizeOf(key: Long, value: ImageBitmap): Int = value.width * value.height * 4
}

private suspend fun coverBitmap(ctx: Context, c: CoverItem): ImageBitmap? {
    val hit = tileCache.get(c.key)
    if (hit != null) return hit
    val uri = ContentUris.withAppendedId(audioCollection(), c.trackId)
    val bmp = withContext(Dispatchers.IO) { loadArt(ctx, uri, 520) }
    if (bmp != null) tileCache.put(c.key, bmp)
    return bmp
}

// Elige la siguiente portada: al azar, sin repetir hasta agotar el mazo y sin coincidir con otro recuadro
private suspend fun pickCover(
    ctx: Context, pool: List<CoverItem>, deck: ArrayDeque<CoverItem>,
    used: Set<Long>, currentKey: Long?
): Shown? {
    val strict = pool.size >= 10
    var tries = 0
    while (tries < 60) {
        tries++
        if (deck.isEmpty()) deck.addAll(pool.shuffled())
        val c = deck.removeFirst()
        if (pool.size > 1 && c.key == currentKey) continue
        if (strict && c.key in used) continue
        if (c.key in noArtKeys) continue
        val bmp = coverBitmap(ctx, c)
        if (bmp == null) {
            noArtKeys.add(c.key)
            continue
        }
        return Shown(c, bmp)
    }
    return null
}

@Composable
fun HomeScreen(
    state: PlayerState,
    tracks: List<Track>,
    onAllSongs: () -> Unit,
    onQueue: () -> Unit,
    onRandomFm: () -> Unit,
    onSoon: (String) -> Unit
) {
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val all = remember(tracks) { buildCoverItems(tracks) }
    val years = remember(all) { all.filter { it.year > 0 }.ifEmpty { all } }
    val keyByTrack = remember(tracks) { tracks.associate { it.id to coverKey(it) } }
    val queueKeys = state.queue.mapNotNull { q -> q.mediaId.toLongOrNull()?.let { id -> keyByTrack[id] } }
    val queuePool = remember(queueKeys, all) {
        val ks = queueKeys.toSet()
        all.filter { it.key in ks }.ifEmpty { all }
    }
    val used = remember { mutableSetOf<Long>() }

    val tiles = listOf(
        TileSpec("Todas las canciones", all, false, onAllSongs),
        TileSpec("Artistas", all, false) { onSoon("Artistas") },
        TileSpec("Géneros", all, false) { onSoon("Géneros") },
        TileSpec("Años", years, true) { onSoon("Años") },
        TileSpec("Música en cola", queuePool, false, onQueue),
        TileSpec("Lista personalizada", all, false) { onSoon("Lista personalizada") },
        TileSpec("Aleatorio FM", all, false, onRandomFm)
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().background(HomeBg),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 24.dp + navBottom),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Image(
                painter = painterResource(R.drawable.home_logo),
                contentDescription = null,
                colorFilter = ColorFilter.tint(HomeGold),
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.statusBarsPadding()
                    .padding(start = 8.dp, top = 16.dp, bottom = 40.dp)
                    .width(80.dp)
            )
        }
        itemsIndexed(tiles, key = { _, t -> t.label }) { i, t ->
            CoverTile(t.label, t.pool, used, t.showYear, i, t.onClick)
        }
    }
}

@Composable
private fun CoverTile(
    label: String, pool: List<CoverItem>, used: MutableSet<Long>,
    showYear: Boolean, slot: Int, onClick: () -> Unit
) {
    val ctx = LocalContext.current
    var shown by remember { mutableStateOf<Shown?>(null) }

    LaunchedEffect(pool) {
        if (pool.isEmpty()) {
            shown = null
            return@LaunchedEffect
        }
        delay(200L + slot * 450L)
        val deck = ArrayDeque<CoverItem>()
        while (true) {
            val old = shown
            val next = pickCover(ctx, pool, deck, used, old?.item?.key)
            if (next != null) {
                if (old != null) used.remove(old.item.key)
                used.add(next.item.key)
                shown = next
            }
            delay(5200L + (slot * 637L) % 2600L)
        }
    }
    DisposableEffect(Unit) {
        onDispose { shown?.let { used.remove(it.item.key) } }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp)).background(TileBg)
                .clickable(onClick = onClick)
        ) {
            if (shown == null) {
                Icon(
                    Icons.Rounded.MusicNote, null, tint = Color(0xFF555555),
                    modifier = Modifier.align(Alignment.Center).size(44.dp)
                )
            }
            Crossfade(
                targetState = shown,
                animationSpec = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
                label = "cover"
            ) { s ->
                if (s != null) CoverImage(s.bitmap)
            }
            if (showYear) YearOverlay(shown?.item?.year ?: 0)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            label, color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth()
        )
    }
}

// Portada con un acercamiento muy lento mientras está visible
@Composable
private fun CoverImage(bmp: ImageBitmap) {
    val zoom = remember { Animatable(1f) }
    LaunchedEffect(Unit) { zoom.animateTo(1.07f, tween(durationMillis = 9000, easing = LinearEasing)) }
    Image(
        bmp, null,
        modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = zoom.value; scaleY = zoom.value },
        contentScale = ContentScale.Crop
    )
}

// Año de la portada visible: esquina inferior izquierda, sobre un degradado suave
@Composable
private fun BoxScope.YearOverlay(year: Int) {
    Box(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.42f)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
    )
    Crossfade(
        targetState = year,
        modifier = Modifier.align(Alignment.BottomStart),
        animationSpec = tween(durationMillis = 900),
        label = "year"
    ) { y ->
        if (y > 0) {
            Column(Modifier.padding(start = 16.dp, bottom = 14.dp)) {
                Box(Modifier.width(22.dp).height(2.dp).background(HomeGold))
                Spacer(Modifier.height(6.dp))
                Text(
                    y.toString(), color = Color.White, fontSize = 30.sp,
                    fontWeight = FontWeight.Light, letterSpacing = 1.5.sp
                )
            }
        }
    }
}

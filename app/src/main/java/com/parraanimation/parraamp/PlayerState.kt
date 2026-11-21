package com.parraanimation.parraamp

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

class PlayerState {
    private var controller: MediaController? = null
    private var future: ListenableFuture<MediaController>? = null

    var isPlaying by mutableStateOf(false); private set
    var item by mutableStateOf<MediaItem?>(null); private set
    var positionMs by mutableLongStateOf(0L); private set
    var durationMs by mutableLongStateOf(0L); private set
    var shuffle by mutableStateOf(false); private set
    var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_OFF); private set
    var format by mutableStateOf<Format?>(null); private set

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sync(player)
    }

    fun connect(ctx: Context) {
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val f = MediaController.Builder(ctx, token).buildAsync()
        future = f
        f.addListener({
            val c = runCatching { f.get() }.getOrNull()
            if (c != null) {
                controller = c
                c.addListener(listener)
                sync(c)
            }
        }, ContextCompat.getMainExecutor(ctx))
    }

    fun release() {
        controller?.removeListener(listener)
        future?.let { MediaController.releaseFuture(it) }
        controller = null
    }

    private fun sync(p: Player) {
        isPlaying = p.isPlaying
        item = p.currentMediaItem
        durationMs = if (p.duration == C.TIME_UNSET) 0L else p.duration
        positionMs = p.currentPosition
        shuffle = p.shuffleModeEnabled
        repeatMode = p.repeatMode
        format = audioFormat(p.currentTracks)
    }

    private fun audioFormat(t: Tracks): Format? {
        for (g in t.groups) {
            if (g.type == C.TRACK_TYPE_AUDIO && g.isSelected) {
                for (i in 0 until g.length) if (g.isTrackSelected(i)) return g.getTrackFormat(i)
            }
        }
        return null
    }

    fun refreshPosition() {
        val c = controller ?: return
        positionMs = c.currentPosition
        if (c.duration != C.TIME_UNSET) durationMs = c.duration
    }

    fun play(tracks: List<Track>, index: Int) {
        val c = controller ?: return
        c.setMediaItems(tracks.map { it.toMediaItem() }, index, 0L)
        c.prepare()
        c.play()
    }

    fun togglePlay() { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun next() { controller?.seekToNext() }
    fun previous() { controller?.seekToPrevious() }
    fun seekTo(ms: Long) { controller?.seekTo(ms); positionMs = ms }
    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }
}

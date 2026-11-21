package com.parraanimation.parraamp

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long
) {
    val uri: Uri get() = ContentUris.withAppendedId(audioCollection(), id)

    fun toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .build()
        )
        .build()
}

fun audioCollection(): Uri =
    if (Build.VERSION.SDK_INT >= 29) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

fun loadTracks(ctx: Context): List<Track> {
    val out = mutableListOf<Track>()
    val m = MediaStore.Audio.Media
    val projection = arrayOf(m._ID, m.TITLE, m.ARTIST, m.ALBUM, m.DURATION)
    val selection = "${m.IS_MUSIC} != 0 AND ${m.DURATION} > 20000"
    ctx.contentResolver.query(audioCollection(), projection, selection, null, "${m.TITLE} COLLATE NOCASE ASC")
        ?.use { c ->
            val iId = c.getColumnIndexOrThrow(m._ID)
            val iTitle = c.getColumnIndexOrThrow(m.TITLE)
            val iArtist = c.getColumnIndexOrThrow(m.ARTIST)
            val iAlbum = c.getColumnIndexOrThrow(m.ALBUM)
            val iDur = c.getColumnIndexOrThrow(m.DURATION)
            while (c.moveToNext()) {
                val artist = c.getString(iArtist)?.takeIf { it != "<unknown>" } ?: "Artista desconocido"
                val album = c.getString(iAlbum)?.takeIf { it != "<unknown>" } ?: "Álbum desconocido"
                out += Track(c.getLong(iId), c.getString(iTitle) ?: "Sin título", artist, album, c.getLong(iDur))
            }
        }
    return out
}

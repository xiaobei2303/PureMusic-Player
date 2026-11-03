package com.puremusic.player.data

import android.content.ContentUris
import android.net.Uri

data class Track(
    val id: Long, val title: String, val artist: String, val album: String,
    val duration: Long, val uri: Uri, val artworkUri: Uri? = null
) {
    val displayTitle: String get() = title.ifBlank { "未知歌曲" }
    val displayArtist: String get() = artist.ifBlank { "未知艺术家" }
    fun mediaId() = id.toString()
}

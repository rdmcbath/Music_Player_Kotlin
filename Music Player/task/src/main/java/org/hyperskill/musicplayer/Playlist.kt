package org.hyperskill.musicplayer

data class Playlist(
    val id: String,
    val name: String,
    val songs: List<Song>
)

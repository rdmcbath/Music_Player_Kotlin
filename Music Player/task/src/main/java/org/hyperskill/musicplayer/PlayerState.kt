package org.hyperskill.musicplayer

sealed class PlayerState {
    object PLAY_MUSIC : PlayerState()
    object ADD_PLAYLIST : PlayerState()
}
package org.hyperskill.musicplayer

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import java.io.IOException

class MediaPlayerHelper(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var onCompletionCallback: (() -> Unit)? = null

    fun isInitialized(): Boolean = mediaPlayer != null

    fun setupMediaPlayer() {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer()

        try {
            mediaPlayer?.setDataSource(
                context,
                Uri.parse("android.resource://${context.packageName}/raw/wisdom")
            )
            mediaPlayer?.prepare()

            mediaPlayer?.setOnCompletionListener {
                it.seekTo(0)
                onCompletionCallback?.invoke()
            }
        } catch (e: IOException) {
            Log.e("MediaPlayerSetup", "Error setting up MediaPlayer: $e")
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    fun setOnCompletionCallback(callback: () -> Unit) {
        onCompletionCallback = callback
        mediaPlayer?.setOnCompletionListener {
            it.seekTo(0)
            onCompletionCallback?.invoke()
        }
    }

    fun getDuration(): Int = mediaPlayer?.duration ?: 0

    fun getCurrentPosition(): Int {
        return mediaPlayer?.currentPosition?.div(1000) ?: 0  // Convert ms to seconds
    }

    fun resetMediaPlayer() {
        mediaPlayer?.seekTo(0)
        mediaPlayer?.pause()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    fun startMediaPlayer() {
        mediaPlayer?.start()
    }

    fun pauseMediaPlayer() {
        mediaPlayer?.pause()
    }

    fun stopMediaPlayer() {
        mediaPlayer?.apply {
            pause()
            seekTo(0)
        }
    }

    fun cleanup() {
        mediaPlayer?.apply {
            reset()
            release()
        }
        mediaPlayer = null
    }

    fun seekTo(positionInSeconds: Int) {
        mediaPlayer?.seekTo(positionInSeconds * 1000)  // Convert seconds to ms
    }
}
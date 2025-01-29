package org.hyperskill.musicplayer

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

class MainViewModel(context: Context) : ViewModel() {
    private var mediaPlayerHelper: MediaPlayerHelper = MediaPlayerHelper(context)
    var previousTrackId: Int? = null
    private lateinit var songListAdapter: SongListAdapter
    private lateinit var selectorAdapter: SongSelectorAdapter
    private var mediaPlayer: MediaPlayer? = null

    private val _playerState = MutableStateFlow<PlayerState>(PlayerState.PLAY_MUSIC)
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _currentPlaylist = MutableStateFlow<Playlist?>(null)

    private val _currentPosition = MutableStateFlow<Int?>(0)
    val currentPosition: StateFlow<Int?> = _currentPosition.asStateFlow()

    private val _currentTrack = MutableStateFlow<Song?>(null)
    val currentTrack: StateFlow<Song?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _songSelectors = MutableStateFlow<List<SongSelector>>(emptyList())
    val songSelectors = _songSelectors.asStateFlow()

    private val _selectedSongs = MutableStateFlow<List<Song>>(emptyList())
    val selectedSongs: StateFlow<List<Song>> = _selectedSongs.asStateFlow()

    private val _isSelectedMap = MutableStateFlow<Map<Int, Boolean>>(emptyMap())
    val isSelectedMap = _isSelectedMap.asStateFlow()

    private val _songDuration = MutableStateFlow(0)

    private val _currentPlaybackPosition = MutableStateFlow(0)
    val currentPlaybackPosition: StateFlow<Int> = _currentPlaybackPosition.asStateFlow()

    fun setSongListAdapter(adapter: SongListAdapter) {
        this.songListAdapter = adapter
    }

    fun setSongSelectorAdapter(adapter: SongSelectorAdapter) {
        this.selectorAdapter = adapter
    }

    init {
        _songDuration.value = 0
        _songs.value = emptyList()
        setupMediaPlayer()
    }

    fun onSearchButtonClick(context: Context) {
        val songs = getDeviceSongs(context)

        // Save current states before updating
        val wasPlaying = _isPlaying.value
        val currentTrackId = _currentTrack.value?.id

        if (_playerState.value == PlayerState.ADD_PLAYLIST) {
            // In ADD_PLAYLIST state, show all songs for selection
            _songSelectors.value = songs.map { song ->
                SongSelector(
                    song = song,
                    isSelected = _isSelectedMap.value[song.id] ?: false
                )
            }
            selectorAdapter.updateSongs(_songSelectors.value)
        } else {
            // In PLAY_MUSIC state, update the songs list
            _songs.value = songs
            songListAdapter.updateSongs(songs)
        }

        // Check for "All Songs" playlist
        val allSongsPlaylist = _playlists.value.find { it.name == "All Songs" }
        if (allSongsPlaylist == null) {
            val newAllSongsPlaylist = Playlist(
                id = UUID.randomUUID().toString(),
                name = "All Songs",
                songs = songs
            )
            _playlists.value = listOf(newAllSongsPlaylist)

            // Only update current playlist in PLAY_MUSIC state
            if (_playerState.value == PlayerState.PLAY_MUSIC) {
                _currentPlaylist.value = newAllSongsPlaylist

                // Restore the current track position if it exists in the new song list
                currentTrackId?.let { id ->
                    val newPosition = songs.indexOfFirst { it.id == id }
                    if (newPosition != -1) {
                        _currentPosition.value = newPosition
                        _currentTrack.value = songs[newPosition]
                        _isPlaying.value = wasPlaying
                    }
                }
            }
        }
    }

    /*>>>> MEDIA PLAYER & CONTROLLER REGION <<<<*/

    private fun setupMediaPlayer() {
        mediaPlayerHelper.setupMediaPlayer()
        mediaPlayerHelper.setOnCompletionCallback {
            _isPlaying.value = false
            _currentPlaybackPosition.value = 0
            stopProgressTracking()
        }

        // Get duration from helper
        val durationInMillis = mediaPlayerHelper.getDuration()
        _songDuration.value = durationInMillis / 1000
    }

    fun cleanupMediaPlayer() {
        mediaPlayerHelper.cleanupMediaPlayer()
    }

    fun seekToPosition(positionInSeconds: Int) {
        mediaPlayerHelper.seekTo(positionInSeconds)
        _currentPlaybackPosition.value = positionInSeconds
    }

    private val handler = Handler(Looper.getMainLooper())
    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            if (playerState.value == PlayerState.PLAY_MUSIC) {
                val currentPositionInSeconds = mediaPlayerHelper.getCurrentPosition()
                _currentPlaybackPosition.value = currentPositionInSeconds

                // Schedule next update
                handler.postDelayed(this, 200)
            }
        }
    }

    private fun startProgressTracking() {
        handler.post(updateProgressRunnable)
    }

    fun pauseProgressTracking() {
        handler.removeCallbacks(updateProgressRunnable)
    }

    private fun stopProgressTracking() {
        handler.removeCallbacks(updateProgressRunnable)
        _currentPlaybackPosition.value = 0
    }

    fun resumeProgressTracking() {
        if (playerState.value == PlayerState.PLAY_MUSIC) {
            startProgressTracking()
        }
    }

    fun onPlayPauseClicked(position: Int) {
        if (position !in songs.value.indices) return

        _currentPosition.value = position
        val selectedSong = songs.value[position]

        when {
            selectedSong != currentTrack.value || !mediaPlayerHelper.isInitialized() -> {
                try {
                    _isPlaying.value = true
                    mediaPlayerHelper.cleanupMediaPlayer()

                    previousTrackId = currentTrack.value?.id
                    _currentTrack.value = selectedSong

                    setupMediaPlayer()
                    mediaPlayerHelper.startMediaPlayer(selectedSong.id)
                    _currentPlaybackPosition.value = mediaPlayerHelper.getCurrentPosition()
                    startProgressTracking()

                    previousTrackId?.let { prevId ->
                        songListAdapter.updateCurrentTrack(selectedSong, prevId)
                    }
                } catch (e: Exception) {
                    mediaPlayerHelper.resetMediaPlayer()
                }
            }

            selectedSong == currentTrack.value -> {
                _isPlaying.value = !isPlaying.value

                if (_isPlaying.value) {
                    mediaPlayerHelper.startMediaPlayer(selectedSong.id)
                    startProgressTracking()
                } else {
                    mediaPlayerHelper.pauseMediaPlayer()
                    pauseProgressTracking()
                }

                songListAdapter.updateCurrentTrack(selectedSong, selectedSong.id)
            }
        }
    }

    fun onStopMediaPlayer() {
        mediaPlayerHelper.stopMediaPlayer()
        _isPlaying.value = false
        stopProgressTracking()
        _currentPlaybackPosition.value = 0
    }

    override fun onCleared() {
        super.onCleared()
        mediaPlayerHelper.cleanupMediaPlayer()
    }

    /*>>>> PLAYLIST REGION <<<<*/

    fun canAddPlaylist(): Boolean {
        return _songs.value.isNotEmpty()
    }

    fun switchToPlayMusicState(clearSelections: Boolean = true) {
        _playerState.value = PlayerState.PLAY_MUSIC
        _songs.value = _currentPlaylist.value?.songs ?: emptyList()
        songListAdapter.updateSongs(_songs.value)

        if (clearSelections) {
            _isSelectedMap.value = emptyMap()
            _selectedSongs.value = emptyList()
        }

        if (_isPlaying.value) {
            mediaPlayer?.start()
            startProgressTracking()
        } else {
            mediaPlayer?.pause()
            pauseProgressTracking()
        }
    }

    fun switchToAddPlaylistState(clearSelections: Boolean = true) {
        // Only clear if coming from menu click
        if (clearSelections) {
            _isSelectedMap.value = emptyMap()
            _selectedSongs.value = emptyList()
        }

        val allSongsPlaylist = _playlists.value.find { it.name == "All Songs" }
        allSongsPlaylist?.let {
            _songSelectors.value = it.songs.map { song ->
                SongSelector(
                    song = song,
                    isSelected = _isSelectedMap.value[song.id] ?: false
                )
            }
        }
        _playerState.value = PlayerState.ADD_PLAYLIST
    }

    fun toggleSongSelection(songSelector: SongSelector) {
        _isSelectedMap.update { currentMap ->
            currentMap + (songSelector.song.id to !(currentMap[songSelector.song.id] ?: false))
        }
        updateSelectedSongs()
    }

    private fun updateSelectedSongs() {
        _selectedSongs.value = _songs.value.filter { song ->
            _isSelectedMap.value[song.id] == true
        }
    }

    fun onSongLongPressed(position: Int) {
        songs.value.getOrNull(position)?.let { song ->
            _isSelectedMap.value = mapOf(song.id to true)
            _selectedSongs.value = listOf(song)
            switchToAddPlaylistState(clearSelections = false)
        }
    }

    fun addPlaylist(playlist: Playlist) {
        val currentPos = _currentPosition.value
        val currentTrackValue = _currentTrack.value
        val currentSongs = _songs.value

        _playlists.update { currentPlaylists ->
            currentPlaylists + playlist
        }

        _currentPosition.value = currentPos
        _currentTrack.value = currentTrackValue
        _songs.value = currentSongs

        switchToPlayMusicState(clearSelections = false)
    }

    fun setCurrentPlaylist(playlist: Playlist) {
        val wasPlaying = _isPlaying.value
        val playlistToLoad = _playlists.value.find { it.name == playlist.name }
        playlistToLoad?.let {
            _currentPlaylist.value = it
            _songs.value = it.songs

            val currentlyPlayingSong = _currentTrack.value
            if (it.songs.any { song -> song.id == currentlyPlayingSong?.id }) {
                if (currentlyPlayingSong != null) {
                    _currentPosition.value =
                        it.songs.indexOfFirst { song -> song.id == currentlyPlayingSong.id }
                }
                _isPlaying.value = wasPlaying

                // Actually start or pause MediaPlayer based on wasPlaying
                if (wasPlaying) {
                    mediaPlayer?.start()
                    startProgressTracking()
                } else {
                    mediaPlayer?.pause()
                    pauseProgressTracking()
                }
            } else {
                // For a new track, properly initialize everything
                val newTrack = it.songs.first()
                _currentPosition.value = 0
                _currentTrack.value = newTrack
                _isPlaying.value = false

                mediaPlayerHelper.cleanupMediaPlayer()
                setupMediaPlayer()
                mediaPlayerHelper.startMediaPlayer(newTrack.id)  // Initialize with new track
                mediaPlayerHelper.pauseMediaPlayer()  // Immediately pause since isPlaying is false
                mediaPlayerHelper.seekTo(0)
                _currentPlaybackPosition.value = 0
                pauseProgressTracking()
            }
            requestAdapterUpdateItemPlayPause(_currentPosition.value ?: 0)
        }
    }

    fun loadPlaylistForSelection(playlist: Playlist) {
        val currentState = _playerState.value
        val wasPlaying = _isPlaying.value
        val currentlyPlayingSong = _currentTrack.value

        when (currentState) {
            PlayerState.PLAY_MUSIC -> {
                _songs.value = playlist.songs
                if (playlist.songs.any { it.id == currentlyPlayingSong?.id }) {
                    if (currentlyPlayingSong != null) {
                        _currentPosition.value =
                            playlist.songs.indexOfFirst { it.id == currentlyPlayingSong.id }
                    }
                    _currentTrack.value = currentlyPlayingSong
                    _isPlaying.value = wasPlaying
                } else {
                    _currentPosition.value = 0
                    _currentTrack.value = playlist.songs.first()
                    _isPlaying.value = true // sets drawable (true: ic_play, false: ic_pause)
                }
                _playerState.value = PlayerState.PLAY_MUSIC
            }

            PlayerState.ADD_PLAYLIST -> {
                _songs.value = playlist.songs // test this change
                _songSelectors.value = playlist.songs.map { song ->
                    SongSelector(song = song, isSelected = _isSelectedMap.value[song.id] ?: false)
                }
                selectorAdapter.updateSongs(_songSelectors.value)
                selectorAdapter.updateSelectedMap(_isSelectedMap.value)
            }
        }
    }

    fun deletePlaylist(playlistName: String) {
        val currentState = _playerState.value
        val playlist = _playlists.value.find { it.name == playlistName }

        if (playlist != null) {
            _playlists.update { it.filter { p -> p.name != playlistName } }
            val allSongs = _playlists.value.find { it.name == "All Songs" }

            if (_currentPlaylist.value?.name == playlistName) {
                allSongs?.let {
                    _currentPlaylist.value = it
                    _songs.value = it.songs

                    when (currentState) {
                        PlayerState.ADD_PLAYLIST -> {
                            _songSelectors.value = it.songs.map { song ->
                                SongSelector(
                                    song = song,
                                    isSelected = _isSelectedMap.value[song.id] ?: false
                                )
                            }
                            selectorAdapter.updateSongs(_songSelectors.value)
                            selectorAdapter.updateSelectedMap(_isSelectedMap.value)
                        }
                        PlayerState.PLAY_MUSIC -> {
                            _currentPlaylist.value = allSongs
                            _songs.value = allSongs.songs
                        }
                    }
                }
            }
        }
    }

    fun requestAdapterUpdateItemPlayPause(position: Int) {
        currentPosition.value?.let {
            songListAdapter.updatePlayPauseButtonState(position, isPlaying.value)
        }
    }

    private fun getDeviceSongs(context: Context): List<Song> {
        val songs = mutableListOf<Song>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                null
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

                while (cursor.moveToNext()) {
                    val id = cursor.getInt(idColumn)
                    val title = cursor.getString(titleColumn)
                    val artist = cursor.getString(artistColumn)
                    val duration = cursor.getLong(durationColumn)

                    songs.add(Song(id, title, artist, duration))
                }
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error loading songs", e)
        }

        return songs
    }

    private fun createHardCodedSongs(): List<Song> {
        return (1..10).map { index ->
            Song(
                id = index,
                title = "title$index",
                artist = "artist$index",
                duration = 215_000
            )
        }
    }
}
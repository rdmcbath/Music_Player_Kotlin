package org.hyperskill.musicplayer

import android.app.Application
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private var mediaPlayerHelper: MediaPlayerHelper = MediaPlayerHelper(application.applicationContext)
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
    val songDuration: StateFlow<Int> = _songDuration.asStateFlow()

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
        setupMediaPlayer()
    }

    fun onSearchButtonClick() {
        val hardCodedSongs = createHardCodedSongs()
        _songs.value = hardCodedSongs  // Set songs immediately regardless of state

        val currentState = _playerState.value

        // Update or create "All Songs" playlist
        val allSongsPlaylist = _playlists.value.find { it.name == "All Songs" }
        if (allSongsPlaylist == null) {
            val newAllSongsPlaylist = Playlist(
                id = UUID.randomUUID().toString(),
                name = "All Songs",
                songs = hardCodedSongs
            )
            _playlists.value = listOf(newAllSongsPlaylist)

            if (currentState == PlayerState.PLAY_MUSIC) {
                setCurrentPlaylist(newAllSongsPlaylist)
            }
        } else {
            // Update existing playlist
            val updatedAllSongs = allSongsPlaylist.copy(songs = hardCodedSongs)
            _playlists.value = _playlists.value.map {
                if (it.name == "All Songs") updatedAllSongs else it
            }

            if (currentState == PlayerState.PLAY_MUSIC) {
                setCurrentPlaylist(updatedAllSongs)
            }
        }

        if (currentState == PlayerState.ADD_PLAYLIST) {
            _songSelectors.value = hardCodedSongs.map { song ->
                SongSelector(song = song, isSelected = _isSelectedMap.value[song.id] ?: false)
            }
            selectorAdapter.updateSongs(_songSelectors.value)
            selectorAdapter.updateSelectedMap(_isSelectedMap.value)
        }
    }

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

    /*>>>> CONTROLLER REGION <<<<*/
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

    /*>>>> PLAYLIST REGION <<<<*/
    fun canAddPlaylist(): Boolean {
        return _songs.value.isNotEmpty()
    }

    fun switchToPlayMusicState(clearSelections: Boolean = true) {
        _playerState.value = PlayerState.PLAY_MUSIC
        _songs.value = _currentPlaylist.value?.songs ?: emptyList()

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

    fun onPlayPauseClicked(position: Int) {
        if (position !in songs.value.indices) return

        _currentPosition.value = position
        val selectedSong = songs.value[position]

        when {
            selectedSong != currentTrack.value || !mediaPlayerHelper.isInitialized() -> {
                try {
                    mediaPlayerHelper.cleanup()

                    previousTrackId = currentTrack.value?.id
                    _currentTrack.value = selectedSong
                    _isPlaying.value = true

                    setupMediaPlayer()
                    mediaPlayerHelper.startMediaPlayer()

                    previousTrackId?.let { prevId ->
                        songListAdapter.updateCurrentTrack(selectedSong, prevId)
                    }

                    startProgressTracking()
                } catch (e: Exception) {
                    mediaPlayerHelper.resetMediaPlayer()
                }
            }
            selectedSong == currentTrack.value -> {
                _isPlaying.value = !isPlaying.value

                selectedSong.id.let { currentId ->
                    songListAdapter.updateCurrentTrack(selectedSong, currentId)
                }

                try {
                    if (_isPlaying.value) {
                        mediaPlayerHelper.startMediaPlayer()
                        startProgressTracking()
                    } else {
                        mediaPlayerHelper.pauseMediaPlayer()
                        pauseProgressTracking()
                    }
                } catch (e: Exception) {
                    mediaPlayerHelper.resetMediaPlayer()
                }
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
        mediaPlayerHelper.cleanup()
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

        switchToPlayMusicState()
    }

    // for PLAY_MUSIC state
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
                _currentPosition.value = 0
                _currentTrack.value = it.songs.first()
                _isPlaying.value = false
                mediaPlayerHelper.pauseMediaPlayer()
                mediaPlayerHelper.seekTo(0)  // Explicitly seek to 0
                _currentPlaybackPosition.value = 0  // Reset the seekbar position
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
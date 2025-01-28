package org.hyperskill.musicplayer

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var searchButton: Button
    private val songAdapter: SongListAdapter by lazy { SongListAdapter(viewModel) }
    private val selectorAdapter: SongSelectorAdapter by lazy { SongSelectorAdapter(viewModel) }
    val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        searchButton = findViewById(R.id.mainButtonSearch)
        recyclerView = findViewById(R.id.mainSongList)

        // Ensure we're in PLAY_MUSIC state initially
        viewModel.switchToPlayMusicState()

        // Setup adapters in this order:
        viewModel.setSongListAdapter(songAdapter)  // Set adapter in ViewModel first
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = songAdapter

        searchButton.setOnClickListener {
            checkAndRequestPermission()
        }

        setupPlayerStateObserver()
        setupSongsObserver()
        setupSongSelectorObserver()
        setupSelectedMapObserver()
        setupPlaybackStateObserver()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    private fun setupPlayerStateObserver() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.playerState.collect { state ->
                    updateUI(state)
                }
            }
        }
    }

    private fun setupSongsObserver() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.songs.collect { songs ->
                    songAdapter.updateSongs(songs)
                }
            }
        }
    }

    private fun setupSongSelectorObserver() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.songSelectors.collect { selectors ->
                    selectorAdapter.updateSongs(selectors)
                }
            }
        }
    }

    private fun setupSelectedMapObserver() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isSelectedMap.collect { selectedMap ->
                    selectorAdapter.updateSelectedMap(selectedMap)
                }
            }
        }
    }

    private fun setupPlaybackStateObserver() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Combine currentTrack and isPlaying states
                viewModel.currentTrack.combine(viewModel.isPlaying) { track, isPlaying ->
                    Pair(track, isPlaying)
                }.collect { (track, _) ->
                    // Find and update previous track position
                    val previousTrackPosition = songAdapter.songs.indexOfFirst { it.id == viewModel.currentTrack.value?.id }
                    if (previousTrackPosition != -1) {
                        songAdapter.notifyItemChanged(previousTrackPosition)
                    }

                    // Find and update new track position
                    val newTrackPosition = songAdapter.songs.indexOfFirst { it.id == track?.id }
                    if (newTrackPosition != -1) {
                        songAdapter.notifyItemChanged(newTrackPosition)
                    }
                }
            }
        }
    }

    private fun updateUI(state: PlayerState) {
        when (state) {
            is PlayerState.PLAY_MUSIC -> {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.mainFragmentContainer, MainPlayerControllerFragment())
                    .commit()

                recyclerView.adapter = songAdapter
                viewModel.setSongListAdapter(songAdapter)

                viewModel.currentTrack.value?.let { currentSong ->
                    songAdapter.updateCurrentTrack(currentSong, viewModel.previousTrackId ?: -1)
                }
            }

            is PlayerState.ADD_PLAYLIST -> {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.mainFragmentContainer, MainAddPlaylistFragment())
                    .commit()

                recyclerView.adapter = selectorAdapter
                viewModel.setSongSelectorAdapter(selectorAdapter)
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.mainMenuAddPlaylist -> {
                when {
                    !viewModel.canAddPlaylist() -> {
                        Toast.makeText(this, "no songs loaded, click search to load songs", Toast.LENGTH_SHORT).show()
                        true
                    }
                    else -> {
                        viewModel.switchToAddPlaylistState(clearSelections = true)
                        true
                    }
                }
            }

            R.id.mainMenuLoadPlaylist -> {
                val playlistNames = viewModel.playlists.value.map { it.name }.toTypedArray()

                AlertDialog.Builder(this)
                    .setTitle("choose playlist to load")
                    .setItems(playlistNames) { _, index ->
                        val selectedName = playlistNames[index]
                        val playlist = viewModel.playlists.value.find { it.name == selectedName }
                        playlist?.let {
                            when (viewModel.playerState.value) {
                                PlayerState.PLAY_MUSIC -> viewModel.setCurrentPlaylist(it)
                                PlayerState.ADD_PLAYLIST -> viewModel.loadPlaylistForSelection(it)
                            }
                        }
                    }
                    .setNegativeButton("cancel") { dialog, _ -> dialog.dismiss() }
                    .show()
                true
            }

            R.id.mainMenuDeletePlaylist -> {
                val playlistNames = viewModel.playlists.value
                    .filter { it.name != "All Songs" }
                    .map { it.name }
                    .toTypedArray()

                AlertDialog.Builder(this)
                    .setTitle("choose playlist to delete")
                    .setItems(playlistNames) { _, index ->
                        val selectedName = playlistNames[index]
                        viewModel.deletePlaylist(selectedName)
                    }
                    .setNegativeButton("cancel") { dialog, _ -> dialog.dismiss() }
                    .show()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun checkAndRequestPermission() {
        val permission = Manifest.permission.READ_EXTERNAL_STORAGE

        // Clear the "don't ask again" state if exists
        if (!shouldShowRequestPermissionRationale(permission)) {
            // This resets the "don't ask again" state
            ActivityCompat.requestPermissions(
                this,
                arrayOf(permission),
                1
            )
            return
        }

        when (ContextCompat.checkSelfPermission(this, permission)) {
            PackageManager.PERMISSION_GRANTED -> {
                viewModel.onSearchButtonClick(this)
            }
            else -> {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(permission),
                    1
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            1 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    viewModel.onSearchButtonClick(this)
                } else {
                    Toast.makeText(this, "Songs cannot be loaded without permission", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
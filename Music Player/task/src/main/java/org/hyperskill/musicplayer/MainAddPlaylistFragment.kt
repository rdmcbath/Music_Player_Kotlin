package org.hyperskill.musicplayer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import java.util.UUID

class MainAddPlaylistFragment : Fragment() {
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity?.let {
            if (it is MainActivity) {
                viewModel = it.viewModel
            }
        } ?: throw IllegalStateException("Fragment must be attached to MainActivity")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_main_add_playlist, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupListeners()
    }

    private fun setupListeners() {
        view?.let { fragmentView ->
            fragmentView.findViewById<Button>(R.id.addPlaylistBtnCancel).setOnClickListener {
                viewModel.switchToPlayMusicState()
            }

            fragmentView.findViewById<Button>(R.id.addPlaylistBtnOk).setOnClickListener {
                val playlistNameEditText =
                    view?.findViewById<EditText>(R.id.addPlaylistEtPlaylistName)
                if (playlistNameEditText != null) {
                    handleOkButtonClick(playlistNameEditText)
                }
            }

        }
    }

    private fun handleOkButtonClick(editText: EditText) {
        val playlistName = editText.text.toString().trim()
        val selectedSongs = viewModel.selectedSongs.value

        when {
            playlistName.equals("All Songs", ignoreCase = true) -> {
                showToast("All Songs is a reserved name choose another playlist name")
            }
            selectedSongs.isEmpty() -> {
                showToast("Add at least one song to your playlist")
            }

            playlistName.isBlank() -> {
                showToast("Add a name to your playlist")
            }

            else -> {
                // Create new playlist with selected songs
                val newPlaylist = Playlist(
                    id = UUID.randomUUID().toString(),
                    name = playlistName,
                    songs = selectedSongs
                )
                viewModel.addPlaylist(newPlaylist)
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}
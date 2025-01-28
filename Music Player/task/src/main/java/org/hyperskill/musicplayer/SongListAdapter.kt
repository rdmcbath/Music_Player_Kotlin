package org.hyperskill.musicplayer

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Locale

class SongListAdapter(
    private val viewModel: MainViewModel
) : RecyclerView.Adapter<SongListAdapter.SongViewHolder>() {
    private lateinit var recyclerView: RecyclerView

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
    }

    var songs: List<Song> = emptyList()
        private set

    fun updateSongs(newSongs: List<Song>) {
        songs = newSongs
        notifyDataSetChanged()
        Log.d("SongListAdapter", "updateSongs called, new size: ${songs.size}")
    }

    override fun onViewRecycled(holder: SongViewHolder) {
        super.onViewRecycled(holder)
        holder.collectJob?.cancel()
    }

    fun updateCurrentTrack(song: Song?, prevTrackId: Int) {
        // Update previous track item if it exists
        prevTrackId.let { prevId ->
            val prevPosition = songs.indexOfFirst { it.id == prevId }
            if (prevPosition != -1) notifyItemChanged(prevPosition)
        }

        // Update new track item
        song?.let { current ->
            val newPosition = songs.indexOfFirst { it.id == current.id }
            if (newPosition != -1) notifyItemChanged(newPosition)
        }
    }

    fun updatePlayPauseButtonState(position: Int, isPlaying: Boolean) {
        (recyclerView.findViewHolderForAdapterPosition(position) as? SongViewHolder)?.playPauseBtn?.setImageResource(
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
        notifyItemChanged(position)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): SongViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_song, parent, false)

        return SongViewHolder(view, viewModel = this.viewModel)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val song = songs[position]
        holder.bind(song)
    }

    override fun getItemCount(): Int = songs.size

    inner class SongViewHolder(
        itemView: View,
        private val viewModel: MainViewModel
    ) : RecyclerView.ViewHolder(itemView) {
        val playPauseBtn: ImageButton = itemView.findViewById(R.id.songItemImgBtnPlayPause)
        private val titleTv: TextView = itemView.findViewById(R.id.songItemTvTitle)
        private val artistTv: TextView = itemView.findViewById(R.id.songItemTvArtist)
        private val durationTv: TextView = itemView.findViewById(R.id.songItemTvDuration)

        init {
            itemView.setOnLongClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    viewModel.onSongLongPressed(position)
                    true  // Return true to indicate the long click was handled
                } else {
                    false // Return false if position is invalid
                }
            }
        }

        var collectJob: Job? = null

        fun bind(song: Song) {
            titleTv.text = song.title
            artistTv.text = song.artist
            durationTv.text = formatDuration(song.duration)

            collectJob?.cancel()
            collectJob = (itemView.context as? LifecycleOwner)?.lifecycleScope?.launch {
                viewModel.currentTrack.combine(viewModel.isPlaying) { track, isPlaying ->
                    Pair(track, isPlaying)
                }.collect { (currentTrack, isPlaying) ->
                    val isThisSongPlaying = currentTrack?.id == song.id
                    playPauseBtn.setImageResource(
                        if (isPlaying && isThisSongPlaying) R.drawable.ic_pause else R.drawable.ic_play
                    )
                }
            }

            playPauseBtn.setOnClickListener {
                viewModel.onPlayPauseClicked(adapterPosition)
                viewModel.currentTrack.value?.id.let { track ->
                    val isSongPlaying = track == song.id && viewModel.isPlaying.value
                    playPauseBtn.setImageResource(
                        if (isSongPlaying) R.drawable.ic_pause else R.drawable.ic_play
                    )
                }
            }
        }

        private fun formatDuration(durationMs: Long): String {
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }
}
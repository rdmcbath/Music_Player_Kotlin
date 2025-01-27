package org.hyperskill.musicplayer

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class SongSelectorAdapter(
    private val viewModel: MainViewModel
) : RecyclerView.Adapter<SongSelectorAdapter.SongSelectorViewHolder>() {
    private var songs = listOf<SongSelector>()
    private var selectedMap = mapOf<Int, Boolean>()

    fun updateSongs(newSongs: List<SongSelector>) {
        songs = newSongs
        notifyDataSetChanged()
    }

    fun updateSelectedMap(newMap: Map<Int, Boolean>) {
        selectedMap = newMap
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongSelectorViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_song_selector, parent, false)
        return SongSelectorViewHolder(view)
    }

    override fun onBindViewHolder(holder: SongSelectorViewHolder, position: Int) {
        holder.bind(songs[position])
    }

    override fun getItemCount(): Int = songs.size

    inner class SongSelectorViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {
        private val rootLayout: ConstraintLayout = itemView.findViewById(R.id.songSelectorItemRoot)
        private val checkbox: CheckBox = itemView.findViewById(R.id.songSelectorItemCheckBox)
        private val titleTextView: TextView = itemView.findViewById(R.id.songSelectorItemTvTitle)
        private val artistTextView: TextView = itemView.findViewById(R.id.songSelectorItemTvArtist)
        private val durationTextView: TextView =
            itemView.findViewById(R.id.songSelectorItemTvDuration)

        fun bind(songSelector: SongSelector) {
            val isItemSelected = selectedMap[songSelector.song.id] ?: false

            checkbox.setOnClickListener {
                viewModel.toggleSongSelection(songSelector)
            }

            itemView.setOnClickListener {
                viewModel.toggleSongSelection(songSelector)
            }

            //rootLayout.isSelected = isItemSelected
            checkbox.isChecked = isItemSelected
            titleTextView.text = songSelector.song.title
            artistTextView.text = songSelector.song.artist
            durationTextView.text = formatDuration(songSelector.song.duration)

            rootLayout.setBackgroundColor(if (isItemSelected) Color.LTGRAY else Color.WHITE)
        }

        private fun formatDuration(durationMs: Long): String {
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }
}
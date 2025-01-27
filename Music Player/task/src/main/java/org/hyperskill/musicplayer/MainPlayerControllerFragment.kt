package org.hyperskill.musicplayer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Locale

class MainPlayerControllerFragment : Fragment() {
    private lateinit var viewModel: MainViewModel
    private lateinit var playPauseBtn: Button
    private lateinit var stopBtn: Button
    private lateinit var controllerSeekBar: SeekBar
    private lateinit var controllerTvCurrentTime: TextView
    private lateinit var controllerTvTotalTime: TextView

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
        return inflater.inflate(R.layout.fragment_main_player_controller, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        playPauseBtn = view.findViewById(R.id.controllerBtnPlayPause)
        stopBtn = view.findViewById(R.id.controllerBtnStop)
        controllerSeekBar = view.findViewById(R.id.controllerSeekBar)
        controllerTvCurrentTime = view.findViewById(R.id.controllerTvCurrentTime)
        controllerTvTotalTime = view.findViewById(R.id.controllerTvTotalTime)

        // Update seekbar max when duration changes
        lifecycleScope.launch {
            viewModel.songDuration.collectLatest { duration ->
                controllerSeekBar.max = duration
            }
        }

        // Update current position
        lifecycleScope.launch {
            viewModel.currentPlaybackPosition.collectLatest { position ->
                if (!controllerSeekBar.isPressed) {  // Only update if user isn't dragging
                    controllerSeekBar.progress = position
                    updateTimeDisplay(controllerTvCurrentTime, position)
                }
            }
        }

        // Update duration display
        lifecycleScope.launch {
            combine(
                viewModel.songDuration,
                viewModel.currentTrack
            ) { durationInSeconds, currentTrack ->
                Pair(durationInSeconds, currentTrack)
            }.collectLatest { (durationInSeconds, currentTrack) ->
                if (currentTrack != null) {
                    updateTimeDisplay(controllerTvTotalTime, durationInSeconds)
                } else {
                    controllerTvTotalTime.text = getString(R.string._00_00)
                }
            }
        }

        playPauseBtn.setOnClickListener {
            if (viewModel.songs.value.isNotEmpty()) {
                viewModel.currentPosition.value?.let { position ->
                    viewModel.onPlayPauseClicked(position)
                }
            }
        }

        stopBtn.setOnClickListener {
            viewModel.onStopMediaPlayer()
            controllerSeekBar.progress = 0
            updateTimeDisplay(controllerTvCurrentTime, 0)

            viewModel.currentPosition.value?.let { position ->
                viewModel.requestAdapterUpdateItemPlayPause(position)
            }
        }

        controllerSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    updateTimeDisplay(controllerTvCurrentTime, progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                viewModel.pauseProgressTracking()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.progress?.let { progress ->
                    viewModel.seekToPosition(progress)
                    // Make sure we update the display immediately
                    updateTimeDisplay(controllerTvCurrentTime, progress)
                    viewModel.resumeProgressTracking()
                }
            }
        })
    }

    private fun updateTimeDisplay(textView: TextView, timeInSeconds: Int) {
        val minutes = timeInSeconds / 60
        val seconds = timeInSeconds % 60
        textView.text = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    override fun onPause() {
        super.onPause()
        viewModel.pauseProgressTracking()
    }

    override fun onResume() {
        super.onResume()
        if (viewModel.isPlaying.value) {
            viewModel.resumeProgressTracking()
        }
    }
}
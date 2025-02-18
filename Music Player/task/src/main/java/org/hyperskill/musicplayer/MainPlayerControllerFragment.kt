package org.hyperskill.musicplayer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import org.hyperskill.musicplayer.viewmodel.PlayerViewModel
import java.util.Locale

class MainPlayerControllerFragment : Fragment() {
    private lateinit var controllerBtnPlayPause: Button
    private lateinit var controllerBtnStop: Button
    private lateinit var controllerTvCurrentTime: TextView
    private lateinit var controllerTvTotalTime: TextView
    private lateinit var controllerSeekBar: SeekBar
    private lateinit var listener: OnMainPlayerControllerFragmentButtonsClicksListener
    private lateinit var viewModel: PlayerViewModel
    private var userIsSeeking = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_main_player_controller, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(requireActivity())[PlayerViewModel::class.java]

        initializeViews(view)
        setButtonsListeners()
        setOnControllerSeekBarChangeListener()
        setCurrentTrackObserver()
        listener = requireActivity() as OnMainPlayerControllerFragmentButtonsClicksListener
    }

    private fun initializeViews(view: View) {
        controllerBtnPlayPause = view.findViewById(R.id.controllerBtnPlayPause)
        controllerBtnStop = view.findViewById(R.id.controllerBtnStop)
        controllerSeekBar = view.findViewById(R.id.controllerSeekBar)
        controllerTvCurrentTime = view.findViewById(R.id.controllerTvCurrentTime)
        controllerTvTotalTime = view.findViewById(R.id.controllerTvTotalTime)
    }

    private fun setButtonsListeners() {
        controllerBtnPlayPause.setOnClickListener {
            listener.onControllerBtnPlayPauseButtonClick()
        }

        controllerBtnStop.setOnClickListener {
           controllerSeekBar.progress = 0
            controllerTvCurrentTime.text =
                requireActivity().resources.getString(R.string.song_duration_time, 0, 0)

            listener.onControllerBtnStopButtonClick()
        }
    }

    private fun setCurrentTrackObserver() {
        viewModel.currentTrackItem.observe(viewLifecycleOwner) { track ->
            if (track != null) {
                // Update total time from track duration
                val totalDuration = track.song.durationInMilliseconds.toInt()
                controllerTvTotalTime.text = formatTime(totalDuration)
                controllerSeekBar.max = totalDuration / 1000
            } else {
                // Reset to default when no track
                controllerTvCurrentTime.text =
                    requireActivity().resources.getString(R.string.song_duration_time, 0, 0)
                controllerSeekBar.max = 100
            }
        }
    }

    private fun setOnControllerSeekBarChangeListener() {
        viewModel.currentPosition.observe(viewLifecycleOwner) { position ->
            if (!userIsSeeking) {  // Only update if user isn't manually seeking
                controllerSeekBar.progress = position / 1000
                controllerTvCurrentTime.text = formatTime(position)
            }
        }

        controllerSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    controllerTvCurrentTime.text = formatTime(progress * 1000)
                    viewModel.updateCurrentPosition(progress * 1000)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                userIsSeeking = true
                viewModel.stopUpdatingPosition()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                userIsSeeking = false
                val progress = controllerSeekBar.progress
                viewModel.mediaPlayer?.seekTo(progress * 1000)

                if (viewModel.isMediaPlayerCompleted) {
                    // Use currentTrackItem from ViewModel instead of playlistAdapter
                    viewModel.currentTrackItem.value?.let {
                        viewModel.updateCurrentPosition(progress * 1000)
                    }
                }

                if (viewModel.mediaPlayer?.isPlaying == true) {
                    viewModel.startUpdatingPosition()
                }

                if (viewModel.isMediaPlayerCompleted) {
                    viewModel.mediaPlayer?.start()
                    viewModel.isMediaPlayerCompleted = false
                }
            }
        })
    }

    private fun formatTime(milliseconds: Int): String {
        val seconds = (milliseconds / 1000) % 60
        val minutes = (milliseconds / 1000) / 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    interface OnMainPlayerControllerFragmentButtonsClicksListener {
        fun onControllerBtnPlayPauseButtonClick()
        fun onControllerBtnStopButtonClick()
    }
}
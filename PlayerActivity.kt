package com.nodecasttv.app.ui

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.ui.PlayerView
import com.nodecasttv.app.R
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@UnstableApi
class PlayerActivity : FragmentActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var tvTitle: TextView
    private lateinit var tvStatus: TextView
    private lateinit var controls: View
    private var controlsVisible = false
    private val hideHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val hideRunnable = Runnable { hideControls() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        val streamUrl = intent.getStringExtra("stream_url") ?: ""
        val title = intent.getStringExtra("title") ?: ""

        playerView = findViewById(R.id.player_view)
        tvTitle = findViewById(R.id.tv_title)
        tvStatus = findViewById(R.id.tv_status)
        controls = findViewById(R.id.controls_overlay)

        tvTitle.text = title
        tvStatus.text = "Carregando…"

        // Build player with OkHttp data source (better for streaming)
        val okHttp = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val dataSourceFactory = OkHttpDataSource.Factory(okHttp)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()

        playerView.player = player
        playerView.useController = false // We use our own overlay

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> tvStatus.text = "Buffering…"
                    Player.STATE_READY -> {
                        tvStatus.text = ""
                        showControls()
                    }
                    Player.STATE_ENDED -> tvStatus.text = "Fim"
                    Player.STATE_IDLE -> tvStatus.text = "Parado"
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                tvStatus.text = "Erro: ${error.message}"
                // Try fallback: non-HLS URL (remove .m3u8)
                val fallback = streamUrl.removeSuffix(".m3u8")
                if (fallback != streamUrl) {
                    tvStatus.text = "Tentando formato alternativo…"
                    playUrl(fallback)
                }
            }
        })

        playUrl(streamUrl)

        // Button controls
        findViewById<ImageButton>(R.id.btn_play_pause).setOnClickListener {
            if (player.isPlaying) player.pause() else player.play()
            updatePlayPauseButton()
        }
        findViewById<ImageButton>(R.id.btn_back_player).setOnClickListener {
            finish()
        }
        findViewById<ImageButton>(R.id.btn_seek_back).setOnClickListener {
            player.seekTo(maxOf(0, player.currentPosition - 10_000))
        }
        findViewById<ImageButton>(R.id.btn_seek_forward).setOnClickListener {
            player.seekTo(player.currentPosition + 10_000)
        }

        playerView.setOnClickListener { toggleControls() }
    }

    private fun playUrl(url: String) {
        val mediaItem = MediaItem.fromUri(url)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
    }

    private fun updatePlayPauseButton() {
        val btn = findViewById<ImageButton>(R.id.btn_play_pause)
        btn.setImageResource(
            if (player.isPlaying) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play
        )
    }

    private fun showControls() {
        controls.visibility = View.VISIBLE
        controls.alpha = 1f
        controlsVisible = true
        updatePlayPauseButton()
        scheduleHide()
    }

    private fun hideControls() {
        controls.animate().alpha(0f).setDuration(400).withEndAction {
            controls.visibility = View.GONE
            controlsVisible = false
        }
    }

    private fun toggleControls() {
        if (controlsVisible) hideControls() else showControls()
    }

    private fun scheduleHide() {
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, 4000)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> {
                toggleControls()
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                if (controlsVisible) { hideControls(); return true }
                finish()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (player.isPlaying) player.pause() else player.play()
                showControls()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                player.play(); showControls(); return true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                player.pause(); showControls(); return true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                player.seekTo(player.currentPosition + 10_000)
                showControls()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND,
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                player.seekTo(maxOf(0, player.currentPosition - 10_000))
                showControls()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onStop() {
        super.onStop()
        player.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }
}

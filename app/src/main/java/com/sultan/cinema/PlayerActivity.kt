package com.sultan.cinema

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView

class PlayerActivity : Activity() {
    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )

        val url = intent.getStringExtra(EXTRA_URL)
        if (url.isNullOrBlank() || !url.startsWith("https://archive.org/")) {
            finish()
            return
        }

        playerView = PlayerView(this).apply {
            useController = true
            controllerAutoShow = true
            keepScreenOn = true
            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
            setBackgroundColor(android.graphics.Color.BLACK)
            contentDescription = intent.getStringExtra(EXTRA_TITLE) ?: "Видеоплеер"
        }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            addView(playerView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
        })

        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("SultanCinema/0.1 AndroidTV")
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(30000)
            .setAllowCrossProtocolRedirects(true)
        val dataSourceFactory = DefaultDataSource.Factory(this, httpFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .also { exo ->
                playerView.player = exo
                exo.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        android.util.Log.e("SultanCinemaPlayer", "Playback failed: ${error.errorCodeName}", error)
                    }
                })
                val metadata = MediaMetadata.Builder()
                    .setTitle(intent.getStringExtra(EXTRA_TITLE) ?: "Видео")
                    .setSubtitle(intent.getStringExtra(EXTRA_SOURCE) ?: "Internet Archive")
                    .build()
                val item = MediaItem.Builder()
                    .setUri(url)
                    .setMediaMetadata(metadata)
                    .build()
                exo.setMediaItem(item)
                exo.prepare()
                exo.playWhenReady = true
            }
    }

    override fun onStop() {
        player?.pause()
        super.onStop()
    }

    override fun onDestroy() {
        playerView.player = null
        player?.release()
        player = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "cinema_url"
        const val EXTRA_TITLE = "cinema_title"
        const val EXTRA_SOURCE = "cinema_source"
        const val EXTRA_LICENSE = "cinema_license"
    }
}

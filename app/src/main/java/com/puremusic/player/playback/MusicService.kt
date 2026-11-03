package com.puremusic.player.playback

import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioAttributes as PlatformAudioAttributes
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.ForwardingPlayer
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class MusicService : MediaSessionService() {
    companion object {
        const val PREFS = "pure_music_playback"
        const val KEY_MODE = "playback_mode"
    }
    private var player: ExoPlayer? = null
    private var session: MediaSession? = null
    private lateinit var audioManager: AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var hasFocus = false
    private var ignoreNextPauseAbandon = false

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> { hasFocus = true /* Deliberately do not resume. */ }
            AudioManager.AUDIOFOCUS_LOSS -> {
                hasFocus = false
                if (player?.playWhenReady == true) ignoreNextPauseAbandon = true
                player?.playWhenReady = false
                abandonFocus()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Pause immediately but retain the focus request so a later GAIN can
                // mark focus available. GAIN never resumes playback automatically.
                hasFocus = false
                if (player?.playWhenReady == true) ignoreNextPauseAbandon = true
                player?.playWhenReady = false
            }
        }
    }

    @UnstableApi
    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val attrs = AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build()
        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        player = ExoPlayer.Builder(this, renderersFactory).build().apply {
            setAudioAttributes(attrs, false) // Focus is handled explicitly to prevent automatic resume.
            addListener(object : Player.Listener {
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    if (playWhenReady) {
                        if (!requestFocus()) this@apply.playWhenReady = false
                    } else if (ignoreNextPauseAbandon) {
                        ignoreNextPauseAbandon = false
                    } else abandonFocus()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    // Random mode is an independent draw at every track boundary.
                    // Unlike ExoPlayer's shuffle order, this allows the same song twice.
                    val mode = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .getString(KEY_MODE, PlaybackMode.REPEAT_ALL.name)
                    val exo = this@apply
                    if (playbackState == Player.STATE_ENDED && mode == PlaybackMode.SHUFFLE.name && exo.mediaItemCount > 0) {
                        val next = chooseRandomIndex(exo.mediaItemCount)
                        exo.seekTo(next, 0L)
                        exo.playWhenReady = true
                    }
                }
            })
        }
        val savedMode = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MODE, PlaybackMode.REPEAT_ALL.name)
        player?.repeatMode = when (savedMode) {
            PlaybackMode.REPEAT_ONE.name -> Player.REPEAT_MODE_ONE
            PlaybackMode.SHUFFLE.name -> Player.REPEAT_MODE_OFF
            else -> Player.REPEAT_MODE_ALL
        }
        // All external transport controls (notification, lock screen and Bluetooth)
        // go through this wrapper, so Shuffle means an independent random draw too.
        session = MediaSession.Builder(this, ShuffleAwarePlayer(player!!) {
            getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_MODE, PlaybackMode.REPEAT_ALL.name) == PlaybackMode.SHUFFLE.name
        }).build()
    }

    private fun requestFocus(): Boolean {
        if (hasFocus) return true
        val platformAttrs = PlatformAudioAttributes.Builder()
            .setUsage(PlatformAudioAttributes.USAGE_MEDIA)
            .setContentType(PlatformAudioAttributes.CONTENT_TYPE_MUSIC).build()
        val result = if (Build.VERSION.SDK_INT >= 26) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(platformAttrs)
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener(focusListener, Handler(Looper.getMainLooper())).build()
            focusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
        hasFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasFocus
    }

    private fun abandonFocus() {
        if (!::audioManager.isInitialized) return
        if (Build.VERSION.SDK_INT >= 26) focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusListener)
        }
        hasFocus = false
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        abandonFocus()
        session?.release()
        player?.release()
        session = null
        player = null
        super.onDestroy()
    }
}


/** Applies the app's true-random next-track rule to MediaSession transport commands. */
@UnstableApi
private class ShuffleAwarePlayer(
    delegate: Player,
    private val isShuffleEnabled: () -> Boolean
) : ForwardingPlayer(delegate) {
    private fun canRandomize(): Boolean = isShuffleEnabled() && mediaItemCount > 0

    // Keep next commands advertised for connected controllers even if the mode
    // or queue changes later. hasNextMediaItem() still reflects actual availability.
    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon()
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .build()

    override fun isCommandAvailable(command: Int): Boolean {
        if (command == Player.COMMAND_SEEK_TO_NEXT ||
            command == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) return true
        return super.isCommandAvailable(command)
    }

    override fun hasNextMediaItem(): Boolean = canRandomize() || super.hasNextMediaItem()

    override fun seekToNext() {
        if (canRandomize()) seekRandomly() else super.seekToNext()
    }

    override fun seekToNextMediaItem() {
        if (canRandomize()) seekRandomly() else super.seekToNextMediaItem()
    }

    private fun seekRandomly() {
        // Current item is intentionally included: consecutive repeats are valid.
        seekTo(chooseRandomIndex(mediaItemCount), 0L)
    }
}

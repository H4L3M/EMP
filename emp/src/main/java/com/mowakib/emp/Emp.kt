package com.mowakib.emp

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.util.Pair
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.MediaItem.AdsConfiguration
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.ext.ima.ImaAdsLoader
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer.DecoderInitializationException
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil.DecoderQueryException
import com.google.android.exoplayer2.source.BehindLiveWindowException
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.source.MediaSourceFactory
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.source.ads.AdsLoader
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector.ParametersBuilder
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.MappedTrackInfo
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.ui.DebugTextViewHelper
import com.google.android.exoplayer2.ui.PlayerNotificationManager
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.util.ErrorMessageProvider
import com.google.android.exoplayer2.util.EventLogger
import com.mowakib.emp.network.EmpDownloadTracker
import com.mowakib.emp.notification.NOTIFICATION_LARGE_ICON_SIZE
import com.mowakib.emp.utils.IntentUtil
import com.mowakib.emp.utils.Util
import kotlinx.coroutines.*
import java.util.*

open class Emp : PlayerNotificationManager.NotificationListener {
    private lateinit var playerView: StyledPlayerView
    var debugRootView: LinearLayout? = null
    var debugTextView: TextView? = null
    var player: SimpleExoPlayer? = null
    private val isShowingTrackSelectionDialog = false
    private val selectTracksButton: Button? = null
    private var mediaItems: List<MediaItem>? = null
    private var trackSelector: DefaultTrackSelector? = null
    private val debugViewHelper: DebugTextViewHelper? = null
    private var lastSeenTrackGroupArray: TrackGroupArray? = null


    //scope
    private val serviceJob = SupervisorJob()
    private var serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    //==========================================Start Notification==================================
    lateinit var playerNotificationManager: PlayerNotificationManager

    var currentIconUri: Uri? = null
    var currentBitmap: Bitmap? = null

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var controller: MediaControllerCompat

    //============================================End Notification==================================


    // For ad playback only.
    private var adsLoader: AdsLoader? = null
    var urlSource: String? = null
        private set

    //----------------------------------------------------------------------------------------------
    private lateinit var context: Context

    //----------------------------------------------------------------------------------------------

    fun init(
        context: Context,
        lifecycleScope: CoroutineScope,
        playerView: StyledPlayerView
    ): Emp {
        this.context = context
        this.playerView = playerView
        dataSourceFactory = Util.getDataSourceFactory( /* context= */this.context)
        this.playerView.setErrorMessageProvider(PlayerErrorMessageProvider(this.context))
        this.playerView.requestFocus()

        serviceScope = lifecycleScope

        mediaSession = MediaSessionCompat(context, "MusicService")
            .apply {
//                setSessionActivity(sessionActivityPendingIntent)
                isActive = true
            }
        controller = MediaControllerCompat(context, mediaSession.sessionToken)

        playerNotificationManager = PlayerNotificationManager.createWithNotificationChannel(
            context,
            "channel_id",
            R.string.application_name,
            R.string.notification_channel_description,
            123,
            object : PlayerNotificationManager.MediaDescriptionAdapter {
                @SuppressLint("UnspecifiedImmutableFlag")
                override fun createCurrentContentIntent(player: Player): PendingIntent? {
//                    val intent = Intent(context, MainActivity::class.java)
//                    return PendingIntent.getActivity(this@MainActivity,0,intent, PendingIntent.FLAG_UPDATE_CURRENT)
                    return null
                }

                override fun getCurrentContentText(player: Player): String {
                    //return mSongList[player.currentWindowIndex].descripation // descrption
                    return "Description"
                }

                override fun getCurrentContentTitle(player: Player): String {
                    return "Title"
                }

                override fun getCurrentLargeIcon(
                    player: Player,
                    callback: PlayerNotificationManager.BitmapCallback
                ): Bitmap? {

                    val iconUri =
                        Uri.parse("http://simpleicon.com/wp-content/uploads/radio.png")
                    return if (currentIconUri != iconUri || currentBitmap == null) {

                        // Cache the bitmap for the current song so that successive calls to
                        // `getCurrentLargeIcon` don't cause the bitmap to be recreated.
                        currentIconUri = iconUri
                        serviceScope.launch {
                            currentBitmap = iconUri?.let {
                                resolveUriAsBitmap(it)
                            }
                            currentBitmap?.let { callback.onBitmap(it) }
                        }
                        null
                    } else {
                        currentBitmap
                    }
                }

            },
            this
        )
        return this
    }

    private suspend fun resolveUriAsBitmap(uri: Uri): Bitmap? {
        return withContext(Dispatchers.IO) {
            // Block on downloading artwork.
            Glide.with(context).applyDefaultRequestOptions(glideOptions)
                .asBitmap()
                .load(uri)
                .submit(NOTIFICATION_LARGE_ICON_SIZE, NOTIFICATION_LARGE_ICON_SIZE)
                .get()
        }
    }

    private fun saveInstance(savedInstanceState: Bundle?): Emp {
        if (savedInstanceState != null) {
            trackSelectorParameters =
                savedInstanceState.getParcelable(KEY_TRACK_SELECTOR_PARAMETERS)
            startAutoPlay = savedInstanceState.getBoolean(KEY_AUTO_PLAY)
            startWindow = savedInstanceState.getInt(KEY_WINDOW)
            startPosition = savedInstanceState.getLong(KEY_POSITION)
        } else {
            val builder = ParametersBuilder( /* context= */context)
            trackSelectorParameters = builder.build()
            clearStartPosition()
        }
        return this
    }

    //    public void onNewClick(Intent intent) {
    //        releasePlayer();
    //        releaseAdsLoader();
    //        clearStartPosition();
    //        setIntent(intent);
    //    }
    fun start() {
        if (SDK_INT > 23) {
            init(context, serviceScope, playerView)
            playerView.onResume()
        }
    }

    fun resume() {
        if (SDK_INT <= 23 || player == null) {
            init(context, serviceScope, playerView)
            playerView.onResume()
        }
    }

    fun pause() {
        if (SDK_INT <= 23) {
            playerView.onPause()
            release()
        }
    }

    fun stop() {
        if (SDK_INT > 23) {
            playerView.onPause()
            release()
        }
    }

    fun destroy() {
        releaseAdsLoader()
    }

    //    @Override
    //    public void onRequestPermissionsResult(
    //            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    //        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    //        if (grantResults.length == 0) {
    //            // Empty results are triggered if a permission is requested while another request was already
    //            // pending and can be safely ignored in this case.
    //            return;
    //        }
    //        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
    //            initializePlayer();
    //        } else {
    //            showToast(R.string.storage_permission_denied);
    //            finish();
    //        }
    //    }
    //    @Override
    //    public void onSaveInstanceState(@NonNull Bundle outState) {
    //        super.onSaveInstanceState(outState);
    //        updateTrackSelectorParameters();
    //        updateStartPosition();
    //        outState.putParcelable(KEY_TRACK_SELECTOR_PARAMETERS, trackSelectorParameters);
    //        outState.putBoolean(KEY_AUTO_PLAY, startAutoPlay);
    //        outState.putInt(KEY_WINDOW, startWindow);
    //        outState.putLong(KEY_POSITION, startPosition);
    //    }
    // Activity input
    //    @Override
    //    public boolean dispatchKeyEvent(KeyEvent event) {
    //        // See whether the player view wants to handle media or DPAD keys events.
    //        return playerView.dispatchKeyEvent(event) || super.dispatchKeyEvent(event);
    //    }
    // OnClickListener methods
    //    @Override
    //    public void onClick(View view) {
    //        if (view == selectTracksButton
    //                && !isShowingTrackSelectionDialog
    //                && TrackSelectionDialog.willHaveContent(trackSelector)) {
    //            isShowingTrackSelectionDialog = true;
    //            TrackSelectionDialog trackSelectionDialog =
    //                    TrackSelectionDialog.createForTrackSelector(
    //                            trackSelector,
    //                            /* onDismissListener= */ dismissedDialog -> isShowingTrackSelectionDialog = false);
    //            trackSelectionDialog.show(getSupportFragmentManager(), /* tag= */ null);
    //        }
    //    }
    // PlayerControlView.VisibilityListener implementation
    //    @Override
    //    public void onVisibilityChange(int visibility) {
    //        debugRootView.setVisibility(visibility);
    //    }
    fun load(url: String?): Emp {
        urlSource = url
        playerNotificationManager.setPlayer(player)
        if (player == null) {
//      Intent intent = getIntent();
//
//      mediaItems = createMediaItems(intent);
//      if (mediaItems.isEmpty()) {
//        return false;
//      }

//            boolean preferExtensionDecoders =
//                    intent.getBooleanExtra(IntentUtil.PREFER_EXTENSION_DECODERS_EXTRA, false);
//            RenderersFactory renderersFactory =
//                    DemoUtil.buildRenderersFactory(/* context= */ this, preferExtensionDecoders);
            val mediaSourceFactory: MediaSourceFactory =
                DefaultMediaSourceFactory(dataSourceFactory!!)
                    .setAdsLoaderProvider { adsConfiguration: AdsConfiguration ->
                        getAdsLoader(
                            adsConfiguration
                        )
                    }
                    .setAdViewProvider(playerView)
            trackSelector = DefaultTrackSelector( /* context= */context)
            //            trackSelector.setParameters(trackSelectorParameters);
            lastSeenTrackGroupArray = null
            player = SimpleExoPlayer.Builder( /* context= */context) //renderersFactory)
                .setMediaSourceFactory(mediaSourceFactory)
                .setTrackSelector(trackSelector!!)
                .build()
            player!!.addListener(PlayerEventListener())
            player!!.addAnalyticsListener(EventLogger(trackSelector))
            player!!.setAudioAttributes(AudioAttributes.DEFAULT,  /* handleAudioFocus= */true)
            player!!.playWhenReady = startAutoPlay
            playerView.player = player
            //            debugViewHelper = new DebugTextViewHelper(mPlayer, debugTextView);
//            debugViewHelper.start();
        }
        val haveStartPosition = startWindow != C.INDEX_UNSET
        if (haveStartPosition) {
            player!!.seekTo(startWindow, startPosition)
        }
        //        mPlayer.setMediaItems(mediaItems, /* resetPosition= */ !haveStartPosition);
        val mediaItem = MediaItem.fromUri(
            urlSource!!
        )
        player!!.setMediaItem(mediaItem) /* resetPosition= */ //!haveStartPosition);
        player!!.prepare()
        player!!.play()
        //        updateButtonVisibility();
        return this
    }

    //    private List<MediaItem> createMediaItems(Intent intent) {
    //        String action = intent.getAction();
    //        boolean actionIsListView = IntentUtil.ACTION_VIEW_LIST.equals(action);
    //        if (!actionIsListView && !IntentUtil.ACTION_VIEW.equals(action)) {
    //            showToast(mContext.getString(R.string.unexpected_intent_action, action));
    //            finish();
    //            return Collections.emptyList();
    //        }
    //
    //        List<MediaItem> mediaItems =
    //                createMediaItems(intent, DemoUtil.getDownloadTracker(/* context= */ this));
    //        boolean hasAds = false;
    //        for (int i = 0; i < mediaItems.size(); i++) {
    //            MediaItem mediaItem = mediaItems.get(i);
    //
    //            if (!Util.checkCleartextTrafficPermitted(mediaItem)) {
    //                showToast(R.string.error_cleartext_not_permitted);
    //                finish();
    //                return Collections.emptyList();
    //            }
    //            if (Util.maybeRequestReadExternalStoragePermission(/* activity= */ this, mediaItem)) {
    //                // The player will be reinitialized if the permission is granted.
    //                return Collections.emptyList();
    //            }
    //
    //            MediaItem.DrmConfiguration drmConfiguration =
    //                    checkNotNull(mediaItem.playbackProperties).drmConfiguration;
    //            if (drmConfiguration != null) {
    //                if (Util.SDK_INT < 18) {
    //                    showToast(R.string.error_drm_unsupported_before_api_18);
    //                    finish();
    //                    return Collections.emptyList();
    //                } else if (!FrameworkMediaDrm.isCryptoSchemeSupported(drmConfiguration.uuid)) {
    //                    showToast(R.string.error_drm_unsupported_scheme);
    //                    finish();
    //                    return Collections.emptyList();
    //                }
    //            }
    //            hasAds |= mediaItem.playbackProperties.adsConfiguration != null;
    //        }
    //        if (!hasAds) {
    //            releaseAdsLoader();
    //        }
    //        return mediaItems;
    //    }
    private fun getAdsLoader(adsConfiguration: AdsConfiguration): AdsLoader {
        // The ads loader is reused for multiple playbacks, so that ad playback can resume.
        if (adsLoader == null) {
            adsLoader = ImaAdsLoader.Builder( /* context= */context).build()
        }
        adsLoader!!.setPlayer(player)
        return adsLoader as AdsLoader
    }

    private fun release() {
        if (player != null) {
            updateTrackSelectorParameters()
            updateStartPosition()
            //            debugViewHelper.stop();
//            debugViewHelper = null;
            player!!.release()
            player = null
            mediaItems = emptyList()
            trackSelector = null
        }
        if (adsLoader != null) {
            adsLoader!!.setPlayer(null)
        }
    }

    private fun releaseAdsLoader() {
        if (adsLoader != null) {
            adsLoader!!.release()
            adsLoader = null
            playerView!!.overlayFrameLayout!!.removeAllViews()
        }
    }

    private fun updateTrackSelectorParameters() {
        if (trackSelector != null) {
            trackSelectorParameters = trackSelector!!.parameters
        }
    }

    private fun updateStartPosition() {
        if (player != null) {
            startAutoPlay = player!!.playWhenReady
            startWindow = player!!.currentWindowIndex
            startPosition = Math.max(0, player!!.contentPosition)
        }
    }

    // User controls
    //    private void updateButtonVisibility() {
    //        selectTracksButton.setEnabled(
    //                mPlayer != null && TrackSelectionDialog.willHaveContent(trackSelector));
    //    }
    //    private void showControls() {
    //        debugRootView.setVisibility(View.VISIBLE);
    //    }
    private fun showToast(messageId: Int) {
        showToast(context.getString(messageId))
    }

    private fun showToast(message: String) {
        Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show()
    }


    private inner class PlayerEventListener : Player.EventListener {
        override fun onPlaybackStateChanged(@Player.State playbackState: Int) {
//            if (playbackState == Player.STATE_ENDED) {
//                showControls();
//            }
//            updateButtonVisibility();
        }

        override fun onPlayerError(e: ExoPlaybackException) {
            if (isBehindLiveWindow(e)) {
                clearStartPosition()
                //                initializePlayer();
                load(urlSource)
            } else {
//                updateButtonVisibility();
//                showControls();
            }
        }

        override fun onTracksChanged(
            trackGroups: TrackGroupArray, trackSelections: TrackSelectionArray
        ) {
//            updateButtonVisibility();
            if (trackGroups !== lastSeenTrackGroupArray) {
                val mappedTrackInfo = trackSelector!!.currentMappedTrackInfo
                if (mappedTrackInfo != null) {
                    if (mappedTrackInfo.getTypeSupport(C.TRACK_TYPE_VIDEO)
                        == MappedTrackInfo.RENDERER_SUPPORT_UNSUPPORTED_TRACKS
                    ) {
                        showToast(R.string.error_unsupported_video)
                    }
                    if (mappedTrackInfo.getTypeSupport(C.TRACK_TYPE_AUDIO)
                        == MappedTrackInfo.RENDERER_SUPPORT_UNSUPPORTED_TRACKS
                    ) {
                        showToast(R.string.error_unsupported_audio)
                    }
                }
                lastSeenTrackGroupArray = trackGroups
            }
        }
    }

    private class PlayerErrorMessageProvider(private val mContext: Context?) :
        ErrorMessageProvider<ExoPlaybackException> {
        override fun getErrorMessage(e: ExoPlaybackException): Pair<Int, String> {
            var errorString = mContext!!.getString(R.string.error_generic)
            if (e.type == ExoPlaybackException.TYPE_RENDERER) {
                val cause = e.rendererException
                if (cause is DecoderInitializationException) {
                    // Special case for decoder initialization failures.
                    val decoderInitializationException = cause
                    errorString = if (decoderInitializationException.codecInfo == null) {
                        if (decoderInitializationException.cause is DecoderQueryException) {
                            mContext.getString(R.string.error_querying_decoders)
                        } else if (decoderInitializationException.secureDecoderRequired) {
                            mContext.getString(
                                R.string.error_no_secure_decoder,
                                decoderInitializationException.mimeType
                            )
                        } else {
                            mContext.getString(
                                R.string.error_no_decoder,
                                decoderInitializationException.mimeType
                            )
                        }
                    } else {
                        mContext.getString(
                            R.string.error_instantiating_decoder,
                            decoderInitializationException.codecInfo!!.name
                        )
                    }
                }
            }
            return Pair.create(0, errorString)
        }
    }

    companion object {
        // Saved instance state keys.
        private const val KEY_TRACK_SELECTOR_PARAMETERS = "track_selector_parameters"
        private const val KEY_WINDOW = "window"
        private const val KEY_POSITION = "position"
        private const val KEY_AUTO_PLAY = "auto_play"
        private var dataSourceFactory: DataSource.Factory? = null
        private var trackSelectorParameters: DefaultTrackSelector.Parameters? = null
        private var startAutoPlay = false
        private var startWindow = 0
        private var startPosition: Long = 0
        private var isForegroundService = false


        fun get() = Emp()


        protected fun clearStartPosition() {
            startAutoPlay = true
            startWindow = C.INDEX_UNSET
            startPosition = C.TIME_UNSET
        }

        private fun isBehindLiveWindow(e: ExoPlaybackException): Boolean {
            if (e.type != ExoPlaybackException.TYPE_SOURCE) {
                return false
            }
            var cause: Throwable? = e.sourceException
            while (cause != null) {
                if (cause is BehindLiveWindowException) {
                    return true
                }
                cause = cause.cause
            }
            return false
        }

        private fun createMediaItems(
            intent: Intent,
            downloadTracker: EmpDownloadTracker
        ): List<MediaItem> {
            val mediaItems: MutableList<MediaItem> = ArrayList()
            for (item in IntentUtil.createMediaItemsFromIntent(intent)) {
                val downloadRequest =
                    downloadTracker.getDownloadRequest(Assertions.checkNotNull(item.playbackProperties).uri)
                if (downloadRequest != null) {
                    val builder = item.buildUpon()
                    builder
                        .setMediaId(downloadRequest.id)
                        .setUri(downloadRequest.uri)
                        .setCustomCacheKey(downloadRequest.customCacheKey)
                        .setMimeType(downloadRequest.mimeType)
                        .setStreamKeys(downloadRequest.streamKeys)
                        .setDrmKeySetId(downloadRequest.keySetId)
                        .setDrmLicenseRequestHeaders(getDrmRequestHeaders(item))
                    mediaItems.add(builder.build())
                } else {
                    mediaItems.add(item)
                }
            }
            return mediaItems
        }

        private fun getDrmRequestHeaders(item: MediaItem): Map<String, String>? {
            assert(item.playbackProperties != null)
            val drmConfiguration = item.playbackProperties!!.drmConfiguration
            return drmConfiguration?.requestHeaders
        }
    }
}

val glideOptions = RequestOptions()
    .fallback(R.drawable.ic_banner)
    .diskCacheStrategy(DiskCacheStrategy.DATA)
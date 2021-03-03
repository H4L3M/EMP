package com.mowakib.emp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Pair;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.ext.ima.ImaAdsLoader;
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer.DecoderInitializationException;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil.DecoderQueryException;
import com.google.android.exoplayer2.offline.DownloadRequest;
import com.google.android.exoplayer2.source.BehindLiveWindowException;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.source.MediaSourceFactory;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.ads.AdsLoader;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.MappedTrackInfo;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.ui.DebugTextViewHelper;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.util.ErrorMessageProvider;
import com.google.android.exoplayer2.util.EventLogger;
import com.google.android.exoplayer2.util.Util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;


public final class Emp {

    // Saved instance state keys.

    private static final String KEY_TRACK_SELECTOR_PARAMETERS = "track_selector_parameters";
    private static final String KEY_WINDOW = "window";
    private static final String KEY_POSITION = "position";
    private static final String KEY_AUTO_PLAY = "auto_play";

    @SuppressLint("StaticFieldLeak")
    private static Context mContext;

    protected StyledPlayerView mPlayerView;
    protected LinearLayout debugRootView;
    protected TextView debugTextView;
    protected SimpleExoPlayer mPlayer;

    private boolean isShowingTrackSelectionDialog;
    private Button selectTracksButton;
    private static DataSource.Factory dataSourceFactory;
    private List<MediaItem> mediaItems;
    private DefaultTrackSelector trackSelector;
    private static DefaultTrackSelector.Parameters trackSelectorParameters;
    private DebugTextViewHelper debugViewHelper;
    private TrackGroupArray lastSeenTrackGroupArray;
    private static boolean startAutoPlay;
    private static int startWindow;
    private static long startPosition;

    // For ad playback only.

    private AdsLoader adsLoader;

    private String mUrlSource;

    public static Emp with(Context context) {
        mContext = context.getApplicationContext();
        return new Emp();
    }


    public Emp init(@NonNull StyledPlayerView playerView) {
        mPlayerView = playerView;

        dataSourceFactory = DemoUtil.getDataSourceFactory(/* context= */ mContext);

//    debugRootView = findViewById(R.id.controls_root);
//    debugTextView = findViewById(R.id.debug_text_view);
//    selectTracksButton = findViewById(R.id.select_tracks_button);
//    selectTracksButton.setOnClickListener(this);
//
//    playerView = findViewById(R.id.player_view);
//        mPlayerView.setControllerVisibilityListener(this);
        mPlayerView.setErrorMessageProvider(new PlayerErrorMessageProvider());
        mPlayerView.requestFocus();
        return this;
    }

    private Emp saveInstance(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            trackSelectorParameters = savedInstanceState.getParcelable(KEY_TRACK_SELECTOR_PARAMETERS);
            startAutoPlay = savedInstanceState.getBoolean(KEY_AUTO_PLAY);
            startWindow = savedInstanceState.getInt(KEY_WINDOW);
            startPosition = savedInstanceState.getLong(KEY_POSITION);
        } else {
            DefaultTrackSelector.ParametersBuilder builder =
                    new DefaultTrackSelector.ParametersBuilder(/* context= */ mContext);
            trackSelectorParameters = builder.build();
            clearStartPosition();
        }
        return this;
    }

//    @Override
//    public void onNewIntent(Intent intent) {
//        super.onNewIntent(intent);
//        releasePlayer();
//        releaseAdsLoader();
//        clearStartPosition();
//        setIntent(intent);
//    }


    public void start() {
        if (Util.SDK_INT > 23) {
            init(mPlayerView);
            if (mPlayerView != null) {
                mPlayerView.onResume();
            }
        }
    }

//    @Override
//    public void onResume() {
//        super.onResume();
//        if (Util.SDK_INT <= 23 || player == null) {
//            initializePlayer();
//            if (playerView != null) {
//                playerView.onResume();
//            }
//        }
//    }

//    @Override
//    public void onPause() {
//        super.onPause();
//        if (Util.SDK_INT <= 23) {
//            if (playerView != null) {
//                playerView.onPause();
//            }
//            releasePlayer();
//        }
//    }

//    @Override
//    public void onStop() {
//        super.onStop();
//        if (Util.SDK_INT > 23) {
//            if (playerView != null) {
//                playerView.onPause();
//            }
//            releasePlayer();
//        }
//    }

//    @Override
//    public void onDestroy() {
//        super.onDestroy();
//        releaseAdsLoader();
//    }

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

    // Internal methods

//    protected void setContentView() {
//        setContentView(R.layout.player_activity);
//    }

    /**
     * @return Whether initialization was successful.
     */
    public Emp load(String url) {
        mUrlSource = url;

        if (mPlayer == null) {
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
            MediaSourceFactory mediaSourceFactory =
                    new DefaultMediaSourceFactory(dataSourceFactory)
                            .setAdsLoaderProvider(this::getAdsLoader)
                            .setAdViewProvider(mPlayerView);

            trackSelector = new DefaultTrackSelector(/* context= */ mContext);
//            trackSelector.setParameters(trackSelectorParameters);
            lastSeenTrackGroupArray = null;
            mPlayer =
                    new SimpleExoPlayer.Builder(/* context= */ mContext) //renderersFactory)
                            .setMediaSourceFactory(mediaSourceFactory)
                            .setTrackSelector(trackSelector)
                            .build();
            mPlayer.addListener(new PlayerEventListener());
            mPlayer.addAnalyticsListener(new EventLogger(trackSelector));
            mPlayer.setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus= */ true);
            mPlayer.setPlayWhenReady(startAutoPlay);
            mPlayerView.setPlayer(mPlayer);
//            debugViewHelper = new DebugTextViewHelper(mPlayer, debugTextView);
//            debugViewHelper.start();
        }
        boolean haveStartPosition = startWindow != C.INDEX_UNSET;
        if (haveStartPosition) {
            mPlayer.seekTo(startWindow, startPosition);
        }
//        mPlayer.setMediaItems(mediaItems, /* resetPosition= */ !haveStartPosition);
        MediaItem mediaItem = MediaItem.fromUri(mUrlSource);
        mPlayer.setMediaItem(mediaItem); /* resetPosition= */ //!haveStartPosition);

        mPlayer.prepare();
        mPlayer.play();
//        updateButtonVisibility();
        return this;
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

    private AdsLoader getAdsLoader(MediaItem.AdsConfiguration adsConfiguration) {
        // The ads loader is reused for multiple playbacks, so that ad playback can resume.
        if (adsLoader == null) {
            adsLoader = new ImaAdsLoader.Builder(/* context= */ mContext).build();
        }
        adsLoader.setPlayer(mPlayer);
        return adsLoader;
    }

    protected void release() {
        if (mPlayer != null) {
            updateTrackSelectorParameters();
            updateStartPosition();
//            debugViewHelper.stop();
//            debugViewHelper = null;
            mPlayer.release();
            mPlayer = null;
            mediaItems = Collections.emptyList();
            trackSelector = null;
        }
        if (adsLoader != null) {
            adsLoader.setPlayer(null);
        }
    }

    private void releaseAdsLoader() {
        if (adsLoader != null) {
            adsLoader.release();
            adsLoader = null;
            mPlayerView.getOverlayFrameLayout().removeAllViews();
        }
    }

    private void updateTrackSelectorParameters() {
        if (trackSelector != null) {
            trackSelectorParameters = trackSelector.getParameters();
        }
    }

    private void updateStartPosition() {
        if (mPlayer != null) {
            startAutoPlay = mPlayer.getPlayWhenReady();
            startWindow = mPlayer.getCurrentWindowIndex();
            startPosition = Math.max(0, mPlayer.getContentPosition());
        }
    }

    protected static void clearStartPosition() {
        startAutoPlay = true;
        startWindow = C.INDEX_UNSET;
        startPosition = C.TIME_UNSET;
    }

    // User controls

//    private void updateButtonVisibility() {
//        selectTracksButton.setEnabled(
//                mPlayer != null && TrackSelectionDialog.willHaveContent(trackSelector));
//    }

//    private void showControls() {
//        debugRootView.setVisibility(View.VISIBLE);
//    }

    private void showToast(int messageId) {
        showToast(mContext.getString(messageId));
    }

    private void showToast(String message) {
        Toast.makeText(mContext.getApplicationContext(), message, Toast.LENGTH_LONG).show();
    }

    private static boolean isBehindLiveWindow(ExoPlaybackException e) {
        if (e.type != ExoPlaybackException.TYPE_SOURCE) {
            return false;
        }
        Throwable cause = e.getSourceException();
        while (cause != null) {
            if (cause instanceof BehindLiveWindowException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private class PlayerEventListener implements Player.EventListener {

        @Override
        public void onPlaybackStateChanged(@Player.State int playbackState) {
//            if (playbackState == Player.STATE_ENDED) {
//                showControls();
//            }
//            updateButtonVisibility();
        }

        @Override
        public void onPlayerError(@NonNull ExoPlaybackException e) {
            if (isBehindLiveWindow(e)) {
                clearStartPosition();
//                initializePlayer();
                load(mUrlSource);
            } else {
//                updateButtonVisibility();
//                showControls();
            }
        }

        @Override
        @SuppressWarnings("ReferenceEquality")
        public void onTracksChanged(
                @NonNull TrackGroupArray trackGroups, @NonNull TrackSelectionArray trackSelections) {
//            updateButtonVisibility();
            if (trackGroups != lastSeenTrackGroupArray) {
                MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
                if (mappedTrackInfo != null) {
                    if (mappedTrackInfo.getTypeSupport(C.TRACK_TYPE_VIDEO)
                            == MappedTrackInfo.RENDERER_SUPPORT_UNSUPPORTED_TRACKS) {
                        showToast(R.string.error_unsupported_video);
                    }
                    if (mappedTrackInfo.getTypeSupport(C.TRACK_TYPE_AUDIO)
                            == MappedTrackInfo.RENDERER_SUPPORT_UNSUPPORTED_TRACKS) {
                        showToast(R.string.error_unsupported_audio);
                    }
                }
                lastSeenTrackGroupArray = trackGroups;
            }
        }
    }

    private static class PlayerErrorMessageProvider implements ErrorMessageProvider<ExoPlaybackException> {

        @Override
        @NonNull
        public Pair<Integer, String> getErrorMessage(@NonNull ExoPlaybackException e) {
            String errorString = mContext.getString(R.string.error_generic);
            if (e.type == ExoPlaybackException.TYPE_RENDERER) {
                Exception cause = e.getRendererException();
                if (cause instanceof DecoderInitializationException) {
                    // Special case for decoder initialization failures.
                    DecoderInitializationException decoderInitializationException =
                            (DecoderInitializationException) cause;
                    if (decoderInitializationException.codecInfo == null) {
                        if (decoderInitializationException.getCause() instanceof DecoderQueryException) {
                            errorString = mContext.getString(R.string.error_querying_decoders);
                        } else if (decoderInitializationException.secureDecoderRequired) {
                            errorString =
                                    mContext.getString(
                                            R.string.error_no_secure_decoder, decoderInitializationException.mimeType);
                        } else {
                            errorString =
                                    mContext.getString(R.string.error_no_decoder, decoderInitializationException.mimeType);
                        }
                    } else {
                        errorString =
                                mContext.getString(
                                        R.string.error_instantiating_decoder,
                                        decoderInitializationException.codecInfo.name);
                    }
                }
            }
            return Pair.create(0, errorString);
        }
    }

    private static List<MediaItem> createMediaItems(Intent intent, DownloadTracker downloadTracker) {
        List<MediaItem> mediaItems = new ArrayList<>();
        for (MediaItem item : IntentUtil.createMediaItemsFromIntent(intent)) {
            @Nullable
            DownloadRequest downloadRequest =
                    downloadTracker.getDownloadRequest(checkNotNull(item.playbackProperties).uri);
            if (downloadRequest != null) {
                MediaItem.Builder builder = item.buildUpon();
                builder
                        .setMediaId(downloadRequest.id)
                        .setUri(downloadRequest.uri)
                        .setCustomCacheKey(downloadRequest.customCacheKey)
                        .setMimeType(downloadRequest.mimeType)
                        .setStreamKeys(downloadRequest.streamKeys)
                        .setDrmKeySetId(downloadRequest.keySetId)
                        .setDrmLicenseRequestHeaders(getDrmRequestHeaders(item));

                mediaItems.add(builder.build());
            } else {
                mediaItems.add(item);
            }
        }
        return mediaItems;
    }

    @Nullable
    private static Map<String, String> getDrmRequestHeaders(MediaItem item) {
        MediaItem.DrmConfiguration drmConfiguration = item.playbackProperties.drmConfiguration;
        return drmConfiguration != null ? drmConfiguration.requestHeaders : null;
    }
}

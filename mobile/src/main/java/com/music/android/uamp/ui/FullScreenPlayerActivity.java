/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.music.android.uamp.ui;

import android.app.FragmentTransaction;
import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v7.widget.SearchView;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.loveplusplus.update.UpdateChecker;
import com.music.android.uamp.AlbumArtCache;
import com.music.android.uamp.AnalyticsApplication;
import com.music.android.uamp.MusicService;
import com.music.android.uamp.R;
import com.music.android.uamp.model.MusicProvider;
import com.music.android.uamp.model.MusicProviderSource;
import com.music.android.uamp.utils.LogHelper;
import com.music.android.uamp.utils.MediaIDHelper;
import com.music.android.uamp.utils.NetworkHelper;
import com.pnikosis.materialishprogress.ProgressWheel;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static com.music.android.uamp.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_DOWNLOAD;
import static com.music.android.uamp.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_DOWNLOAD_VIDEOID;
import static com.music.android.uamp.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_VIDEOID;

/**
 * A full screen player that shows the current playing music with a background image
 * depicting the album art. The activity also has controls to seek/pause/play the audio.
 */
public class FullScreenPlayerActivity extends ActionBarCastActivity implements MediaBrowserFragment.MediaFragmentListener {
    private static final String TAG = LogHelper.makeLogTag(FullScreenPlayerActivity.class);
    private static final long PROGRESS_UPDATE_INTERNAL = 1000;
    private static final long PROGRESS_UPDATE_INITIAL_INTERVAL = 100;
    private static final String FRAGMENT_TAG = "uamp_list_container";
    private final Handler mHandler = new Handler();
    private final ScheduledExecutorService mExecutorService =
            Executors.newSingleThreadScheduledExecutor();
    private ImageView mSkipPrev;
    private ImageView mSkipNext;
    private ImageView mFastFoward;
    private ImageView mRewind;
    private ImageView mDownLoad;
    private ImageView mPlayPause;
    private TextView mStart;
    private TextView mEnd;
    private SeekBar mSeekbar;
    private TextView mLine1;
    private TextView mLine2;
    private TextView mLine3;
    private ProgressBar mLoading;
    private View mControllers;
    private Drawable mPauseDrawable;
    private Drawable mPlayDrawable;
    private ImageView mBackgroundImage;
    private String mCurrentArtUrl;
    private MediaBrowserCompat mMediaBrowser;
    private ScheduledFuture<?> mScheduleFuture;
    private PlaybackStateCompat mLastPlaybackState;
    private final Runnable mUpdateProgressTask = new Runnable() {
        @Override
        public void run() {
            updateProgress();
        }
    };
    private List<Long> durations = new ArrayList();
    private final MediaControllerCompat.Callback mCallback = new MediaControllerCompat.Callback() {
        @Override
        public void onPlaybackStateChanged(@NonNull PlaybackStateCompat state) {
            LogHelper.d(TAG, "onPlaybackstate changed", state);
            updatePlaybackState(state);
        }

        @Override
        public void onMetadataChanged(MediaMetadataCompat metadata) {
            if (metadata != null) {
                updateMediaDescription(metadata.getDescription());
                updateDuration(metadata);
                String durationStr = metadata.getString(MusicProviderSource.CUSTOM_METADATA_TRACKS_DURATIONS);
                if (durationStr != null) {
                    String durs[] = durationStr.split(",");
                    for (String str : durs) {
                        if (str == null || str.equalsIgnoreCase("")) {
                            continue;
                        }
                        durations.add(Long.parseLong(str));
                    }
                    if (durations.size() > 2) {
                        mFastFoward.setVisibility(View.VISIBLE);
                        mRewind.setVisibility(View.VISIBLE);
                    }
                }
            }
        }
    };
    private final MediaBrowserCompat.ConnectionCallback mConnectionCallback =
            new MediaBrowserCompat.ConnectionCallback() {
                @Override
                public void onConnected() {
                    LogHelper.d(TAG, "onConnected");
                    try {
                        connectToSession(mMediaBrowser.getSessionToken());
                        getBrowseFragment().onConnected();
                    } catch (RemoteException e) {
                        LogHelper.e(TAG, e, "could not connect media controller");
                    }
                }
            };
    private Boolean isDownLoading = false;
    //private Tracker mTracker;
    private FirebaseAnalytics mFirebaseAnalytics;
    private ProgressWheel mProgressWeel;
    private long fastForwardData;

    private void navigateToBrowser(String mediaId) {
        LogHelper.d(TAG, "navigateToBrowser, mediaId=" + mediaId);
        MediaBrowserFragment fragment = getBrowseFragment();

        if (fragment == null || !TextUtils.equals(fragment.getMediaId(), mediaId)) {
            fragment = new MediaBrowserFragment();
            fragment.setMediaId(mediaId);
            FragmentTransaction transaction = getFragmentManager().beginTransaction();
            transaction.setCustomAnimations(
                    R.animator.slide_in_from_right, R.animator.slide_out_to_left,
                    R.animator.slide_in_from_left, R.animator.slide_out_to_right);
            transaction.replace(R.id.playlist, fragment, FRAGMENT_TAG);
            // If this is not the top level media (root), we add it to the fragment back stack,
            // so that actionbar toggle and Back will work appropriately:
            /*if (mediaId != null) {
                transaction.addToBackStack(null);
            }*/
            transaction.commit();
        }
    }

    private MediaBrowserFragment getBrowseFragment() {
        return (MediaBrowserFragment) getFragmentManager().findFragmentByTag(FRAGMENT_TAG);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_full_player);
        initializeToolbar();
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("");
        }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        NetworkHelper.setScreenOff(this);

        AnalyticsApplication application = (AnalyticsApplication) getApplication();
        //mTracker = application.getDefaultTracker();
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);
        //mTracker = application.getDefaultTracker();
        /*mTracker.setScreenName("FullScreenPlayerActivity");
        mTracker.send(new HitBuilders.ScreenViewBuilder().build());*/
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        NetworkHelper.setScreenOff(this);
        mBackgroundImage = (ImageView) findViewById(R.id.background_image);
        mPauseDrawable = ContextCompat.getDrawable(this, R.drawable.ic_pause_white);
        mPlayDrawable = ContextCompat.getDrawable(this, R.drawable.ic_play_arrow_white);
        mPlayPause = (ImageView) findViewById(R.id.play_pause);
        mSkipNext = (ImageView) findViewById(R.id.next);
        mFastFoward = (ImageView) findViewById(R.id.forward);
        mRewind = (ImageView) findViewById(R.id.reverse);
        mDownLoad = (ImageView) findViewById(R.id.download);
        mProgressWeel = (ProgressWheel) findViewById(R.id.progress_wheel);
        mSkipPrev = (ImageView) findViewById(R.id.prev);
        mStart = (TextView) findViewById(R.id.startText);
        mEnd = (TextView) findViewById(R.id.endText);
        mSeekbar = (SeekBar) findViewById(R.id.seekBar1);
        mLine1 = (TextView) findViewById(R.id.line1);
        mLine2 = (TextView) findViewById(R.id.line2);
        mLine3 = (TextView) findViewById(R.id.line3);
        mLoading = (ProgressBar) findViewById(R.id.progressBar1);
        mControllers = findViewById(R.id.controllers);

        mSkipNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MediaControllerCompat.TransportControls controls =
                        getSupportMediaController().getTransportControls();
                controls.skipToNext();
            }
        });

        mSkipPrev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MediaControllerCompat.TransportControls controls =
                        getSupportMediaController().getTransportControls();
                controls.skipToPrevious();
            }
        });

        mPlayPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PlaybackStateCompat state = getSupportMediaController().getPlaybackState();
                if (state != null) {
                    MediaControllerCompat.TransportControls controls =
                            getSupportMediaController().getTransportControls();
                    switch (state.getState()) {
                        case PlaybackStateCompat.STATE_PLAYING: // fall through
                        case PlaybackStateCompat.STATE_BUFFERING:
                            controls.pause();
                            stopSeekbarUpdate();
                            break;
                        case PlaybackStateCompat.STATE_PAUSED:
                        case PlaybackStateCompat.STATE_STOPPED:
                            controls.play();
                            scheduleSeekbarUpdate();
                            break;
                        default:
                            LogHelper.d(TAG, "onClick with state ", state.getState());
                    }
                }
            }
        });

        mFastFoward.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                MediaControllerCompat.TransportControls controls =
                        getSupportMediaController().getTransportControls();
                Long position = getSongPosition(true);
                controls.seekTo(position);
                mSeekbar.setProgress(position.intValue());
            }
        });

        mRewind.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                MediaControllerCompat.TransportControls controls =
                        getSupportMediaController().getTransportControls();
                Long position = getSongPosition(false);
                controls.seekTo(position);
                mSeekbar.setProgress(position.intValue());
            }
        });

        mSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mStart.setText(DateUtils.formatElapsedTime(progress / 1000));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                stopSeekbarUpdate();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                getSupportMediaController().getTransportControls().seekTo(seekBar.getProgress());
                scheduleSeekbarUpdate();
            }
        });

        // Only update from the intent if we are not recreating from a config change:
        if (savedInstanceState == null) {
            updateFromParams(getIntent());
        }


        mMediaBrowser = new MediaBrowserCompat(this,
                new ComponentName(this, MusicService.class), mConnectionCallback, null);
        String tmpMediaId = null;
        if (getIntent() != null) {
            MediaDescriptionCompat description = getIntent().getParcelableExtra(
                    MusicPlayerActivity.EXTRA_CURRENT_MEDIA_DESCRIPTION);
            if (description != null) {
                tmpMediaId = description.getMediaId();
            }
        }

        final String mediaId = tmpMediaId;
        if (mediaId != null && MediaIDHelper.extractBrowseCategoryValueFromMediaID(mediaId) != null && MediaIDHelper.extractBrowseCategoryTypeFromMediaID(mediaId).equalsIgnoreCase(MEDIA_ID_MUSICS_BY_DOWNLOAD_VIDEOID)) {
            mDownLoad.setVisibility(INVISIBLE);
        }
        mDownLoad.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isDownLoading) {
                    return;
                }
                isDownLoading = true;
                mProgressWeel.setVisibility(view.VISIBLE);
                mDownLoad.setVisibility(View.GONE);
                String downloadMediaId = MediaIDHelper.createMediaID(MediaIDHelper.extractMusicIDFromMediaID(mediaId), MEDIA_ID_MUSICS_BY_DOWNLOAD, MediaIDHelper.extractBrowseCategoryValueFromMediaID(MEDIA_ID_MUSICS_BY_VIDEOID));
                mMediaBrowser.getItem(downloadMediaId, new MediaBrowserCompat.ItemCallback() {
                    /**
                     * Called when the item has been returned by the browser service.
                     *
                     * @param item The item that was returned or null if it doesn't exist.
                     */
                    @Override
                    public void onItemLoaded(MediaBrowserCompat.MediaItem item) {
                        mDownLoad.setImageResource(R.drawable.ic_download);
                        isDownLoading = false;
                    }

                    /**
                     * Called when the item doesn't exist or there was an error retrieving it.
                     *
                     * @param itemId The media id of the media item which could not be loaded.
                     */
                    @Override
                    public void onError(@NonNull String message) {
                        Toast.makeText(FullScreenPlayerActivity.this, "Unable to download music", Toast.LENGTH_LONG);
                        mDownLoad.setImageResource(R.drawable.ic_download);
                        isDownLoading = false;
                    }
                });

                final Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        isDownLoading = false;
                        mProgressWeel.setVisibility(View.GONE);
                    }
                }, 15 * 1000);
            }
        });
        navigateToBrowser(mediaId);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        //searchView.setVisibility(View.INVISIBLE);
        SearchManager searchManager =
                (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        SearchView searchView =
                (SearchView) menu.findItem(R.id.search).getActionView();
        /*ComponentName cn = new ComponentName(this, FullScreenPlayerActivity.class);
        searchView.setSearchableInfo(
                searchManager.getSearchableInfo(cn));*/

        SearchSuggestion searchSuggestion = new SearchSuggestion(this, searchView);
        searchSuggestion.addSearchSuggestions();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void connectToSession(MediaSessionCompat.Token token) throws RemoteException {
        MediaControllerCompat mediaController = new MediaControllerCompat(
                FullScreenPlayerActivity.this, token);
        if (mediaController.getMetadata() == null) {
            finish();
            return;
        }
        setSupportMediaController(mediaController);
        mediaController.registerCallback(mCallback);
        PlaybackStateCompat state = mediaController.getPlaybackState();
        updatePlaybackState(state);
        MediaMetadataCompat metadata = mediaController.getMetadata();
        if (metadata != null) {
            updateMediaDescription(metadata.getDescription());
            updateDuration(metadata);
            String durationStr = metadata.getString(MusicProviderSource.CUSTOM_METADATA_TRACKS_DURATIONS);
            if (durationStr != null) {
                String durs[] = durationStr.split(",");
                for (String str : durs) {
                    if (str == null || str.equalsIgnoreCase("")) {
                        continue;
                    }
                    durations.add(Long.parseLong(str));
                }
                if (durations.size() > 2) {
                    mFastFoward.setVisibility(View.VISIBLE);
                    mRewind.setVisibility(View.VISIBLE);
                }
            }
        }
        updateProgress();
        if (state != null && (state.getState() == PlaybackStateCompat.STATE_PLAYING ||
                state.getState() == PlaybackStateCompat.STATE_BUFFERING)) {
            scheduleSeekbarUpdate();
        }
    }

    private void updateFromParams(Intent intent) {
        if (intent != null) {
            MediaDescriptionCompat description = intent.getParcelableExtra(
                    MusicPlayerActivity.EXTRA_CURRENT_MEDIA_DESCRIPTION);
            if (description != null) {
                updateMediaDescription(description);
            }
        }
    }

    private void scheduleSeekbarUpdate() {
        stopSeekbarUpdate();
        if (!mExecutorService.isShutdown()) {
            mScheduleFuture = mExecutorService.scheduleAtFixedRate(
                    new Runnable() {
                        @Override
                        public void run() {
                            mHandler.post(mUpdateProgressTask);
                        }
                    }, PROGRESS_UPDATE_INITIAL_INTERVAL,
                    PROGRESS_UPDATE_INTERNAL, TimeUnit.MILLISECONDS);
        }
    }

    private void stopSeekbarUpdate() {
        if (mScheduleFuture != null) {
            mScheduleFuture.cancel(false);
        }
    }

    public String getMediaId() {
        MediaBrowserFragment fragment = getBrowseFragment();
        if (fragment == null) {
            return null;
        }
        return fragment.getMediaId();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mMediaBrowser != null) {
            mMediaBrowser.connect();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mMediaBrowser != null) {
            mMediaBrowser.disconnect();
        }
        if (getSupportMediaController() != null) {
            getSupportMediaController().unregisterCallback(mCallback);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopSeekbarUpdate();
        mExecutorService.shutdown();
    }

    private void fetchImageAsync(@NonNull MediaDescriptionCompat description) {
        if (description.getIconUri() == null) {
            return;
        }
        String artUrl = description.getIconUri().toString();
        mCurrentArtUrl = artUrl;
        AlbumArtCache cache = AlbumArtCache.getInstance();
        Bitmap art = cache.getBigImage(artUrl);
        if (art == null) {
            art = description.getIconBitmap();
        }
        if (artUrl.startsWith("/")) {
            File imgFile = new File(artUrl);
            if (imgFile.exists()) {
                art = BitmapFactory.decodeFile(imgFile.getAbsolutePath());
            }
        }
        if (art != null) {
            // if we have the art cached or from the MediaDescription, use it:
            mBackgroundImage.setImageBitmap(art);
        } else {
            // otherwise, fetch a high res version and update:
            cache.fetch(artUrl, new AlbumArtCache.FetchListener() {
                @Override
                public void onFetched(String artUrl, Bitmap bitmap, Bitmap icon) {
                    // sanity check, in case a new fetch request has been done while
                    // the previous hasn't yet returned:
                    if (artUrl.equals(mCurrentArtUrl)) {
                        mBackgroundImage.setImageBitmap(bitmap);
                    }
                }
            });
        }
    }

    private void updateMediaDescription(MediaDescriptionCompat description) {
        if (description == null) {
            return;
        }
        LogHelper.d(TAG, "updateMediaDescription called ");
        mLine1.setText(description.getTitle());
        mLine2.setText(description.getSubtitle());
        fetchImageAsync(description);
    }

    private void updateDuration(MediaMetadataCompat metadata) {
        if (metadata == null) {
            return;
        }
        LogHelper.d(TAG, "updateDuration called ");
        int duration = (int) metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION);
        mSeekbar.setMax(duration);
        mEnd.setText(DateUtils.formatElapsedTime(duration / 1000));
    }

    private void updatePlaybackState(PlaybackStateCompat state) {
        if (state == null) {
            return;
        }
        mLastPlaybackState = state;
        if (getSupportMediaController() != null && getSupportMediaController().getExtras() != null) {
            String castName = getSupportMediaController()
                    .getExtras().getString(MusicService.EXTRA_CONNECTED_CAST);
            String line3Text = castName == null ? "" : getResources()
                    .getString(R.string.casting_to_device, castName);
            mLine3.setText(line3Text);
        }

        switch (state.getState()) {
            case PlaybackStateCompat.STATE_PLAYING:
                mLoading.setVisibility(INVISIBLE);
                mPlayPause.setVisibility(VISIBLE);
                mPlayPause.setImageDrawable(mPauseDrawable);
                mControllers.setVisibility(VISIBLE);
                scheduleSeekbarUpdate();
                break;
            case PlaybackStateCompat.STATE_PAUSED:
                mControllers.setVisibility(VISIBLE);
                mLoading.setVisibility(INVISIBLE);
                mPlayPause.setVisibility(VISIBLE);
                mPlayPause.setImageDrawable(mPlayDrawable);
                stopSeekbarUpdate();
                break;
            case PlaybackStateCompat.STATE_NONE:
            case PlaybackStateCompat.STATE_STOPPED:
                mLoading.setVisibility(INVISIBLE);
                mPlayPause.setVisibility(VISIBLE);
                mPlayPause.setImageDrawable(mPlayDrawable);
                stopSeekbarUpdate();
                break;
            case PlaybackStateCompat.STATE_CONNECTING:
            case PlaybackStateCompat.STATE_BUFFERING:
                mPlayPause.setVisibility(INVISIBLE);
                mLoading.setVisibility(VISIBLE);
                mLine3.setText(R.string.loading);
                stopSeekbarUpdate();
                break;
            case PlaybackStateCompat.STATE_ERROR:
                mControllers.setVisibility(VISIBLE);
                mLoading.setVisibility(INVISIBLE);
                mPlayPause.setVisibility(VISIBLE);
                mPlayPause.setImageDrawable(mPlayDrawable);
                stopSeekbarUpdate();
                mLoading.setVisibility(INVISIBLE);
                mLine3.setText(state.getErrorMessage());
                Toast.makeText(FullScreenPlayerActivity.this.getBaseContext(), state.getErrorMessage(),
                        Toast.LENGTH_LONG).show();
                break;
            default:
                LogHelper.d(TAG, "Unhandled state ", state.getState());
        }

        mSkipNext.setVisibility((state.getActions() & PlaybackStateCompat.ACTION_SKIP_TO_NEXT) == 0
                ? INVISIBLE : VISIBLE);
        mSkipPrev.setVisibility((state.getActions() & PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS) == 0
                ? INVISIBLE : VISIBLE);
    }

    private void updateProgress() {
        if (mLastPlaybackState == null) {
            return;
        }
        long currentPosition = mLastPlaybackState.getPosition();
        if (mLastPlaybackState.getState() != PlaybackStateCompat.STATE_PAUSED) {
            // Calculate the elapsed time between the last position update and now and unless
            // paused, we can assume (delta * speed) + current position is approximately the
            // latest position. This ensure that we do not repeatedly call the getPlaybackState()
            // on MediaControllerCompat.
            long timeDelta = SystemClock.elapsedRealtime() -
                    mLastPlaybackState.getLastPositionUpdateTime();
            currentPosition += (int) timeDelta * mLastPlaybackState.getPlaybackSpeed();
        }
        mSeekbar.setProgress((int) currentPosition);
    }

    @Override
    public void onMediaItemSelected(MediaBrowserCompat.MediaItem item) {

        Intent intent = getIntent();
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        if (item != null) {
            intent.putExtra(MusicPlayerActivity.EXTRA_CURRENT_MEDIA_DESCRIPTION,
                    item.getDescription());
            String musicId = MediaIDHelper.extractMusicIDFromMediaID(item.getDescription().getMediaId());
            intent.putExtra("mediaId", item.getDescription().getMediaId());
        }

        Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.ITEM_ID, item.getMediaId());
        bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, MediaIDHelper.extractBrowseCategoryTypeFromMediaID(item.getMediaId()));
        bundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, "play");
        bundle.putString(FirebaseAnalytics.Param.ITEM_CATEGORY, MediaIDHelper.extractBrowseCategoryTypeFromMediaID(item.getMediaId()));
        bundle.putString(FirebaseAnalytics.Param.CONTENT, "FullScreenPlayerActivity");
        mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle);
        getSupportMediaController().getTransportControls()
                .prepareFromMediaId(item.getMediaId(), null);
        finish();
        startActivity(intent);

    }

    @Override
    public void setToolbarTitle(CharSequence title) {

    }

    @Override
    public MediaBrowserCompat getMediaBrowser() {
        return mMediaBrowser;
    }

    public long getSongPosition(Boolean isNext) {
        int position = mSeekbar.getProgress();
        if (isNext) {
            return getNextSong(position);
        } else {
            return getPrevSong(position);
        }
    }

    private long getNextSong(int position) {
        for (Long duration : durations) {
            if (position < duration) {
                return duration;
            }
        }
        return position;
    }

    private long getPrevSong(int position) {
        Long prev = 0l;
        int i = 0;
        for (Long duration : durations) {
            if (position < duration) {
                if (position < (prev + 5000) && i >= 2) {
                    return durations.get(i - 2);
                }
                return prev;
            }
            prev = duration;
            i++;
        }
        if (i >= 2) {
            return durations.get(i - 2);
        }
        return position;
    }


}

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

package com.music.android.uamp;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaBrowserCompat.MediaItem;
import android.support.v4.media.MediaBrowserServiceCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaButtonReceiver;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v7.media.MediaRouter;

import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.StandardExceptionParser;
import com.google.android.gms.analytics.Tracker;
import com.music.android.uamp.model.MusicProvider;
import com.music.android.uamp.model.RetrieveType;
import com.music.android.uamp.playback.CastPlayback;
import com.music.android.uamp.playback.DownLoadManager;
import com.music.android.uamp.playback.FavouriteManager;
import com.music.android.uamp.playback.HistoryManager;
import com.music.android.uamp.playback.LocalPlayback;
import com.music.android.uamp.playback.Playback;
import com.music.android.uamp.playback.PlaybackManager;
import com.music.android.uamp.playback.QueueManager;
import com.music.android.uamp.ui.NowPlayingActivity;
import com.music.android.uamp.utils.CarHelper;
import com.music.android.uamp.utils.Constants;
import com.music.android.uamp.utils.LogHelper;
import com.music.android.uamp.utils.MediaIDHelper;
import com.music.android.uamp.utils.TvHelper;
import com.music.android.uamp.utils.WearHelper;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.cast.framework.SessionManagerListener;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;

import static android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE;
import static com.music.android.uamp.utils.MediaIDHelper.MEDIA_ID_ADD_TO_QUEUE;
import static com.music.android.uamp.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_DOWNLOAD;
import static com.music.android.uamp.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_DOWNLOAD_VIDEOID;
import static com.music.android.uamp.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_FAVOURITE;
import static com.music.android.uamp.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_FAVOURITE_VIDEOID;
import static com.music.android.uamp.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_HISTORY;
import static com.music.android.uamp.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_HISTORY_VIDEOID;
import static com.music.android.uamp.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_LOCAL;
import static com.music.android.uamp.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_LOCAL_VIDEOID;
import static com.music.android.uamp.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_PLAY;
import static com.music.android.uamp.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_SEARCH;
import static com.music.android.uamp.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_VIDEOID;
import static com.music.android.uamp.utils.MediaIDHelper.MEDIA_ID_ROOT;

/**
 * This class provides a MediaBrowser through a service. It exposes the media library to a browsing
 * client, through the onGetRoot and onLoadChildren methods. It also creates a MediaSession and
 * exposes it through its MediaSession.Token, which allows the client to create a MediaController
 * that connects to and send control commands to the MediaSession remotely. This is useful for
 * user interfaces that need to interact with your media session, like Android Auto. You can
 * (should) also use the same service from your app's UI, which gives a seamless playback
 * experience to the user.
 * <p>
 * To implement a MediaBrowserService, you need to:
 * <p>
 * <ul>
 * <p>
 * <li> Extend {@link android.service.media.MediaBrowserService}, implementing the media browsing
 * related methods {@link android.service.media.MediaBrowserService#onGetRoot} and
 * {@link android.service.media.MediaBrowserService#onLoadChildren};
 * <li> In onCreate, start a new {@link android.media.session.MediaSession} and notify its parent
 * with the session's token {@link android.service.media.MediaBrowserService#setSessionToken};
 * <p>
 * <li> Set a callback on the
 * {@link android.media.session.MediaSession#setCallback(android.media.session.MediaSession.Callback)}.
 * The callback will receive all the user's actions, like play, pause, etc;
 * <p>
 * <li> Handle all the actual music playing using any method your app prefers (for example,
 * {@link android.media.MediaPlayer})
 * <p>
 * <li> Update playbackState, "now playing" metadata and queue, using MediaSession proper methods
 * {@link android.media.session.MediaSession#setPlaybackState(android.media.session.PlaybackState)}
 * {@link android.media.session.MediaSession#setMetadata(android.media.MediaMetadata)} and
 * {@link android.media.session.MediaSession#setQueue(java.util.List)})
 * <p>
 * <li> Declare and export the service in AndroidManifest with an intent receiver for the action
 * android.media.browse.MediaBrowserService
 * <p>
 * </ul>
 * <p>
 * To make your app compatible with Android Auto, you also need to:
 * <p>
 * <ul>
 * <p>
 * <li> Declare a meta-data tag in AndroidManifest.xml linking to a xml resource
 * with a &lt;automotiveApp&gt; root element. For a media app, this must include
 * an &lt;uses name="media"/&gt; element as a child.
 * For example, in AndroidManifest.xml:
 * &lt;meta-data android:name="com.google.android.gms.car.application"
 * android:resource="@xml/automotive_app_desc"/&gt;
 * And in res/values/automotive_app_desc.xml:
 * &lt;automotiveApp&gt;
 * &lt;uses name="media"/&gt;
 * &lt;/automotiveApp&gt;
 * <p>
 * </ul>
 *
 * @see <a href="README.md">README.md</a> for more details.
 */
public class MusicService extends MediaBrowserServiceCompat implements
        PlaybackManager.PlaybackServiceCallback {

    private static final String TAG = LogHelper.makeLogTag(MusicService.class);

    // Extra on MediaSession that contains the Cast device name currently connected to
    public static final String EXTRA_CONNECTED_CAST = "com.music.android.uamp.CAST_NAME";
    // The action of the incoming Intent indicating that it contains a command
    // to be executed (see {@link #onStartCommand})
    public static final String ACTION_CMD = "com.music.android.uamp.ACTION_CMD";
    // The key in the extras of the incoming Intent indicating the command that
    // should be executed (see {@link #onStartCommand})
    public static final String CMD_NAME = "CMD_NAME";
    // A value of a CMD_NAME key in the extras of the incoming Intent that
    // indicates that the music playback should be paused (see {@link #onStartCommand})
    public static final String CMD_PAUSE = "CMD_PAUSE";
    // A value of a CMD_NAME key that indicates that the music playback should switch
    // to local playback from cast playback.
    public static final String CMD_STOP_CASTING = "CMD_STOP_CASTING";
    // Delay stopSelf by using a handler.
    private static final long STOP_DELAY = 1000000;


    private MusicProvider mMusicProvider;
    private PlaybackManager mPlaybackManager;

    private MediaSessionCompat mSession;
    private MediaNotificationManager mMediaNotificationManager;
    private Bundle mSessionExtras;
    private final DelayedStopHandler mDelayedStopHandler = new DelayedStopHandler(this);
    private MediaRouter mMediaRouter;
    private PackageValidator mPackageValidator;
    private SessionManager mCastSessionManager;
    private SessionManagerListener<CastSession> mCastSessionManagerListener;

    private boolean mIsConnectedToCar;
    private BroadcastReceiver mCarConnectionReceiver;
    private DownLoadManager downloadManager;
    private Tracker mTracker;

    /*
     * (non-Javadoc)
     * @see android.app.Service#onCreate()
     */
    @Override
    public void onCreate() {
        super.onCreate();
        LogHelper.d(TAG, "onCreate");

        mMusicProvider = new MusicProvider(this);

        // To make the app more responsive, fetch and cache catalog information now.
        // This can help improve the response time in the method
        // {@link #onLoadChildren(String, Result<List<MediaItem>>) onLoadChildren()}.
        mMusicProvider.retrieveMediaAsync(RetrieveType.DEFAULT, null, null /* Callback */);

        mPackageValidator = new PackageValidator(this);

        GoogleAnalytics analytics = GoogleAnalytics.getInstance(getApplicationContext());
        analytics.setLocalDispatchPeriod(10 * 60);
        mTracker = analytics.newTracker("UA-88784216-1"); // Replace with actual tracker id
        mTracker.enableExceptionReporting(true);
        mTracker = analytics.newTracker("UA-88784216-1"); // Replace with actual tracker id
        mTracker.enableExceptionReporting(true);
        //mTracker.enableAdvertisingIdCollection(true);
        mTracker.enableAutoActivityTracking(true);

// Build and send exception.

        QueueManager queueManager = new QueueManager(mMusicProvider, getResources(),
                new QueueManager.MetadataUpdateListener() {
                    @Override
                    public void onMetadataChanged(MediaMetadataCompat metadata) {
                        mSession.setMetadata(metadata);
                    }

                    @Override
                    public void onMetadataRetrieveError() {
                        mPlaybackManager.updatePlaybackState(
                                getString(R.string.error_no_metadata));
                    }

                    @Override
                    public void onCurrentQueueIndexUpdated(int queueIndex) {
                        mPlaybackManager.handlePlayRequest();
                    }

                    @Override
                    public void onQueueUpdated(String title,
                                               List<MediaSessionCompat.QueueItem> newQueue) {
                        mSession.setQueue(newQueue);
                        mSession.setQueueTitle(title);
                    }
                });

        LocalPlayback playback = new LocalPlayback(this, mMusicProvider);
        mPlaybackManager = new PlaybackManager(this, getResources(), mMusicProvider, queueManager,
                playback);
        downloadManager = new DownLoadManager(this, mMusicProvider);

        // Start a new MediaSession
        mSession = new MediaSessionCompat(this, "MusicService");
        setSessionToken(mSession.getSessionToken());
        mSession.setCallback(mPlaybackManager.getMediaSessionCallback());
        mSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        Context context = getApplicationContext();
        Intent intent = new Intent(context, NowPlayingActivity.class);
        PendingIntent pi = PendingIntent.getActivity(context, 99 /*request code*/,
                intent, PendingIntent.FLAG_UPDATE_CURRENT);
        mSession.setSessionActivity(pi);

        mSessionExtras = new Bundle();
        CarHelper.setSlotReservationFlags(mSessionExtras, true, true, true);
        WearHelper.setSlotReservationFlags(mSessionExtras, true, true);
        WearHelper.setUseBackgroundFromTheme(mSessionExtras, true);
        mSession.setExtras(mSessionExtras);

        mPlaybackManager.updatePlaybackState(null);

        try {
            mMediaNotificationManager = new MediaNotificationManager(this);
        } catch (RemoteException e) {
            throw new IllegalStateException("Could not create a MediaNotificationManager", e);
        }

        if (!TvHelper.isTvUiMode(this)) {
            mCastSessionManager = CastContext.getSharedInstance(this).getSessionManager();
            mCastSessionManagerListener = new CastSessionManagerListener();
            mCastSessionManager.addSessionManagerListener(mCastSessionManagerListener,
                    CastSession.class);
        }

        mMediaRouter = MediaRouter.getInstance(getApplicationContext());

        registerCarConnectionReceiver();
    }

    /**
     * (non-Javadoc)
     *
     * @see android.app.Service#onStartCommand(android.content.Intent, int, int)
     */
    @Override
    public int onStartCommand(Intent startIntent, int flags, int startId) {
        if (startIntent != null) {
            String action = startIntent.getAction();
            String command = startIntent.getStringExtra(CMD_NAME);
            if (ACTION_CMD.equals(action)) {
                if (CMD_PAUSE.equals(command)) {
                    mPlaybackManager.handlePauseRequest();
                } else if (CMD_STOP_CASTING.equals(command)) {
                    CastContext.getSharedInstance(this).getSessionManager().endCurrentSession(true);
                }
            } else {
                // Try to handle the intent as a media button event wrapped by MediaButtonReceiver
                MediaButtonReceiver.handleIntent(mSession, startIntent);
            }
        }
        // Reset the delay handler to enqueue a message to stop the service if
        // nothing is playing.
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        mDelayedStopHandler.sendEmptyMessageDelayed(0, STOP_DELAY);
        return START_STICKY;
    }

    /**
     * (non-Javadoc)
     *
     * @see android.app.Service#onDestroy()
     */
    @Override
    public void onDestroy() {
        LogHelper.d(TAG, "onDestroy");
        unregisterCarConnectionReceiver();
        // Service is being killed, so make sure we release our resources
        mPlaybackManager.handleStopRequest(null);
        mMediaNotificationManager.stopNotification();
        FavouriteManager.saveFavourites(getBaseContext());
        HistoryManager.saveHistory(getBaseContext());
        if (mCastSessionManager != null) {
            mCastSessionManager.removeSessionManagerListener(mCastSessionManagerListener,
                    CastSession.class);
        }

        mDelayedStopHandler.removeCallbacksAndMessages(null);
        mSession.release();

    }

    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid,
                                 Bundle rootHints) {
        LogHelper.d(TAG, "OnGetRoot: clientPackageName=" + clientPackageName,
                "; clientUid=" + clientUid + " ; rootHints=", rootHints);
        // To ensure you are not allowing any arbitrary app to browse your app's contents, you
        // need to check the origin:
        if (!mPackageValidator.isCallerAllowed(this, clientPackageName, clientUid)) {
            // If the request comes from an untrusted package, return null. No further calls will
            // be made to other media browsing methods.
            LogHelper.w(TAG, "OnGetRoot: IGNORING request from untrusted package "
                    + clientPackageName);
            return null;
        }
        //noinspection StatementWithEmptyBody
        if (CarHelper.isValidCarPackage(clientPackageName)) {
            // Optional: if your app needs to adapt the music library to show a different subset
            // when connected to the car, this is where you should handle it.
            // If you want to adapt other runtime behaviors, like tweak ads or change some behavior
            // that should be different on cars, you should instead use the boolean flag
            // set by the BroadcastReceiver mCarConnectionReceiver (mIsConnectedToCar).
        }
        //noinspection StatementWithEmptyBody
        if (WearHelper.isValidWearCompanionPackage(clientPackageName)) {
            // Optional: if your app needs to adapt the music library for when browsing from a
            // Wear device, you should return a different MEDIA ROOT here, and then,
            // on onLoadChildren, handle it accordingly.
        }

        return new BrowserRoot(MEDIA_ID_ROOT, null);
    }

    @Override
    public void onLoadChildren(@NonNull final String parentMediaId,
                               @NonNull final Result<List<MediaItem>> result) {
        LogHelper.d(TAG, "OnLoadChildren: parentMediaId=", parentMediaId);

        try {

            String categoryType = MediaIDHelper.extractBrowseCategoryTypeFromMediaID(parentMediaId);
            if (categoryType != null && MEDIA_ID_MUSICS_BY_SEARCH.equalsIgnoreCase(categoryType)) {
                final String searchQuery = MediaIDHelper.extractBrowseCategoryValueFromMediaID(parentMediaId);
                List<MediaItem> list = mMusicProvider.getSearchList(searchQuery);
                if (list != null) {
                    result.sendResult(list);
                } else {
                    result.detach();
                    mMusicProvider.retrieveMediaAsync(RetrieveType.SEARCH, searchQuery, new MusicProvider.Callback() {
                        @Override
                        public void onMusicCatalogReady(boolean success) {
                            result.sendResult(mMusicProvider.getSearchList(searchQuery));
                        }
                    });
                }
            } else if (categoryType != null && MEDIA_ID_MUSICS_BY_VIDEOID.equalsIgnoreCase(categoryType) && !MediaIDHelper.isBrowseable(parentMediaId)) {
                final String musicId = MediaIDHelper.extractMusicIDFromMediaID(parentMediaId);
                List<MediaItem> list = mMusicProvider.getVideosIDList(musicId);
                if (list != null) {
                    result.sendResult(list);
                } else {
                    result.detach();
                    mMusicProvider.retrieveMediaAsync(RetrieveType.VIDEOID, musicId, new MusicProvider.Callback() {
                        @Override
                        public void onMusicCatalogReady(boolean success) {
                            List<MediaItem> items = mMusicProvider.getVideosIDList(musicId);
                            if (items == null || items.isEmpty()) {
                                items = mMusicProvider.getChildren(MEDIA_ID_MUSICS_BY_DOWNLOAD, getResources());
                            }
                            result.sendResult(items);
                        }
                    });
                }
            } else if (categoryType != null && MEDIA_ID_MUSICS_BY_PLAY.equalsIgnoreCase(categoryType) && !MediaIDHelper.isBrowseable(parentMediaId)) {
                String id = null;
                if (mPlaybackManager.getTopItemOfQueue() != null) {
                    id = MediaIDHelper.extractMusicIDFromMediaID(mPlaybackManager.getTopItemOfQueue());
                } else {
                    id = MediaIDHelper.extractMusicIDFromMediaID(parentMediaId);
                }
                final String musicId = id;
                List<MediaItem> items = mMusicProvider.getCurrentPlayList((musicId));
                if (items == null || items.isEmpty()) {
                    result.detach();
                    mMusicProvider.retrieveMediaAsync(RetrieveType.PLAY, musicId, new MusicProvider.Callback() {
                        @Override
                        public void onMusicCatalogReady(boolean success) {
                            List<MediaItem> items = mMusicProvider.getCurrentPlayList((musicId));
                            if (items == null || items.isEmpty()) {
                                items = mMusicProvider.getChildren(MEDIA_ID_MUSICS_BY_DOWNLOAD, getResources());
                            }
                            result.sendResult(items);
                            mPlaybackManager.updatePlayBackQueue();
                        }
                    });
                } else {
                    result.sendResult(items);
                    mPlaybackManager.updatePlayBackQueue();
                }
            } else if (categoryType != null && (MEDIA_ID_MUSICS_BY_DOWNLOAD_VIDEOID.equalsIgnoreCase(categoryType))) {
                final String musicId = MediaIDHelper.extractMusicIDFromMediaID(parentMediaId);
                List<MediaItem> list = mMusicProvider.getVideosIDList(musicId);
                if (list == null || list.isEmpty()) {
                    result.detach();
                    mMusicProvider.retrieveMediaAsync(RetrieveType.VIDEOID, musicId, new MusicProvider.Callback() {
                        @Override
                        public void onMusicCatalogReady(boolean success) {
                            List<MediaItem> items = mMusicProvider.getVideosIDList(musicId);
                            if (items == null || items.isEmpty()) {
                                items = mMusicProvider.getChildren(MEDIA_ID_MUSICS_BY_DOWNLOAD, getResources());
                            }
                            result.sendResult(items);
                        }
                    });
                } else {
                    result.sendResult(list);
                }
            } else if (categoryType != null && MEDIA_ID_MUSICS_BY_HISTORY_VIDEOID.equalsIgnoreCase(categoryType)) {
                final String musicId = MediaIDHelper.extractMusicIDFromMediaID(parentMediaId);
                MediaMetadataCompat mediaMetadataCompat = mMusicProvider.getMusic(musicId);
                if (mediaMetadataCompat.getString(Constants.CUSTOM_METADATA_LOCAL) != null) {
                    String searchQuery = mediaMetadataCompat.getString(MediaMetadataCompat.METADATA_KEY_ALBUM);
                    if (searchQuery == null || searchQuery.isEmpty()) {
                        searchQuery = mediaMetadataCompat.getString(MediaMetadataCompat.METADATA_KEY_ARTIST);
                    }
                    if (searchQuery == null || searchQuery.isEmpty()) {
                        searchQuery = mediaMetadataCompat.getString(MediaMetadataCompat.METADATA_KEY_TITLE);
                        String extensions[] = {".mp3", ".3gp", ".flac", ".ota", ".ogg"};
                        for (String staticExtension : extensions) {
                            if (searchQuery.contains(staticExtension)) {
                                searchQuery = searchQuery.replace(staticExtension, "");
                                break;
                            }
                        }
                    }
                    searchQuery = searchQuery.replaceAll("[-+.^:,(){}0-9]", "");
                    List<MediaItem> list = mMusicProvider.getSearchList(searchQuery);
                    if (list != null) {
                        result.sendResult(list);
                    } else {
                        result.detach();
                        final String finalSearchQuery = searchQuery;
                        mMusicProvider.retrieveMediaAsync(RetrieveType.SEARCH, searchQuery, new MusicProvider.Callback() {
                            @Override
                            public void onMusicCatalogReady(boolean success) {
                                result.sendResult(mMusicProvider.getSearchList(finalSearchQuery));
                            }
                        });
                    }
                    return;
                }
                List<MediaItem> list = mMusicProvider.getVideosIDList(musicId);
                if (list == null || list.isEmpty()) {
                    result.detach();
                    mMusicProvider.retrieveMediaAsync(RetrieveType.VIDEOID, musicId, new MusicProvider.Callback() {
                        @Override
                        public void onMusicCatalogReady(boolean success) {
                            List<MediaItem> items = mMusicProvider.getVideosIDList(musicId);
                            if (items == null || items.isEmpty()) {
                                items = mMusicProvider.getChildren(MEDIA_ID_MUSICS_BY_DOWNLOAD, getResources());
                            }
                            result.sendResult(items);
                        }
                    });
                } else {
                    result.sendResult(list);
                }
            } else if (categoryType != null && MEDIA_ID_MUSICS_BY_FAVOURITE_VIDEOID.equalsIgnoreCase(categoryType)) {
                List<MediaItem> items = mMusicProvider.getChildren(MEDIA_ID_MUSICS_BY_FAVOURITE, getResources());
                result.sendResult(items);

            } else if (categoryType != null && MEDIA_ID_MUSICS_BY_LOCAL_VIDEOID.equalsIgnoreCase(categoryType)) {
                List<MediaItem> items = mMusicProvider.getChildren(MEDIA_ID_MUSICS_BY_LOCAL, getResources());
                result.sendResult(items);

            } else if (categoryType != null && MEDIA_ID_MUSICS_BY_DOWNLOAD.equalsIgnoreCase(categoryType)) {
                result.sendResult(mMusicProvider.getChildren(parentMediaId, getResources()));
            } else if (categoryType != null && MEDIA_ID_MUSICS_BY_FAVOURITE.equalsIgnoreCase(categoryType)) {
                result.sendResult(mMusicProvider.getChildren(parentMediaId, getResources()));
            } else if (categoryType != null && MEDIA_ID_MUSICS_BY_HISTORY.equalsIgnoreCase(categoryType)) {
                result.sendResult(mMusicProvider.getChildren(parentMediaId, getResources()));
            } else if (mMusicProvider.isInitialized()) {
                // if music library is ready, return immediately
                result.sendResult(mMusicProvider.getChildren(parentMediaId, getResources()));
            } else {
                // otherwise, only return results when the music library is retrieved
                result.detach();
                mMusicProvider.retrieveMediaAsync(RetrieveType.DEFAULT, "", new MusicProvider.Callback() {
                    @Override
                    public void onMusicCatalogReady(boolean success) {
                        result.sendResult(mMusicProvider.getChildren(parentMediaId, getResources()));
                    }
                });
            }
        } catch (Exception e) {
            mTracker.send(new HitBuilders.ExceptionBuilder()
                    .setDescription(new StandardExceptionParser(this, null)
                            .getDescription(Thread.currentThread().getName(), e))
                    .setFatal(false)
                    .build());
            e.printStackTrace();
            result.sendResult(null);
        }
    }

    @Override
    public void onLoadItem(String mediaId, final Result<MediaBrowserCompat.MediaItem> result) {
        String categoryType = MediaIDHelper.extractBrowseCategoryTypeFromMediaID(mediaId);

        final String musicId = MediaIDHelper.extractMusicIDFromMediaID(mediaId);

        if (categoryType != null && MEDIA_ID_MUSICS_BY_DOWNLOAD.equalsIgnoreCase(categoryType) && !MediaIDHelper.isBrowseable(mediaId)) {
            if (!downloadManager.isDownloadManagerAvailable()) {
                result.sendResult(null);
                return;
            }

            Boolean start = downloadManager.downLoad(musicId, new BroadcastReceiver() {
                public void onReceive(Context ctxt, Intent intent) {
                    if (intent.getAction().equals(ACTION_DOWNLOAD_COMPLETE)) {
                        //result.sendResult(mMusicProvider.getMediaItemMusic(musicId));
                        //return;
                    }
                }
            });
            if (!start) {
                result.sendResult(null);
                return;
            }
        }
        if (categoryType != null && MEDIA_ID_MUSICS_BY_FAVOURITE_VIDEOID.equalsIgnoreCase(categoryType) && !MediaIDHelper.isBrowseable(mediaId)) {
            String tmp = MediaIDHelper.extractBrowseCategoryValueFromMediaID(mediaId);
            if (tmp != null && (tmp.equalsIgnoreCase("true") || tmp.equalsIgnoreCase("false"))) {
                Boolean isFavourite = Boolean.parseBoolean(tmp);
                mMusicProvider.setFavorite(musicId, mMusicProvider.getMusic(musicId), isFavourite);
                result.sendResult(mMusicProvider.getMediaItemMusic(musicId));
            }
            return;
        }
        if ((categoryType != null && MEDIA_ID_ADD_TO_QUEUE.equalsIgnoreCase(categoryType))) {
            String topItemOfQueue = mPlaybackManager.getTopItemOfQueue();
            String currentMusicId = mPlaybackManager.getCurrentPlayMusicId();
            if (currentMusicId == null || topItemOfQueue == null) {
                result.detach();
                return;
            }
            mMusicProvider.addMusicToCurrentList(MediaIDHelper.extractMusicIDFromMediaID(topItemOfQueue), MediaIDHelper.extractMusicIDFromMediaID(currentMusicId), musicId);
            result.sendResult(mMusicProvider.getMediaItemMusic(MediaIDHelper.extractMusicIDFromMediaID(currentMusicId)));
            mPlaybackManager.updatePlayBackQueue();
            return;
        }
        //mPlaybackManager.updatePlayBackQueue();
        result.detach();
    }

    private boolean addItemToExistingPlayList(String mediaId) {

        return true;
    }

    /**
     * Callback method called from PlaybackManager whenever the music is about to play.
     */
    @Override
    public void onPlaybackStart() {
        if (!mSession.isActive()) {
            mSession.setActive(true);
        }

        mDelayedStopHandler.removeCallbacksAndMessages(null);

        // The service needs to continue running even after the bound client (usually a
        // MediaController) disconnects, otherwise the music playback will stop.
        // Calling startService(Intent) will keep the service running until it is explicitly killed.
        startService(new Intent(getApplicationContext(), MusicService.class));
    }


    /**
     * Callback method called from PlaybackManager whenever the music stops playing.
     */
    @Override
    public void onPlaybackStop() {
        // Reset the delayed stop handler, so after STOP_DELAY it will be executed again,
        // potentially stopping the service.
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        mDelayedStopHandler.sendEmptyMessageDelayed(0, STOP_DELAY);
        stopForeground(true);
    }

    @Override
    public void onNotificationRequired() {
        mMediaNotificationManager.startNotification();
    }

    @Override
    public void onPlaybackStateUpdated(PlaybackStateCompat newState) {
        mSession.setPlaybackState(newState);
    }

    private void registerCarConnectionReceiver() {
        IntentFilter filter = new IntentFilter(CarHelper.ACTION_MEDIA_STATUS);
        mCarConnectionReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String connectionEvent = intent.getStringExtra(CarHelper.MEDIA_CONNECTION_STATUS);
                mIsConnectedToCar = CarHelper.MEDIA_CONNECTED.equals(connectionEvent);
                LogHelper.i(TAG, "Connection event to Android Auto: ", connectionEvent,
                        " isConnectedToCar=", mIsConnectedToCar);
            }
        };
        registerReceiver(mCarConnectionReceiver, filter);
    }

    private void unregisterCarConnectionReceiver() {
        unregisterReceiver(mCarConnectionReceiver);
    }

    /**
     * A simple handler that stops the service if playback is not active (playing)
     */
    private static class DelayedStopHandler extends Handler {
        private final WeakReference<MusicService> mWeakReference;

        private DelayedStopHandler(MusicService service) {
            mWeakReference = new WeakReference<>(service);
        }

        @Override
        public void handleMessage(Message msg) {
            MusicService service = mWeakReference.get();
            if (service != null && service.mPlaybackManager.getPlayback() != null) {
                if (service.mPlaybackManager.getPlayback().isPlaying()) {
                    LogHelper.d(TAG, "Ignoring delayed stop since the media player is in use.");
                    return;
                }
                LogHelper.d(TAG, "Stopping service with delay handler.");
                service.stopSelf();
            }
        }
    }

    /**
     * Session Manager Listener responsible for switching the Playback instances
     * depending on whether it is connected to a remote player.
     */
    private class CastSessionManagerListener implements SessionManagerListener<CastSession> {

        @Override
        public void onSessionEnded(CastSession session, int error) {
            LogHelper.d(TAG, "onSessionEnded");
            mSessionExtras.remove(EXTRA_CONNECTED_CAST);
            mSession.setExtras(mSessionExtras);
            Playback playback = new LocalPlayback(MusicService.this, mMusicProvider);
            mMediaRouter.setMediaSessionCompat(null);
            mPlaybackManager.switchToPlayback(playback, false);
        }

        @Override
        public void onSessionResumed(CastSession session, boolean wasSuspended) {
        }

        @Override
        public void onSessionStarted(CastSession session, String sessionId) {
            // In case we are casting, send the device name as an extra on MediaSession metadata.
            mSessionExtras.putString(EXTRA_CONNECTED_CAST,
                    session.getCastDevice().getFriendlyName());
            mSession.setExtras(mSessionExtras);
            // Now we can switch to CastPlayback
            Playback playback = new CastPlayback(mMusicProvider, MusicService.this);
            mMediaRouter.setMediaSessionCompat(mSession);
            mPlaybackManager.switchToPlayback(playback, true);
        }

        @Override
        public void onSessionStarting(CastSession session) {
        }

        @Override
        public void onSessionStartFailed(CastSession session, int error) {
        }

        @Override
        public void onSessionEnding(CastSession session) {
            // This is our final chance to update the underlying stream position
            // In onSessionEnded(), the underlying CastPlayback#mRemoteMediaClient
            // is disconnected and hence we update our local value of stream position
            // to the latest position.
            mPlaybackManager.getPlayback().updateLastKnownStreamPosition();
        }

        @Override
        public void onSessionResuming(CastSession session, String sessionId) {
        }

        @Override
        public void onSessionResumeFailed(CastSession session, int error) {
        }

        @Override
        public void onSessionSuspended(CastSession session, int reason) {
        }
    }
}

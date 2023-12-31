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

package com.music.android.uamp.playback;

import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import com.music.android.uamp.R;
import com.music.android.uamp.model.AudioMetaData;
import com.music.android.uamp.model.MusicProvider;
import com.music.android.uamp.model.MusicProviderSource;
import com.music.android.uamp.utils.Constants;
import com.music.android.uamp.utils.LogHelper;
import com.music.android.uamp.utils.MediaIDHelper;
import com.music.android.uamp.utils.NetworkHelper;
import com.music.android.uamp.utils.WearHelper;

import static android.os.AsyncTask.THREAD_POOL_EXECUTOR;

/**
 * Manage the interactions among the container service, the queue manager and the actual playback.
 */
public class PlaybackManager implements Playback.Callback {

    private static final String TAG = LogHelper.makeLogTag(PlaybackManager.class);
    // Action to thumbs up a media item
    private static final String CUSTOM_ACTION_THUMBS_UP = "com.music.android.uamp.THUMBS_UP";
    public static final String CUSTOM_ACTION_ADD_TO_QUEUE = "com.music.android.uamp.ADD_TO_QUEUE";

    private MusicProvider mMusicProvider;
    private QueueManager mQueueManager;
    private Resources mResources;
    private Playback mPlayback;
    private PlaybackServiceCallback mServiceCallback;
    private MediaSessionCallback mMediaSessionCallback;

    public PlaybackManager(PlaybackServiceCallback serviceCallback, Resources resources,
                           MusicProvider musicProvider, QueueManager queueManager,
                           Playback playback) {
        mMusicProvider = musicProvider;
        mServiceCallback = serviceCallback;
        mResources = resources;
        mQueueManager = queueManager;
        mMediaSessionCallback = new MediaSessionCallback();
        mPlayback = playback;
        mPlayback.setCallback(this);
    }

    public Playback getPlayback() {
        return mPlayback;
    }

    public MediaSessionCompat.Callback getMediaSessionCallback() {
        return mMediaSessionCallback;
    }

    /**
     * Handle a request to play music
     */
    public void handlePlayRequest() {
        LogHelper.d(TAG, "handlePlayRequest: mState=" + mPlayback.getState());
        MediaSessionCompat.QueueItem currentMusic = mQueueManager.getCurrentMusic();
        if (currentMusic != null) {
            if (true) {
                mServiceCallback.onPlaybackStart();
                mPlayback.play(currentMusic);
                //getNextQueueItemAudioUrlAndUpdate();

            } else {
                mPlayback.setState(PlaybackStateCompat.STATE_CONNECTING);
                this.onPlaybackStatusChanged(PlaybackStateCompat.STATE_CONNECTING);
                if (NetworkHelper.isOnline(mMusicProvider.getContext())) {
                    getAudioUrlAndPlay(currentMusic);
                } else {
                    mQueueManager.updateMetadata();
                    mMediaSessionCallback.onStop();
                    PlaybackManager.this.onError("Please connect to internet to play for this song");
                }
            }
        }
    }

    public void getNextQueueItemAudioUrlAndUpdate() {
        MediaSessionCompat.QueueItem item = mQueueManager.getNextItem();
        if (item == null) {
            return;
        }
        MediaMetadataCompat track = mMusicProvider.getMusic(
                MediaIDHelper.extractMusicIDFromMediaID(item.getDescription().getMediaId()));

        //noinspection ResourceType
        String source = track.getString(Constants.CUSTOM_METADATA_TRACK_SOURCE);
        if (source != null) {
            return;
        }
        new AsyncTask<String, Void, AudioMetaData>() {
            @Override
            protected AudioMetaData doInBackground(String... params) {

                try {
                    Thread.sleep(5000); // sleep for 2 minutes
                    if (mPlayback.getState() == PlaybackStateCompat.STATE_ERROR) {
                        return null;
                    }
                    MediaSessionCompat.QueueItem item = mQueueManager.getNextItem();
                    if (item == null) {
                        return null;
                    }
                    return mMusicProvider.getSourceUrl(MediaIDHelper.extractMusicIDFromMediaID(item.getDescription().getMediaId()));
                } catch (InterruptedException e) {
                    return null;
                } catch (Exception e) {
                    LogHelper.e("Unable to get next audio url");
                    return null;
                }

            }

            @Override
            protected void onPostExecute(AudioMetaData source) {
                if (source != null) {
                    final MediaSessionCompat.QueueItem item = mQueueManager.getNextItem();
                    mMusicProvider.updateSource(Constants.CUSTOM_METADATA_TRACK_SOURCE,
                            MediaIDHelper.extractMusicIDFromMediaID(item.getDescription().getMediaId()), source.getUrl());
                }
            }
        }.executeOnExecutor(THREAD_POOL_EXECUTOR);

    }

    public void getAudioUrlAndPlay(final MediaSessionCompat.QueueItem currentMusic) {

        new AsyncTask<String, Void, AudioMetaData>() {
            @Override
            protected AudioMetaData doInBackground(String... params) {
                return mMusicProvider.getSourceUrl(MediaIDHelper.extractMusicIDFromMediaID(currentMusic.getDescription().getMediaId()));

            }

            @Override
            protected void onPostExecute(AudioMetaData source) {
                if (source != null) {

                    MediaSessionCompat.QueueItem latestCurrentMusic = mQueueManager.getCurrentMusic();
                    if (latestCurrentMusic != currentMusic) {
                        LogHelper.e(TAG, "currentMusic is different from the new current music");
                        return;
                    }
                    if (mPlayback.getState() == PlaybackStateCompat.STATE_ERROR) {
                        LogHelper.e(TAG, "music service is down is return");
                        return;
                    }
                    mMusicProvider.updateSource(Constants.CUSTOM_METADATA_TRACK_SOURCE,
                            MediaIDHelper.extractMusicIDFromMediaID(currentMusic.getDescription().getMediaId()), source.getUrl());
                    if (source.getDurations() != null && !source.getDurations().isEmpty()) {
                        String durStr = "";
                        for (Long dur : source.getDurations()) {
                            durStr = durStr + dur + ",";
                        }
                        if (!durStr.equalsIgnoreCase("")) {
                            durStr = durStr.substring(0, durStr.length() - 1);
                        }
                        mMusicProvider.updateSource(Constants.CUSTOM_METADATA_TRACKS_DURATIONS,
                                MediaIDHelper.extractMusicIDFromMediaID(currentMusic.getDescription().getMediaId()), durStr);

                    }

                    mPlayback.play(currentMusic);
                    mQueueManager.updateMetadata();
                    mServiceCallback.onPlaybackStart();
                    //getNextQueueItemAudioUrlAndUpdate();
                    //LogHelper.e(TAG, source);

                } else {
                    mQueueManager.updateMetadata();
                    mMediaSessionCallback.onStop();
                    PlaybackManager.this.onError("Youtube is restricting this song to download as of now, Please try playing different song");
                }

            }
        }.executeOnExecutor(THREAD_POOL_EXECUTOR);

    }

    /**
     * Handle a request to pause music
     */
    public void handlePauseRequest() {
        LogHelper.d(TAG, "handlePauseRequest: mState=" + mPlayback.getState());
        if (mPlayback.isPlaying()) {
            mPlayback.pause();
            mServiceCallback.onPlaybackStop();
        }
    }

    /**
     * Handle a request to stop music
     *
     * @param withError Error message in case the stop has an unexpected cause. The error
     *                  message will be set in the PlaybackState and will be visible to
     *                  MediaController clients.
     */
    public void handleStopRequest(String withError) {
        LogHelper.d(TAG, "handleStopRequest: mState=" + mPlayback.getState() + " error=", withError);
        mPlayback.stop(true);
        LogHelper.e(TAG, "music service is down");
        mPlayback.setState(PlaybackStateCompat.STATE_ERROR);
        mServiceCallback.onPlaybackStop();
        updatePlaybackState(withError);
    }


    /**
     * Update the current media player state, optionally showing an error message.
     *
     * @param error if not null, error message to present to the user.
     */
    public void updatePlaybackState(String error) {
        LogHelper.d(TAG, "updatePlaybackState, playback state=" + mPlayback.getState());
        long position = PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN;
        if (mPlayback != null && mPlayback.isConnected()) {
            position = mPlayback.getCurrentStreamPosition();
        }

        //noinspection ResourceType
        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(getAvailableActions());

        setCustomAction(stateBuilder);
        int state = mPlayback.getState();

        // If there is an error message, send it to the playback state:
        if (error != null) {
            // Error states are really only supposed to be used for errors that cause playback to
            // stop unexpectedly and persist until the user takes action to fix it.
            stateBuilder.setErrorMessage(error);
            state = PlaybackStateCompat.STATE_ERROR;
        }
        //noinspection ResourceType
        stateBuilder.setState(state, position, 1.0f, SystemClock.elapsedRealtime());

        // Set the activeQueueItemId if the current index is valid.
        MediaSessionCompat.QueueItem currentMusic = mQueueManager.getCurrentMusic();
        if (currentMusic != null) {
            stateBuilder.setActiveQueueItemId(currentMusic.getQueueId());
        }

        mServiceCallback.onPlaybackStateUpdated(stateBuilder.build());

        if (state == PlaybackStateCompat.STATE_PLAYING ||
                state == PlaybackStateCompat.STATE_PAUSED) {
            mServiceCallback.onNotificationRequired();
        }
    }

    private void setCustomAction(PlaybackStateCompat.Builder stateBuilder) {
        MediaSessionCompat.QueueItem currentMusic = mQueueManager.getCurrentMusic();
        if (currentMusic == null) {
            return;
        }
        // Set appropriate "Favorite" icon on Custom action:
        String mediaId = currentMusic.getDescription().getMediaId();
        if (mediaId == null) {
            return;
        }
        String musicId = MediaIDHelper.extractMusicIDFromMediaID(mediaId);
        int favoriteIcon = mMusicProvider.isFavorite(musicId) ?
                R.drawable.ic_star_on : R.drawable.ic_star_off;
        LogHelper.d(TAG, "updatePlaybackState, setting Favorite custom action of music ",
                musicId, " current favorite=", mMusicProvider.isFavorite(musicId));
        Bundle customActionExtras = new Bundle();
        WearHelper.setShowCustomActionOnWear(customActionExtras, true);
        stateBuilder.addCustomAction(new PlaybackStateCompat.CustomAction.Builder(
                CUSTOM_ACTION_THUMBS_UP, mResources.getString(R.string.favorite), favoriteIcon)
                .setExtras(customActionExtras)
                .build());
    }

    private long getAvailableActions() {
        long actions =
                PlaybackStateCompat.ACTION_PLAY |
                        PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID |
                        PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH |
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT;
        if (mPlayback.isPlaying()) {
            actions |= PlaybackStateCompat.ACTION_PAUSE;
        }
        return actions;
    }

    /**
     * Implementation of the Playback.Callback interface
     */
    @Override
    public void onCompletion() {
        // The media player finished playing the current song, so we go ahead
        // and start the next.
        if (mQueueManager.skipQueuePosition(1)) {
            handlePlayRequest();
            mQueueManager.updateMetadata();
        } else {
            // If skipping was not possible, we stop and release the resources:
            handleStopRequest(null);
        }
    }

    @Override
    public void onPlaybackStatusChanged(int state) {
        if (PlaybackStateCompat.STATE_PLAYING == state) {
            getNextQueueItemAudioUrlAndUpdate();
        }
        updatePlaybackState(null);
    }

    @Override
    public void onMetaDataChanged() {
        mQueueManager.updateMetadata();
    }

    @Override
    public void onError(String error) {
        updatePlaybackState(error);
    }

    @Override
    public void setCurrentMediaId(String mediaId) {
        LogHelper.d(TAG, "setCurrentMediaId", mediaId);
        mQueueManager.setQueueFromMusic(mediaId);
    }


    /**
     * Switch to a different Playback instance, maintaining all playback state, if possible.
     *
     * @param playback switch to this playback
     */
    public void switchToPlayback(Playback playback, boolean resumePlaying) {
        if (playback == null) {
            throw new IllegalArgumentException("Playback cannot be null");
        }
        // suspend the current one.
        int oldState = mPlayback.getState();
        int pos = mPlayback.getCurrentStreamPosition();
        String currentMediaId = mPlayback.getCurrentMediaId();
        mPlayback.stop(false);
        playback.setCallback(this);
        playback.setCurrentStreamPosition(pos < 0 ? 0 : pos);
        playback.setCurrentMediaId(currentMediaId);
        playback.start();
        // finally swap the instance
        mPlayback = playback;
        switch (oldState) {
            case PlaybackStateCompat.STATE_BUFFERING:
            case PlaybackStateCompat.STATE_CONNECTING:
            case PlaybackStateCompat.STATE_PAUSED:
                mPlayback.pause();
                break;
            case PlaybackStateCompat.STATE_PLAYING:
                MediaSessionCompat.QueueItem currentMusic = mQueueManager.getCurrentMusic();
                if (resumePlaying && currentMusic != null) {
                    mPlayback.play(currentMusic);
                } else if (!resumePlaying) {
                    mPlayback.pause();
                } else {
                    mPlayback.stop(true);
                }
                break;
            case PlaybackStateCompat.STATE_NONE:
                break;
            default:
                LogHelper.d(TAG, "Default called. Old state is ", oldState);
        }
    }


    public void updatePlayBackQueue() {
        mQueueManager.updateQueueItem();
    }

    public String getTopItemOfQueue() {
        return mQueueManager.getTopElemnetOfQueue();
    }

    public String getCurrentPlayMusicId(){
        MediaSessionCompat.QueueItem queueItem=mQueueManager.getCurrentMusic();
        if(queueItem != null){
            return mQueueManager.getCurrentMusic().getDescription().getMediaId();
        }else {
            return null;
        }
    }


    private class MediaSessionCallback extends MediaSessionCompat.Callback {
        @Override
        public void onPlay() {
            LogHelper.d(TAG, "play");
            if (mQueueManager.getCurrentMusic() == null) {
                mQueueManager.setRandomQueue();
            }
            handlePlayRequest();
        }

        @Override
        public void onSkipToQueueItem(long queueId) {
            LogHelper.d(TAG, "OnSkipToQueueItem:" + queueId);
            mQueueManager.setCurrentQueueItem(queueId);
            mQueueManager.updateMetadata();
        }

        @Override
        public void onSeekTo(long position) {
            LogHelper.d(TAG, "onSeekTo:", position);
            mPlayback.seekTo((int) position);
        }

        @Override
        public void onPlayFromMediaId(String mediaId, Bundle extras) {
            LogHelper.d(TAG, "playFromMediaId mediaId:", mediaId, "  extras=", extras);
            mQueueManager.setQueueFromMusic(mediaId);
            handlePlayRequest();
        }

        @Override
        public void onPause() {
            LogHelper.d(TAG, "pause. current state=" + mPlayback.getState());
            handlePauseRequest();
        }

        @Override
        public void onStop() {
            LogHelper.d(TAG, "stop. current state=" + mPlayback.getState());
            handleStopRequest(null);
        }

        @Override
        public void onSkipToNext() {
            LogHelper.d(TAG, "skipToNext");
            if (mQueueManager.skipQueuePosition(1)) {
                handlePlayRequest();
            } else {
                handleStopRequest("Cannot skip");
            }
            mQueueManager.updateMetadata();
        }

        @Override
        public void onSkipToPrevious() {
            if (mQueueManager.skipQueuePosition(-1)) {
                handlePlayRequest();
            } else {
                handleStopRequest("Cannot skip");
            }
            mQueueManager.updateMetadata();
        }

        @Override
        public void onCustomAction(@NonNull String action, Bundle extras) {
            if (CUSTOM_ACTION_THUMBS_UP.equals(action)) {
                LogHelper.i(TAG, "onCustomAction: favorite for current track");
                MediaSessionCompat.QueueItem currentMusic = mQueueManager.getCurrentMusic();
                if (currentMusic != null) {
                    String mediaId = currentMusic.getDescription().getMediaId();
                    if (mediaId != null) {
                        String musicId = MediaIDHelper.extractMusicIDFromMediaID(mediaId);
                        mMusicProvider.setFavorite(musicId, mMusicProvider.getMusic(musicId), true);
                    }
                }
                // playback state needs to be updated because the "Favorite" icon on the
                // custom action will change to reflect the new favorite state.
                updatePlaybackState(null);
            } else if (CUSTOM_ACTION_ADD_TO_QUEUE.equals(action)) {
                String mediaId = extras.getString("mediaId");
                mQueueManager.updateQueueItem();
                MediaSessionCompat.QueueItem currentMusic = mQueueManager.getCurrentMusic();
                String topItem = mQueueManager.getTopElemnetOfQueue();
                mMusicProvider.addMusicToCurrentList(MediaIDHelper.extractMusicIDFromMediaID(topItem),MediaIDHelper.extractMusicIDFromMediaID(currentMusic.getDescription().getMediaId()), MediaIDHelper.extractMusicIDFromMediaID(mediaId));
            } else {
                LogHelper.e(TAG, "Unsupported action: ", action);
            }
        }

        /**
         * Handle free and contextual searches.
         * <p/>
         * All voice searches on Android Auto are sent to this method through a connected
         * {@link android.support.v4.media.session.MediaControllerCompat}.
         * <p/>
         * Threads and async handling:
         * Search, as a potentially slow operation, should run in another thread.
         * <p/>
         * Since this method runs on the main thread, most apps with non-trivial metadata
         * should defer the actual search to another thread (for example, by using
         * an {@link AsyncTask} as we do here).
         **/
        @Override
        public void onPlayFromSearch(final String query, final Bundle extras) {
            LogHelper.d(TAG, "playFromSearch  query=", query, " extras=", extras);

            mPlayback.setState(PlaybackStateCompat.STATE_CONNECTING);
            boolean successSearch = mQueueManager.setQueueFromSearch(query, extras);
            if (successSearch) {
                handlePlayRequest();
                mQueueManager.updateMetadata();
            } else {
                updatePlaybackState("Could not find music");
            }
        }

        @Override
        public void onPrepareFromMediaId(String mediaId, Bundle extras) {
            mPlayback.setState(PlaybackStateCompat.STATE_CONNECTING);
            mQueueManager.setQueueFromMusic(mediaId);
            handlePlayRequest();
        }
    }


    public interface PlaybackServiceCallback {
        void onPlaybackStart();

        void onNotificationRequired();

        void onPlaybackStop();

        void onPlaybackStateUpdated(PlaybackStateCompat newState);
    }
}

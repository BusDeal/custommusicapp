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
import android.support.annotation.NonNull;
import android.support.test.runner.AndroidJUnit4;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.test.mock.MockResources;

import com.music.android.uamp.TestSetupHelper;
import com.music.android.uamp.model.MusicProvider;
import com.music.android.uamp.utils.MediaIDHelper;
import com.music.android.uamp.utils.SimpleMusicProviderSource;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

/**
 * Android instrumentation unit tests for {@link PlaybackManager} and related classes.
 */
@RunWith(AndroidJUnit4.class)
public class PlaybackManagerTest {

    private MusicProvider musicProvider;
    private Resources resources;

    @Before
    public void setUpMusicProvider() throws Exception {
        SimpleMusicProviderSource source = new SimpleMusicProviderSource();
        populateMusicSource(source);
        musicProvider = TestSetupHelper.setupMusicProvider(source);

        resources = new MockResources() {
            @NonNull
            @Override
            public String getString(int id) throws NotFoundException {
                return "";
            }

            @NonNull
            @Override
            public String getString(int id, Object... formatArgs) throws NotFoundException {
                return "";
            }
        };
    }

    private void populateMusicSource(SimpleMusicProviderSource source) {
        source.add("Music 1", "Album 1", "Smith Singer", "Genre 1",
                "https://r2---sn-5jucgv5qc5oq-cagl.googlevideo.com/videoplayback?initcwndbps=2397500&ipbits=0&clen=18982696&ms=au&mv=m&mt=1476902653&ip=106.51.128.0&key=yt6&upn=3S1Da4pUMm8&id=o-AH70WVx-rz5qwNC7R2nDapqvxvKarwilFlChEKE2-jtl&mn=sn-5jucgv5qc5oq-cagl&mm=31&ei=88AHWLg8yc2gA-Tqs6gD&lmt=1462681748934659&gcr=in&gir=yes&requiressl=yes&keepalive=yes&source=youtube&itag=171&pl=24&dur=1091.175&mime=audio/webm&expire=1476924755&sparams=clen,dur,ei,gcr,gir,id,initcwndbps,ip,ipbits,itag,keepalive,lmt,mime,mm,mn,ms,mv,pl,requiressl,source,upn,expire&signature=B60A230BE90EA4FA358DC75D3C276AF1833FFE73.C1925F30348F2A00E3CF9AD693A939F4ABE0BB57&ratebypass=yes&title=video", null, 1, 3, 18982696);
        source.add("Music 2", "Album 1", "Joe Singer", "Genre 1",
                "https://examplemusic.com/music2.mp3", null, 2, 3, 3300);
        source.add("Music 3", "Album 1", "John Singer", "Genre 1",
                "https://examplemusic.com/music3.mp3", null, 3, 3, 3400);
        source.add("Romantic Song 1", "Album 2", "Joe Singer", "Genre 2",
                "https://examplemusic.com/music4.mp3", null, 1, 2, 4200);
        source.add("Romantic Song 2", "Album 2", "Joe Singer", "Genre 2",
                "https://examplemusic.com/music5.mp3", null, 2, 2, 4200);
    }

    @Test
    public void testPlay() throws Exception {
        String mediaId = MediaIDHelper.MEDIA_ID_ROOT;
        while (MediaIDHelper.isBrowseable(mediaId)) {
            mediaId = musicProvider.getChildren(mediaId, resources).get(0).getMediaId();
        }

        // Using a CountDownLatch, we will check if all callbacks are called correctly when
        // a onPlayFromMediaId command is issued.
        final CountDownLatch latch = new CountDownLatch(5);
        final String expectedMediaId = mediaId;

        QueueManager queueManager = new QueueManager(musicProvider, resources, new SimpleMetadataUpdateListener() {
            @Override
            public void onMetadataChanged(MediaMetadataCompat metadata) {
                // Latch countdown 1: QueueManager will change appropriately
                assertEquals(MediaIDHelper.extractMusicIDFromMediaID(expectedMediaId),
                        metadata.getDescription().getMediaId());
                latch.countDown();
            }
        });

        SimplePlaybackServiceCallback serviceCallback = new SimplePlaybackServiceCallback() {
            @Override
            public void onPlaybackStart() {
                // Latch countdown 2: PlaybackService will get a onPlaybackStart call
                latch.countDown();
            }

            @Override
            public void onPlaybackStateUpdated(PlaybackStateCompat newState) {
                if (newState.getState() == PlaybackStateCompat.STATE_PLAYING) {
                    // Latch countdown 3: PlaybackService will get a state updated call (here we
                    // ignore the unrelated state changes)
                    latch.countDown();
                }
            }

            @Override
            public void onNotificationRequired() {
                // Latch countdown 4: PlaybackService will get call to show a media notification
                latch.countDown();
            }
        };

        Playback playback = new SimplePlayback() {
            @Override
            public void play(MediaSessionCompat.QueueItem item) {
                // Latch countdown 5: Playback will be called with the correct queueItem
                assertEquals(expectedMediaId, item.getDescription().getMediaId());
                latch.countDown();
            }
        };

        PlaybackManager playbackManager = new PlaybackManager(serviceCallback, resources,
                musicProvider, queueManager, playback);
        playbackManager.getMediaSessionCallback().onPlayFromMediaId(expectedMediaId, null);

        latch.await(30, TimeUnit.SECONDS);

        // Finally, check if the current music in queueManager is as expected
        assertEquals(expectedMediaId, queueManager.getCurrentMusic().getDescription().getMediaId());
    }


    @Test
    public void testPlayFromSearch() throws Exception {
        // Using a CountDownLatch, we will check if all callbacks are called correctly when
        // a onPlayFromMediaId command is issued.
        final CountDownLatch latch = new CountDownLatch(5);
        final String expectedMusicId = musicProvider.searchMusicBySongTitle("Music 3")
                .iterator().next().getDescription().getMediaId();

        QueueManager queueManager = new QueueManager(musicProvider, resources, new SimpleMetadataUpdateListener() {
            @Override
            public void onMetadataChanged(MediaMetadataCompat metadata) {
                // Latch countdown 1: QueueManager will change appropriately
                assertEquals(expectedMusicId, metadata.getDescription().getMediaId());
                latch.countDown();
            }
        });

        SimplePlaybackServiceCallback serviceCallback = new SimplePlaybackServiceCallback() {
            @Override
            public void onPlaybackStart() {
                // Latch countdown 2: PlaybackService will get a onPlaybackStart call
                latch.countDown();
            }

            @Override
            public void onPlaybackStateUpdated(PlaybackStateCompat newState) {
                if (newState.getState() == PlaybackStateCompat.STATE_PLAYING) {
                    // Latch countdown 3: PlaybackService will get a state updated call (here we
                    // ignore the unrelated state changes)
                    latch.countDown();
                }
            }

            @Override
            public void onNotificationRequired() {
                // Latch countdown 4: PlaybackService will get call to show a media notification
                latch.countDown();
            }
        };

        Playback playback = new SimplePlayback() {
            @Override
            public void play(MediaSessionCompat.QueueItem item) {
                // Latch countdown 5: Playback will be called with the correct queueItem
                assertEquals(expectedMusicId, MediaIDHelper.extractMusicIDFromMediaID(
                        item.getDescription().getMediaId()));
                latch.countDown();
            }
        };

        PlaybackManager playbackManager = new PlaybackManager(serviceCallback, resources,
                musicProvider, queueManager, playback);
        playbackManager.getMediaSessionCallback().onPlayFromSearch("Music 3", null);

        latch.await(5, TimeUnit.SECONDS);

        // Finally, check if the current music in queueManager is as expected
        assertEquals(expectedMusicId, MediaIDHelper.extractMusicIDFromMediaID(
                queueManager.getCurrentMusic().getDescription().getMediaId()));
    }


}
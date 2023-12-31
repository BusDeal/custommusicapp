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

package com.music.android.uamp.utils;

import android.os.Bundle;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;

import com.music.android.uamp.VoiceSearchParams;
import com.music.android.uamp.model.MusicProvider;
import com.music.android.uamp.model.MutableMediaMetadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.music.android.uamp.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_DOWNLOAD_VIDEOID;
import static com.music.android.uamp.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_FAVOURITE_VIDEOID;
import static com.music.android.uamp.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_HISTORY_VIDEOID;
import static com.music.android.uamp.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_LOCAL;
import static com.music.android.uamp.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_LOCAL_VIDEOID;
import static com.music.android.uamp.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_SEARCH;
import static com.music.android.uamp.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_VIDEOID;

/**
 * Utility class to help on queue related tasks.
 */
public class QueueHelper {

    private static final String TAG = LogHelper.makeLogTag(QueueHelper.class);

    private static final int RANDOM_QUEUE_SIZE = 10;

    public static List<MediaSessionCompat.QueueItem> getPlayingQueue(String mediaId,
                                                                     MusicProvider musicProvider) {

        // extract the browsing hierarchy from the media ID:
        String[] hierarchy = MediaIDHelper.getHierarchy(mediaId);

        if (hierarchy.length != 2) {
            LogHelper.e(TAG, "Could not build a playing queue for this mediaId: ", mediaId);
            return null;
        }

        String categoryType = hierarchy[0];
        String categoryValue = hierarchy[1];
        LogHelper.d(TAG, "Creating playing queue for ", categoryType, ",  ", categoryValue);


        List<MediaMetadataCompat> current = new ArrayList<>();
        MediaMetadataCompat mediaMetadataCompat = musicProvider.getMusic(MediaIDHelper.extractMusicIDFromMediaID(mediaId));
        if (mediaMetadataCompat == null) {
            return new ArrayList<MediaSessionCompat.QueueItem>();
        }
        current.add(mediaMetadataCompat);

        return convertToQueue(current, hierarchy[0], hierarchy[1]);
    }

    public static List<MediaSessionCompat.QueueItem> getAdditionalPlayingTracks(String mediaId,
                                                                                MusicProvider musicProvider) {

        String[] hierarchy = MediaIDHelper.getHierarchy(mediaId);

        if (hierarchy.length != 2) {
            LogHelper.e(TAG, "Could not build a playing queue for this mediaId: ", mediaId);
            return null;
        }

        String categoryType = hierarchy[0];
        String categoryValue = hierarchy[1];
        LogHelper.d(TAG, "Creating playing queue for ", categoryType, ",  ", categoryValue);

        Iterable<MediaMetadataCompat> tracks = null;


        // This sample only supports genre and by_search category types.
        if (categoryType.equals(MEDIA_ID_MUSICS_BY_LOCAL)) {
            tracks = musicProvider.getMusicsByGenre(categoryValue);

        } else if (categoryType.equals(MEDIA_ID_MUSICS_BY_VIDEOID)) {
            tracks = musicProvider.getYoutubeIdBasedMusic(MediaIDHelper.extractMusicIDFromMediaID(mediaId));
        } else if (categoryType.equals(MEDIA_ID_MUSICS_BY_DOWNLOAD_VIDEOID)) {
            tracks = musicProvider.getDownLoadMusicList(MediaIDHelper.extractMusicIDFromMediaID(mediaId));
        } else if (categoryType.equals(MEDIA_ID_MUSICS_BY_FAVOURITE_VIDEOID)) {
            tracks = musicProvider.getFavouriteMusicTracks(MediaIDHelper.extractMusicIDFromMediaID(mediaId));
        } else if (categoryType.equals(MEDIA_ID_MUSICS_BY_HISTORY_VIDEOID)) {
            tracks = musicProvider.getHistoryMusicTracks(MediaIDHelper.extractMusicIDFromMediaID(mediaId));
        } else if (categoryType.equals(MEDIA_ID_MUSICS_BY_LOCAL_VIDEOID)) {
            tracks = musicProvider.getLocalMusicTracks(MediaIDHelper.extractMusicIDFromMediaID(mediaId));
        }
        if (tracks == null) {
            List<MediaMetadataCompat> current = new ArrayList<>();
            MediaMetadataCompat mediaMetadataCompat = musicProvider.getMusic(MediaIDHelper.extractMusicIDFromMediaID(mediaId));
            current.add(mediaMetadataCompat);
            tracks = current;
        }

        Iterable<MediaMetadataCompat> myQueueItems = musicProvider.getPlayList(MediaIDHelper.extractMusicIDFromMediaID(mediaId));
        List<MediaSessionCompat.QueueItem> queueItems = new ArrayList<>();
        if (myQueueItems != null) {
            queueItems = convertToQueue(myQueueItems, hierarchy[0], hierarchy[1]);
        }else {
            queueItems= convertToQueue(tracks, hierarchy[0], hierarchy[1]);
        }
        return queueItems;
    }

    public static List<MediaSessionCompat.QueueItem> getPlayingQueueFromSearch(String query,
                                                                               Bundle queryParams, MusicProvider musicProvider) {

        LogHelper.d(TAG, "Creating playing queue for musics from search: ", query,
                " params=", queryParams);

        VoiceSearchParams params = new VoiceSearchParams(query, queryParams);

        LogHelper.d(TAG, "VoiceSearchParams: ", params);

        if (params.isAny) {
            // If isAny is true, we will play anything. This is app-dependent, and can be,
            // for example, favorite playlists, "I'm feeling lucky", most recent, etc.
            return getRandomQueue(musicProvider);
        }

        Iterable<MediaMetadataCompat> result = null;
        if (params.isAlbumFocus) {
            result = musicProvider.searchMusicByAlbum(params.album);
        } else if (params.isGenreFocus) {
            result = musicProvider.getMusicsByGenre(params.genre);
        } else if (params.isArtistFocus) {
            result = musicProvider.searchMusicByArtist(params.artist);
        } else if (params.isSongFocus) {
            result = musicProvider.searchMusicBySongTitle(params.song);
        }

        // If there was no results using media focus parameter, we do an unstructured query.
        // This is useful when the user is searching for something that looks like an artist
        // to Google, for example, but is not. For example, a user searching for Madonna on
        // a PodCast application wouldn't get results if we only looked at the
        // Artist (podcast author). Then, we can instead do an unstructured search.
        if (params.isUnstructured || result == null || !result.iterator().hasNext()) {
            // To keep it simple for this example, we do unstructured searches on the
            // song title only. A real world application could search on other fields as well.
            result = musicProvider.searchMusicBySongTitle(query);
        }

        return convertToQueue(result, MEDIA_ID_MUSICS_BY_SEARCH, query);
    }


    public static int getMusicIndexOnQueue(Iterable<MediaSessionCompat.QueueItem> queue,
                                           String mediaId) {
        int index = 0;
        for (MediaSessionCompat.QueueItem item : queue) {
            if (mediaId.equals(item.getDescription().getMediaId())) {
                return index;
            }
            index++;
        }
        return -1;
    }

    public static int getMusicIndexOnQueue(Iterable<MediaSessionCompat.QueueItem> queue,
                                           long queueId) {
        int index = 0;
        for (MediaSessionCompat.QueueItem item : queue) {
            if (queueId == item.getQueueId()) {
                return index;
            }
            index++;
        }
        return -1;
    }

    private static List<MediaSessionCompat.QueueItem> convertToQueue(
            Iterable<MediaMetadataCompat> tracks, String... categories) {
        List<MediaSessionCompat.QueueItem> queue = new ArrayList<>();
        int count = 0;
        for (MediaMetadataCompat track : tracks) {

            String genre = track.getDescription().getTitle().toString();
            if (genre == null) {
                genre = "null";
            }
            if (genre.indexOf("/") > 0) {
                genre = genre.replace("/", "");
            }
            if (genre.indexOf("|") > 0) {
                genre = genre.replace("|", "");
            }
            // We create a hierarchy-aware mediaID, so we know what the queue is about by looking
            // at the QueueItem media IDs.
            String hierarchyAwareMediaID = MediaIDHelper.createMediaID(
                    track.getDescription().getMediaId(), MediaIDHelper.MEDIA_ID_MUSICS_BY_VIDEOID, genre);

            MediaMetadataCompat trackCopy = new MediaMetadataCompat.Builder(track)
                    .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, hierarchyAwareMediaID)
                    .build();

            // We don't expect queues to change after created, so we use the item index as the
            // queueId. Any other number unique in the queue would work.
            MediaSessionCompat.QueueItem item = new MediaSessionCompat.QueueItem(
                    trackCopy.getDescription(), count++);
            queue.add(item);
        }
        return queue;

    }

    /**
     * Create a random queue with at most {@link #RANDOM_QUEUE_SIZE} elements.
     *
     * @param musicProvider the provider used for fetching music.
     * @return list containing {@link MediaSessionCompat.QueueItem}'s
     */
    public static List<MediaSessionCompat.QueueItem> getRandomQueue(MusicProvider musicProvider) {
        List<MediaMetadataCompat> result = new ArrayList<>(RANDOM_QUEUE_SIZE);
        Iterable<MediaMetadataCompat> shuffled = musicProvider.getShuffledMusic();
        for (MediaMetadataCompat metadata : shuffled) {
            if (result.size() == RANDOM_QUEUE_SIZE) {
                break;
            }
            result.add(metadata);
        }
        LogHelper.d(TAG, "getRandomQueue: result.size=", result.size());

        return convertToQueue(result, MEDIA_ID_MUSICS_BY_SEARCH, "random");
    }

    public static boolean isIndexPlayable(int index, List<MediaSessionCompat.QueueItem> queue) {
        return (queue != null && index >= 0 && index < queue.size());
    }
}

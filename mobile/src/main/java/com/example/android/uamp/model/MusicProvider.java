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

package com.example.android.uamp.model;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.media.browse.MediaBrowser;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.util.LruCache;

import com.example.android.uamp.R;
import com.example.android.uamp.playback.DownLoadManager;
import com.example.android.uamp.utils.LogHelper;
import com.example.android.uamp.utils.MediaIDHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.example.android.uamp.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_DOWNLOAD;
import static com.example.android.uamp.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_DOWNLOAD_VIDEOID;
import static com.example.android.uamp.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_GENRE;
import static com.example.android.uamp.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_SEARCH;
import static com.example.android.uamp.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_VIDEOID;
import static com.example.android.uamp.utils.MediaIDHelper.MEDIA_ID_ROOT;
import static com.example.android.uamp.utils.MediaIDHelper.createMediaID;

/**
 * Simple data provider for music tracks. The actual metadata source is delegated to a
 * MusicProviderSource defined by a constructor argument of this class.
 */
public class MusicProvider {

    private static final String TAG = LogHelper.makeLogTag(MusicProvider.class);

    private MusicProviderSource mSource;

    // Categorized caches for music track data:
    private ConcurrentMap<String, List<MediaMetadataCompat>> mMusicListByGenre;
    private Map<String, MutableMediaMetadata> mMusicListById;
    private Map<String, MutableMediaMetadata> downloadMusicList;
    //private final LinkedHashMap<String, MutableMediaMetadata> mMusicListBySearch;
    //private final LinkedHashMap<String, MutableMediaMetadata> mMusicListByVideoId;
    private final Set<String> mFavoriteTracks;
    private LruCache<String, Map<String, MutableMediaMetadata>> mMusicListBySearch = new LruCache<>(10);
    private LruCache<String, Map<String, MutableMediaMetadata>> mMusicListByVideoId = new LruCache<>(100);
    private Context context;

    public String getSourceUrl(String videoId) {
        return mSource.getAudioSourceUrl(videoId);
    }

    enum State {
        NON_INITIALIZED, INITIALIZING, INITIALIZED
    }


    private volatile State mCurrentState = State.NON_INITIALIZED;

    public interface Callback {
        void onMusicCatalogReady(boolean success);
    }

    public MusicProvider(Context context) {
        this(context, new RemoteJSONSource());
    }

    public MusicProvider(Context context, MusicProviderSource source) {
        this.context = context;
        mSource = source;
        mMusicListByGenre = new ConcurrentHashMap<>();
        mMusicListById = new LinkedHashMap<>();
        downloadMusicList=DownLoadManager.getDownloadedMedia(context);
        mFavoriteTracks = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    }

    /**
     * Get an iterator over the list of genres
     *
     * @return genres
     */
    public Iterable<String> getGenres() {
        if (mCurrentState != State.INITIALIZED) {
            return Collections.emptyList();
        }
        return mMusicListByGenre.keySet();
    }

    /**
     * Get an iterator over a shuffled collection of all songs
     */
    public Iterable<MediaMetadataCompat> getShuffledMusic() {
        if (mCurrentState != State.INITIALIZED) {
            return Collections.emptyList();
        }
        List<MediaMetadataCompat> shuffled = new ArrayList<>(mMusicListById.size());
        for (MutableMediaMetadata mutableMetadata : mMusicListById.values()) {
            shuffled.add(mutableMetadata.metadata);
        }
        Collections.shuffle(shuffled);
        return shuffled;
    }

    /**
     * Get an iterator over a shuffled collection of all songs
     */
    private Iterable<MediaMetadataCompat> getYoutubeOrderMusic() {
        if (mCurrentState != State.INITIALIZED) {
            return Collections.emptyList();
        }
        List<MediaMetadataCompat> shuffled = new ArrayList<>(mMusicListById.size());
        for (MutableMediaMetadata mutableMetadata : mMusicListById.values()) {
            shuffled.add(mutableMetadata.metadata);
        }
        return shuffled;
    }

    private Iterable<MediaMetadataCompat> getDownloadedMusic() {
        downloadMusicList = DownLoadManager.getDownloadedMedia(context);
        List<MediaMetadataCompat> shuffled = new ArrayList<>(downloadMusicList.size());
        for (MutableMediaMetadata mutableMetadata : downloadMusicList.values()) {
            shuffled.add(mutableMetadata.metadata);
        }
        return shuffled;
    }

    private Iterable<MediaMetadataCompat> getYoutubeSearchMusic(String query) {
        Map<String, MutableMediaMetadata> data = mMusicListBySearch.get(query);

        if (data == null) {
            return null;
        }
        if (mCurrentState != State.INITIALIZED) {
            return Collections.emptyList();
        }
        List<MediaMetadataCompat> shuffled = new ArrayList<>(data.size());
        for (MutableMediaMetadata mutableMetadata : data.values()) {
            shuffled.add(mutableMetadata.metadata);
        }
        return shuffled;
    }

    public Iterable<MediaMetadataCompat> getDownLoadMusicList(String videoId) {
        Map<String, MutableMediaMetadata> data = mMusicListByVideoId.get(videoId);
        if (data == null) {
            return null;
        }
        if (mCurrentState != State.INITIALIZED) {
            return Collections.emptyList();
        }
        List<MediaMetadataCompat> shuffled = new ArrayList<>(data.size());
        for (MutableMediaMetadata mutableMetadata : downloadMusicList.values()) {
            shuffled.add(mutableMetadata.metadata);
        }
        return shuffled;
    }

    public Iterable<MediaMetadataCompat> getYoutubeIdBasedMusic(String videoId) {
        Map<String, MutableMediaMetadata> data = mMusicListByVideoId.get(videoId);
        if (data == null) {
            return null;
        }
        if (mCurrentState != State.INITIALIZED) {
            return Collections.emptyList();
        }
        List<MediaMetadataCompat> shuffled = new ArrayList<>(data.size());
        for (MutableMediaMetadata mutableMetadata : data.values()) {
            shuffled.add(mutableMetadata.metadata);
        }
        return shuffled;
    }

    private MutableMediaMetadata findMediaFromMusicIdList(String videoId) {
        for (Map<String, MutableMediaMetadata> mutableMediaMetadataMap : mMusicListByVideoId.snapshot().values()) {
            if (mutableMediaMetadataMap.containsKey(videoId)) {
                return mutableMediaMetadataMap.get(videoId);
            }
        }
        return null;
    }

    private MutableMediaMetadata findMediaFromMusicSearchList(String videoId) {
        for (Map<String, MutableMediaMetadata> mutableMediaMetadataMap : mMusicListBySearch.snapshot().values()) {
            if (mutableMediaMetadataMap.containsKey(videoId)) {
                return mutableMediaMetadataMap.get(videoId);
            }
        }
        return null;
    }

    private MutableMediaMetadata findMediaFromDownloadList(String videoId) {
        return downloadMusicList.get(videoId);
    }


    /**
     * Get music tracks of the given genre
     */
    public Iterable<MediaMetadataCompat> getMusicsByGenre(String genre) {
        if (mCurrentState != State.INITIALIZED || !mMusicListByGenre.containsKey(genre)) {
            return Collections.emptyList();
        }
        return mMusicListByGenre.get(genre);
    }


    /**
     * Very basic implementation of a search that filter music tracks with title containing
     * the given query.
     */
    public Iterable<MediaMetadataCompat> searchMusicBySongTitle(String query) {
        return searchMusic(MediaMetadataCompat.METADATA_KEY_TITLE, query);
    }

    /**
     * Very basic implementation of a search that filter music tracks with album containing
     * the given query.
     */
    public Iterable<MediaMetadataCompat> searchMusicByAlbum(String query) {
        return searchMusic(MediaMetadataCompat.METADATA_KEY_ALBUM, query);
    }

    /**
     * Very basic implementation of a search that filter music tracks with artist containing
     * the given query.
     */
    public Iterable<MediaMetadataCompat> searchMusicByArtist(String query) {
        return searchMusic(MediaMetadataCompat.METADATA_KEY_ARTIST, query);
    }

    Iterable<MediaMetadataCompat> searchMusic(String metadataField, String query) {
        if (mCurrentState != State.INITIALIZED) {
            return Collections.emptyList();
        }
        ArrayList<MediaMetadataCompat> result = new ArrayList<>();
        query = query.toLowerCase(Locale.US);
        for (MutableMediaMetadata track : mMusicListById.values()) {
            if (track.metadata.getString(metadataField).toLowerCase(Locale.US)
                    .contains(query)) {
                result.add(track.metadata);
            }
        }
        return result;
    }


    /**
     * Return the MediaMetadataCompat for the given musicID.
     *
     * @param musicId The unique, non-hierarchical music ID.
     */
    public MediaMetadataCompat getMusic(String musicId) {
        MutableMediaMetadata mutableMediaMetadata = getMutableMusic(musicId);
        return mutableMediaMetadata != null ? mutableMediaMetadata.metadata : null;
    }

    public MediaBrowserCompat.MediaItem getMediaItemMusic(String musicId) {
        MediaMetadataCompat mediaMetadataCompat = getMusic(musicId);
        return createMediaItem(MEDIA_ID_MUSICS_BY_VIDEOID, mediaMetadataCompat);

    }

    private MutableMediaMetadata getMutableMusic(String musicId) {
        MutableMediaMetadata mutableMediaMetadata;
        mutableMediaMetadata = mMusicListById.containsKey(musicId) ? mMusicListById.get(musicId) : null;
        if (mutableMediaMetadata == null) {
            mutableMediaMetadata = findMediaFromDownloadList(musicId);
        }
        if (mutableMediaMetadata == null) {
            mutableMediaMetadata = findMediaFromMusicSearchList(musicId);
        }
        if (mutableMediaMetadata == null) {
            mutableMediaMetadata = findMediaFromMusicIdList(musicId);
        }
        return mutableMediaMetadata;
    }


    public synchronized void updateMusicArt(String musicId, Bitmap albumArt, Bitmap icon) {
        MediaMetadataCompat metadata = getMusic(musicId);
        metadata = new MediaMetadataCompat.Builder(metadata)

                // set high resolution bitmap in METADATA_KEY_ALBUM_ART. This is used, for
                // example, on the lockscreen background when the media session is active.
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt)

                // set small version of the album art in the DISPLAY_ICON. This is used on
                // the MediaDescription and thus it should be small to be serialized if
                // necessary
                .putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, icon)

                .build();

        MutableMediaMetadata mutableMetadata = getMutableMusic(musicId);
        if (mutableMetadata == null) {
            throw new IllegalStateException("Unexpected error: Inconsistent data structures in " +
                    "MusicProvider");
        }

        mutableMetadata.metadata = metadata;
    }

    public synchronized void updateSource(String attribute, String musicId, String source) {
        MediaMetadataCompat metadata = getMusic(musicId);
        metadata = new MediaMetadataCompat.Builder(metadata)
                .putString(attribute, source)
                .build();

        MutableMediaMetadata mutableMetadata = getMutableMusic(musicId);
        if (mutableMetadata == null) {
            throw new IllegalStateException("Unexpected error: Inconsistent data structures in " +
                    "MusicProvider");
        }

        mutableMetadata.metadata = metadata;
    }

    public synchronized void updateDuration(String musicId, Long duration) {
        MediaMetadataCompat metadata = getMusic(musicId);
        metadata = new MediaMetadataCompat.Builder(metadata)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
                .build();

        MutableMediaMetadata mutableMetadata = getMutableMusic(musicId);
        if (mutableMetadata == null) {
            throw new IllegalStateException("Unexpected error: Inconsistent data structures in " +
                    "MusicProvider");
        }

        mutableMetadata.metadata = metadata;
    }

    public void setFavorite(String musicId, boolean favorite) {
        if (favorite) {
            mFavoriteTracks.add(musicId);
        } else {
            mFavoriteTracks.remove(musicId);
        }
    }

    public boolean isInitialized() {
        return mCurrentState == State.INITIALIZED;
    }

    public boolean isFavorite(String musicId) {
        return mFavoriteTracks.contains(musicId);
    }

    /**
     * Get the list of music tracks from a server and caches the track information
     * for future reference, keying tracks by musicId and grouping by genre.
     */
    public void retrieveMediaAsync(final RetrieveType retriveType, final String query, final Callback callback) {
        LogHelper.d(TAG, "retrieveMediaAsync called");

        if (retriveType == retriveType.DEFAULT && mCurrentState == State.INITIALIZED) {
            if (callback != null) {
                // Nothing to do, execute callback immediately
                callback.onMusicCatalogReady(true);
            }
            return;
        }

        // Asynchronously load the music catalog in a separate thread
        new AsyncTask<String, Void, State>() {
            @Override
            protected State doInBackground(String... params) {
                retrieveMedia(retriveType, query);
                return mCurrentState;
            }

            @Override
            protected void onPostExecute(State current) {
                if (callback != null) {
                    callback.onMusicCatalogReady(current == State.INITIALIZED);
                }
            }
        }.execute();
    }

    private synchronized void buildListsByGenre() {
        ConcurrentMap<String, List<MediaMetadataCompat>> newMusicListByGenre = new ConcurrentHashMap<>();

        for (MutableMediaMetadata m : mMusicListById.values()) {
            String genre = m.metadata.getString(MediaMetadataCompat.METADATA_KEY_GENRE);
            List<MediaMetadataCompat> list = newMusicListByGenre.get(genre);
            if (list == null) {
                list = new ArrayList<>();
                newMusicListByGenre.put(genre, list);
            }
            list.add(m.metadata);
        }
        mMusicListByGenre = newMusicListByGenre;
    }

    private synchronized void retrieveMedia(RetrieveType retrieveType, String... params) {
        Map<String, MutableMediaMetadata> map = new LinkedHashMap<>();
        try {

            if (retrieveType == RetrieveType.DEFAULT && mCurrentState == State.INITIALIZED) {
                return;
            }
            mCurrentState = State.INITIALIZING;
            Iterator<MediaMetadataCompat> tracks = mSource.iterator(retrieveType, params);

            while (tracks.hasNext()) {
                MediaMetadataCompat item = tracks.next();
                String musicId = item.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID);
                map.put(musicId, new MutableMediaMetadata(musicId, item));
            }

            if (!map.isEmpty()) {
                mSource.updateDuration(map);
            }

            if (retrieveType == RetrieveType.DEFAULT) {
                mMusicListById = map;
            }
            if (retrieveType == RetrieveType.SEARCH) {
                mMusicListBySearch.put(params[0], map);
            }
            if (retrieveType == RetrieveType.VIDEOID) {
                mMusicListByVideoId.put(params[0], map);
            }
            buildListsByGenre();
            mCurrentState = State.INITIALIZED;

            if (map.isEmpty()) {
                mCurrentState = State.NON_INITIALIZED;
            }

        } finally {
            if (mCurrentState != State.INITIALIZED) {
                // Something bad happened, so we reset state to NON_INITIALIZED to allow
                // retries (eg if the network connection is temporary unavailable)
                mCurrentState = State.NON_INITIALIZED;
            }


        }
    }


    public List<MediaBrowserCompat.MediaItem> getChildren(String mediaId, Resources resources) {
        List<MediaBrowserCompat.MediaItem> mediaItems = new ArrayList<>();

        if (!MediaIDHelper.isBrowseable(mediaId)) {
            return mediaItems;
        }

        if (MEDIA_ID_ROOT.equals(mediaId)) {
            for (MediaMetadataCompat metadata : getYoutubeOrderMusic()) {
                mediaItems.add(createMediaItem(MEDIA_ID_MUSICS_BY_VIDEOID, metadata));
            }

            //mediaItems.add(createBrowsableMediaItemForRoot(resources));

        }
        if (MEDIA_ID_MUSICS_BY_DOWNLOAD.equals(mediaId)) {
            for (MediaMetadataCompat metadata : getDownloadedMusic()) {
                mediaItems.add(createMediaItem(MEDIA_ID_MUSICS_BY_DOWNLOAD_VIDEOID, metadata));
            }

            //mediaItems.add(createBrowsableMediaItemForRoot(resources));

        } else if (MEDIA_ID_MUSICS_BY_GENRE.equals(mediaId)) {
            for (String genre : getGenres()) {
                mediaItems.add(createBrowsableMediaItemForGenre(genre, resources));
            }

        } else if (mediaId.startsWith(MEDIA_ID_MUSICS_BY_GENRE)) {
            String genre = MediaIDHelper.getHierarchy(mediaId)[1];
            for (MediaMetadataCompat metadata : getMusicsByGenre(genre)) {
                mediaItems.add(createMediaItem(MEDIA_ID_MUSICS_BY_GENRE, metadata));
            }

        } else {
            LogHelper.w(TAG, "Skipping unmatched mediaId: ", mediaId);
        }
        return mediaItems;
    }

    public List<MediaBrowserCompat.MediaItem> getSearchList(String query) {
        List<MediaBrowserCompat.MediaItem> mediaItems = new ArrayList<>();
        Iterable<MediaMetadataCompat> iterable = getYoutubeSearchMusic(query);
        if (iterable == null) {
            return null;
        }
        for (MediaMetadataCompat metadata : getYoutubeSearchMusic(query)) {
            mediaItems.add(createMediaItem(MEDIA_ID_MUSICS_BY_SEARCH, metadata));
        }
        return mediaItems;
    }

    public List<MediaBrowserCompat.MediaItem> getVideosIDList(String musicId) {
        List<MediaBrowserCompat.MediaItem> mediaItems = new ArrayList<>();
        Iterable<MediaMetadataCompat> iterable = getYoutubeIdBasedMusic(musicId);
        if (iterable == null) {
            return null;
        }
        for (MediaMetadataCompat metadata : iterable) {
            mediaItems.add(createMediaItem(MEDIA_ID_MUSICS_BY_VIDEOID, metadata));
        }
        return mediaItems;
    }


    private MediaBrowserCompat.MediaItem createBrowsableMediaItemForRoot(Resources resources) {
        MediaDescriptionCompat description = new MediaDescriptionCompat.Builder()
                .setMediaId(MEDIA_ID_MUSICS_BY_GENRE)
                .setTitle(resources.getString(R.string.browse_genres))
                .setSubtitle(resources.getString(R.string.browse_genre_subtitle))
                .setIconUri(Uri.parse("android.resource://" +
                        "com.example.android.uamp/drawable/ic_by_genre"))
                .build();
        return new MediaBrowserCompat.MediaItem(description,
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE);
    }

    private MediaBrowserCompat.MediaItem createBrowsableMediaItemForGenre(String genre,
                                                                          Resources resources) {
        MediaDescriptionCompat description = new MediaDescriptionCompat.Builder()
                .setMediaId(createMediaID(null, MEDIA_ID_MUSICS_BY_GENRE, genre))
                .setTitle(genre)
                .setSubtitle(resources.getString(
                        R.string.browse_musics_by_genre_subtitle, genre))
                .build();
        return new MediaBrowserCompat.MediaItem(description,
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE);
    }

    private MediaBrowserCompat.MediaItem createMediaItem(String category, MediaMetadataCompat metadata) {
        // Since mediaMetadata fields are immutable, we need to create a copy, so we
        // can set a hierarchy-aware mediaID. We will need to know the media hierarchy
        // when we get a onPlayFromMusicID call, so we can create the proper queue based
        // on where the music was selected from (by artist, by genre, random, etc)

        String genre = metadata.getString(MediaMetadataCompat.METADATA_KEY_GENRE);
        if (genre.indexOf("/") > 0) {
            genre = genre.replace("/", "");
        }
        String hierarchyAwareMediaID = MediaIDHelper.createMediaID(
                metadata.getDescription().getMediaId(), category, genre);
        MediaMetadataCompat copy = new MediaMetadataCompat.Builder(metadata)
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, hierarchyAwareMediaID)
                .build();

        MediaDescriptionCompat.Builder bob = new MediaDescriptionCompat.Builder();
        bob.setMediaId(copy.getDescription().getMediaId());
        bob.setTitle(copy.getDescription().getTitle());
        bob.setSubtitle(metadata.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE));
        bob.setDescription(metadata.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION));
        bob.setIconBitmap(copy.getDescription().getIconBitmap());
        bob.setIconUri(copy.getDescription().getIconUri());
        Bundle bundle=new Bundle();
        bundle.putLong(MediaMetadataCompat.METADATA_KEY_DURATION,metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION));
        bob.setExtras(bundle);
        MediaDescriptionCompat mDescription = bob.build();

        return new MediaBrowserCompat.MediaItem(mDescription,
                MediaBrowserCompat.MediaItem.FLAG_PLAYABLE);

    }

}

package com.example.android.uamp.playback;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.v4.media.MediaMetadataCompat;
import android.util.LruCache;

import com.example.android.uamp.model.FavouriteData;
import com.example.android.uamp.model.MutableMediaMetadata;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static android.content.Context.MODE_PRIVATE;

/**
 * Created by sagar on 30/11/16.
 */

public class FavouriteManager {

    private static Map<String, MediaMetadataCompat>  mFavourites=new HashMap<>();

    public static void addFavouriteItem(String musicId, MediaMetadataCompat mediaMetadataCompat) {
        mFavourites.put(musicId, mediaMetadataCompat);
    }

    public static Map<String, MediaMetadataCompat> getFavourites() {
        return mFavourites;
    }

    public static void saveFavourites(Context context) {

        try {
            SharedPreferences.Editor editor = context.getSharedPreferences("MY_Favourites", MODE_PRIVATE).edit();
            Gson gson = new Gson();
            Type type = new TypeToken<List<FavouriteData>>() {
            }.getType();
            List<FavouriteData> favouriteDataList = new ArrayList<>();
            for (String musicId : mFavourites.keySet()) {
                MediaMetadataCompat mediaMetadataCompat = mFavourites.get(musicId);
                FavouriteData favouriteData = new FavouriteData();
                Map<String, String> metadata = new HashMap<>();
                metadata.put(MediaMetadataCompat.METADATA_KEY_ALBUM, mediaMetadataCompat.getString(MediaMetadataCompat.METADATA_KEY_ALBUM));
                metadata.put(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, mediaMetadataCompat.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID));
                metadata.put(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, mediaMetadataCompat.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION));
                metadata.put(MediaMetadataCompat.METADATA_KEY_ARTIST, mediaMetadataCompat.getString(MediaMetadataCompat.METADATA_KEY_ARTIST));
                metadata.put(MediaMetadataCompat.METADATA_KEY_GENRE, mediaMetadataCompat.getString(MediaMetadataCompat.METADATA_KEY_GENRE));
                metadata.put(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, mediaMetadataCompat.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE));
                metadata.put(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, mediaMetadataCompat.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI));
                metadata.put(MediaMetadataCompat.METADATA_KEY_ART_URI, mediaMetadataCompat.getString(MediaMetadataCompat.METADATA_KEY_ART_URI));
                metadata.put(MediaMetadataCompat.METADATA_KEY_TITLE, mediaMetadataCompat.getString(MediaMetadataCompat.METADATA_KEY_TITLE));
                metadata.put(MediaMetadataCompat.METADATA_KEY_DURATION, mediaMetadataCompat.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) + "");
                favouriteData.setMetadata(metadata);
                favouriteData.setMusicId(musicId);
                favouriteDataList.add(favouriteData);
            }
            String json = gson.toJson(favouriteDataList, type);
            editor.putString("Favourites", json);
            editor.commit();
        }catch (Exception e){

        }
    }

    public static Map<String, MediaMetadataCompat> loadFavourites(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences("MY_Favourites", MODE_PRIVATE);
            String restoredText = prefs.getString("Favourites", null);
            if (restoredText != null) {
                Type type = new TypeToken<List<FavouriteData>>() {
                }.getType();
                Gson gson = new Gson();
                List<FavouriteData> favouriteDataList = gson.fromJson(restoredText, type);
                for (FavouriteData favouriteData : favouriteDataList) {
                    Map<String, String> metadata = favouriteData.getMetadata();
                    String durationStr = metadata.get(MediaMetadataCompat.METADATA_KEY_DURATION);
                    Long duration = 0l;
                    if (durationStr != null) {
                        duration = Long.parseLong(durationStr);
                    }
                    MediaMetadataCompat mediaMetadataCompat = new MediaMetadataCompat.Builder()
                            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, metadata.get(MediaMetadataCompat.METADATA_KEY_MEDIA_ID))
                            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, metadata.get(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION))
                            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, metadata.get(MediaMetadataCompat.METADATA_KEY_ALBUM))
                            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, metadata.get(MediaMetadataCompat.METADATA_KEY_ARTIST))
                            .putString(MediaMetadataCompat.METADATA_KEY_GENRE, metadata.get(MediaMetadataCompat.METADATA_KEY_GENRE))
                            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, metadata.get(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE))
                            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, metadata.get(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI))
                            .putString(MediaMetadataCompat.METADATA_KEY_ART_URI, metadata.get(MediaMetadataCompat.METADATA_KEY_ART_URI))
                            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, metadata.get(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI))
                            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, metadata.get(MediaMetadataCompat.METADATA_KEY_TITLE))
                            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
                            .build();
                    mFavourites.put(favouriteData.getMusicId(), mediaMetadataCompat);
                }
            }
        }catch (Exception e){

        }
        return mFavourites;
    }

    public static void removeFavouriteItem(String musicId) {
        mFavourites.remove(musicId);
    }
}

package com.music.android.uamp.playback;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.v4.media.MediaMetadataCompat;
import android.util.LruCache;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.music.android.uamp.model.FavouriteData;
import com.music.android.uamp.model.MutableMediaMetadata;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import static android.content.Context.MODE_PRIVATE;

/**
 * Created by sagar on 16/7/17.
 */

public class HistoryManager {
    private static LruCache<String, MediaMetadataCompat> mHistory = new LruCache<>(50);

    public static void addHistoryItem(String musicId, MediaMetadataCompat mediaMetadataCompat) {
        mHistory.remove(musicId);
        mHistory.put(musicId, mediaMetadataCompat);
    }

    public static Map<String, MediaMetadataCompat> getHistory() {
        Map<String,MediaMetadataCompat>map= mHistory.snapshot();
        LinkedHashMap<String,MediaMetadataCompat>data=new LinkedHashMap<>();
        ListIterator<String> iterator = new ArrayList<String>(map.keySet()).listIterator(map.size());
        while (iterator.hasPrevious()) {
            String key = iterator.previous();
            data.put(key,map.get(key));
        };
        return  data;
        //return mHistory.snapshot();
    }

    public static void saveHistory(Context context) {

        try {
            SharedPreferences.Editor editor = context.getSharedPreferences("MY_History", MODE_PRIVATE).edit();
            Gson gson = new Gson();
            Type type = new TypeToken<List<FavouriteData>>() {
            }.getType();
            List<FavouriteData> favouriteDataList = new ArrayList<>();
            for (String musicId : getHistory().keySet()) {
                MediaMetadataCompat mediaMetadataCompat = getHistory().get(musicId);
                FavouriteData favouriteData = new FavouriteData();
                Map<String, String> metadata = new LinkedHashMap<>();
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
            Collections.reverse(favouriteDataList);
            String json = gson.toJson(favouriteDataList, type);
            editor.putString("historyItems1", json);
            editor.apply();
        }catch (Exception e){

        }
    }

    public static Map<String, MediaMetadataCompat> loadHistories(Context context) {

        try {
            SharedPreferences prefs = context.getSharedPreferences("MY_History", MODE_PRIVATE);
            String restoredText = prefs.getString("historyItems1", null);
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
                            .putString(MediaMetadataCompat.METADATA_KEY_DATE, metadata.get(MediaMetadataCompat.METADATA_KEY_DATE))
                            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
                            .build();
                    mHistory.put(favouriteData.getMusicId(), mediaMetadataCompat);
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }


        return getHistory();
    }

    public static void removeHistoryItem(String musicId) {
        mHistory.snapshot().remove(musicId);
    }
}

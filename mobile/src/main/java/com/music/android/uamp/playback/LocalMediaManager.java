package com.music.android.uamp.playback;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.os.Environment;
import android.support.v4.media.MediaMetadataCompat;
import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.music.android.uamp.AlbumArtCache;
import com.music.android.uamp.model.FavouriteData;
import com.music.android.uamp.model.MusicProviderSource;
import com.music.android.uamp.utils.BitmapHelper;
import com.music.android.uamp.utils.Constants;
import com.music.android.uamp.utils.TINGenerator;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static android.content.Context.MODE_PRIVATE;

/**
 * Created by sagar on 21/7/17.
 *
 */

public class LocalMediaManager {

    private static String  extensions[]={"mp3",".3gp",".flac",".ota",".ogg"};

    private static Map<String,MediaMetadataCompat> local=new LinkedHashMap<>();

    public static Map<String, MediaMetadataCompat> getLocalMedia(Context context){
        try {
            //refreshLocalMedia(context);
            SharedPreferences prefs = context.getSharedPreferences("MY_Local", MODE_PRIVATE);
            String restoredText = prefs.getString("localItems", null);
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
                            .putString(Constants.CUSTOM_METADATA_TRACK_SOURCE, metadata.get(Constants.CUSTOM_METADATA_TRACK_SOURCE))
                            .putString(Constants.CUSTOM_METADATA_LOCAL, "true")
                            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
                            .build();
                    local.put(favouriteData.getMusicId(), mediaMetadataCompat);
                }
            }
            else{
                loadLocalMedia(context);
                getLocalMedia(context);
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return sortByComparator(local);
    }


    private static Map<String, MediaMetadataCompat> sortByComparator(Map<String, MediaMetadataCompat> unsortMap)
    {

        List<Map.Entry<String, MediaMetadataCompat>> list = new LinkedList<Map.Entry<String, MediaMetadataCompat>>(unsortMap.entrySet());

        // Sorting the list based on values
        Collections.sort(list, new Comparator<Map.Entry<String, MediaMetadataCompat>>()
        {
            public int compare(Map.Entry<String, MediaMetadataCompat> o1,
                               Map.Entry<String, MediaMetadataCompat> o2)
            {
                    return o1.getValue().getString(MediaMetadataCompat.METADATA_KEY_TITLE).compareTo(o2.getValue().getString(MediaMetadataCompat.METADATA_KEY_TITLE));
            }
        });

        // Maintaining insertion order with the help of LinkedList
        Map<String, MediaMetadataCompat> sortedMap = new LinkedHashMap<String, MediaMetadataCompat>();
        for (Map.Entry<String, MediaMetadataCompat> entry : list)
        {
            sortedMap.put(entry.getKey(), entry.getValue());
        }

        return sortedMap;
    }


    public static void refreshLocalMedia(Context context){

        SharedPreferences.Editor editor = context.getSharedPreferences("localMedia", MODE_PRIVATE).edit();
        Set<String> set=new HashSet<>();
        editor.putStringSet("mediaList", set);
        editor.apply();
        editor = context.getSharedPreferences("MY_Local", MODE_PRIVATE).edit();
        editor.putString("localItems", null);
        editor.apply();
    }

    private static Map<String, MediaMetadataCompat> loadLocalMedia(Context context){
        SharedPreferences sharedPreferences = context.getSharedPreferences("localMedia", MODE_PRIVATE);
        Set<String> songs = new HashSet<>();
        songs = sharedPreferences.getStringSet("mediaList", songs);
        if(songs == null || songs.isEmpty()) {
            for (String path : getExternalMounts()) {
                listOfSongs(path, songs);
            }
            listOfSongs(Environment.getExternalStorageDirectory().getAbsolutePath(), songs);
            SharedPreferences.Editor editor = context.getSharedPreferences("localMedia", MODE_PRIVATE).edit();
            editor.putStringSet("mediaList", songs);
            editor.apply();
        }

        SharedPreferences.Editor editor = context.getSharedPreferences("MY_Local", MODE_PRIVATE).edit();
        Gson gson = new Gson();
        Type type = new TypeToken<List<FavouriteData>>() {
        }.getType();

        Map<String,MediaMetadataCompat> localMedia=new LinkedHashMap<>();

        List<FavouriteData> favouriteDataList = new ArrayList<>();
        AlbumArtCache cache = AlbumArtCache.getInstance();
        for(String file:songs){
            MediaMetadataRetriever metaRetriver = new MediaMetadataRetriever();
            metaRetriver.setDataSource(file);
            Map<String,String> localMediaLocal=new LinkedHashMap<>();
            try {
                String musicId=TINGenerator.getNextTIN();
                FavouriteData favouriteData = new FavouriteData();
                /*Bitmap songImage=null;
                try {
                    byte art[] = metaRetriver.getEmbeddedPicture();
                    if(art != null) {
                        songImage = BitmapFactory.decodeByteArray(art, 0, art.length);
                        cache.setBitMap(songImage,file);
                    }

                }catch(Exception e){
                    e.printStackTrace();
                }*/
                String title=metaRetriver .extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                String duration=metaRetriver .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                if(duration == null || Long.parseLong(duration) < 60000){
                    continue;
                }
                if(title == null || title.isEmpty())
                {
                    File file1=new File(file);
                    title=file1.getName();
                }
                String artist=metaRetriver .extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
                String gener=metaRetriver .extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE);
                String alubum=metaRetriver .extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);

                //String id=metaRetriver .extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER);

                localMediaLocal.put(MediaMetadataCompat.METADATA_KEY_MEDIA_ID,musicId);
                localMediaLocal.put(MediaMetadataCompat.METADATA_KEY_TITLE,title);
                localMediaLocal.put(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION,title);
                localMediaLocal.put(MediaMetadataCompat.METADATA_KEY_ALBUM,alubum);
                localMediaLocal.put(MediaMetadataCompat.METADATA_KEY_ARTIST,artist);
                localMediaLocal.put(MediaMetadataCompat.METADATA_KEY_GENRE,gener);
                localMediaLocal.put(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE,alubum);
                localMediaLocal.put(MediaMetadataCompat.METADATA_KEY_ALBUM_ART,musicId);
                localMediaLocal.put(MediaMetadataCompat.METADATA_KEY_DURATION, duration);
                localMediaLocal.put(MediaMetadataCompat.METADATA_KEY_ART_URI, file);
                localMediaLocal.put(Constants.CUSTOM_METADATA_TRACK_SOURCE, file);

                favouriteData.setMetadata(localMediaLocal);
                favouriteData.setMusicId(musicId);
                favouriteDataList.add(favouriteData);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            String json = gson.toJson(favouriteDataList, type);
            editor.putString("localItems", json);
            editor.apply();

        }
        return localMedia;
    }


    public static HashSet<String> getExternalMounts() {
        final HashSet<String> out = new HashSet<String>();
        String reg = "(?i).*vold.*(vfat|ntfs|exfat|fat32|ext3|ext4).*rw.*";
        String s = "";
        try {
            final Process process = new ProcessBuilder().command("mount")
                    .redirectErrorStream(true).start();
            process.waitFor();
            final InputStream is = process.getInputStream();
            final byte[] buffer = new byte[1024];
            while (is.read(buffer) != -1) {
                s = s + new String(buffer);
            }
            is.close();
        } catch (final Exception e) {
            e.printStackTrace();
        }

        // parse output
        final String[] lines = s.split("\n");
        for (String line : lines) {
            if (!line.toLowerCase(Locale.US).contains("asec")) {
                if (line.matches(reg)) {
                    String[] parts = line.split(" ");
                    for (String part : parts) {
                        if (part.startsWith("/"))
                            if (!part.toLowerCase(Locale.US).contains("vold"))
                                out.add(part);
                    }
                }
            }
        }
        return out;
    }


    public static  void listOfSongs(String directoryName, Set<String> files) {
        File directory = new File(directoryName);

        // get all the files from a directory
        File[] fList = directory.listFiles();
        for (File file : fList) {
            if (file.isFile()) {
                String extension = "";
                String fileName=file.getName();
                int i = fileName.lastIndexOf('.');
                if (i > 0) {
                    extension = fileName.substring(i+1);
                    for(String staticExtension:extensions){
                        if(extension.equalsIgnoreCase(staticExtension)){
                            files.add(file.getAbsolutePath());
                        }
                    }
                }
            } else if (file.isDirectory()) {
                listOfSongs(file.getAbsolutePath(), files);
            }
        }
    }
}

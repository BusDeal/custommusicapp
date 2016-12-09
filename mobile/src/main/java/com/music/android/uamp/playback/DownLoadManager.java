package com.music.android.uamp.playback;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.support.v4.media.MediaMetadataCompat;

import com.music.android.uamp.model.MusicProvider;
import com.music.android.uamp.model.MusicProviderSource;
import com.music.android.uamp.model.MutableMediaMetadata;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by sagar on 5/11/16.
 */

public class DownLoadManager {

    /**
     * @param context used to check the device version and DownloadManager information
     * @return true if the download manager is available
     */
    MusicProvider musicProvider;

    Context context;

    public DownLoadManager(Context context, MusicProvider musicProvider) {
        this.musicProvider = musicProvider;
        this.context = context;
    }

    public boolean isDownloadManagerAvailable() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            return true;
        }
        return false;
    }

    public void getAudioUrlAndDownLoad(final String musicId, final BroadcastReceiver broadcastReceiver) {
        new AsyncTask<String, Void, String>() {
            @Override
            protected String doInBackground(String... params) {
                return musicProvider.getSourceUrl(musicId);

            }

            @Override
            protected void onPostExecute(String source) {
                DownLoadManager.this.downLoad(source, broadcastReceiver);
            }
        }.execute();
    }

    public Boolean downLoad(final String musicId, final BroadcastReceiver broadcastReceiver) {


        Map<String, MutableMediaMetadata> downloadedMedia = getDownloadedMedia(context);
        if (downloadedMedia.containsKey(musicId)) {
            return false;
        }
        new AsyncTask<String, Void, String>() {
            @Override
            protected String doInBackground(String... params) {
                return musicProvider.getSourceUrl(musicId);

            }

            @Override
            protected void onPostExecute(String source) {
                if (source == null) {
                    return;
                }
                MediaMetadataCompat mediaMetadataCompat = musicProvider.getMusic(musicId);
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(source));
                request.setDescription(mediaMetadataCompat.getDescription().getDescription());
                request.setTitle(mediaMetadataCompat.getDescription().getTitle() + "::" + musicId + "::" + mediaMetadataCompat.getLong(MediaMetadataCompat.METADATA_KEY_DURATION));
                request.setMimeType("audio");
                // in order for this if to run, you must use the android 3.2 to compile your app
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                    request.allowScanningByMediaScanner();
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                }
                request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, mediaMetadataCompat.getDescription().getMediaId() + "/" + mediaMetadataCompat.getDescription().getTitle() + ".mp4");

                // get download service and enqueue file
                DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                manager.enqueue(request);
                request = new DownloadManager.Request(mediaMetadataCompat.getDescription().getIconUri());
                request.setDescription(mediaMetadataCompat.getDescription().getDescription());
                request.setTitle(mediaMetadataCompat.getDescription().getTitle() + "::" + musicId);
                request.setMimeType("img");
                request.setVisibleInDownloadsUi(false);
                request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, mediaMetadataCompat.getDescription().getMediaId() + "/images/" + mediaMetadataCompat.getDescription().getTitle() + ".jpg");
                manager.enqueue(request);

                context.registerReceiver(broadcastReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

            }
        }.execute();

        return true;
    }

    public static Map<String, MutableMediaMetadata> getDownloadedMedia(Context context) {

        Map<String, MutableMediaMetadata> mMusicListById = new LinkedHashMap<>();
        try {
            DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            Cursor cursor = manager.query(new DownloadManager.Query());
            cursor.moveToFirst();


            while (!cursor.isAfterLast()) {
                String status = cursor.getString(7);
                if (!status.equalsIgnoreCase("200")) {
                    cursor.moveToNext();
                    continue;
                }
                String mimeType = cursor.getString(9);
                String title = cursor.getString(4);
                String desc = cursor.getString(5);

                if (mimeType == null || !(mimeType.equalsIgnoreCase("img") || mimeType.equalsIgnoreCase("audio"))) {
                    cursor.moveToNext();
                    continue;
                }
                String data[] = title.split("::");
                if (data.length < 2) {
                    cursor.moveToNext();
                    continue;
                }

                String src = cursor.getString(1);
                File file = new File(src);
                if (!file.exists()) {
                    cursor.moveToNext();
                    continue;
                }
                title = data[0];
                String id = data[1];
                Long duration = 0l;
                if (data.length > 2) {
                    duration = new Long(data[2]);
                    duration = duration * 1000;
                }
                String musicId = id;
                MutableMediaMetadata mediaMetadata = mMusicListById.get(musicId);
                if (mimeType.equalsIgnoreCase("img")) {

                    if (mediaMetadata != null) {
                        MediaMetadataCompat compat = new MediaMetadataCompat.Builder(mediaMetadata.metadata)
                                .putString(MediaMetadataCompat.METADATA_KEY_ART_URI, cursor.getString(1))
                                .build();
                        mediaMetadata.metadata = compat;
                    } else {
                        MediaMetadataCompat compat = new MediaMetadataCompat.Builder()
                                .putString(MediaMetadataCompat.METADATA_KEY_ART_URI, cursor.getString(1))
                                .build();
                        MutableMediaMetadata mutableMediaMetadata = new MutableMediaMetadata(musicId, compat);
                        mMusicListById.put(musicId, mutableMediaMetadata);
                    }
                    cursor.moveToNext();
                    continue;
                }


                String album = title, genre = title, artist = title;
                String args[] = null;
                if (title.contains("||")) {
                    args = title.split("\\|\\|");
                } else if (title.contains("|")) {
                    args = title.split("\\|");
                } else if (title.contains("-")) {
                    args = title.split("-");
                }
                if (args != null) {
                    if (args.length >= 1) {
                        album = args[0];
                        genre = args[1];
                        artist = args[1];
                    }
                    if (args.length > 2) {
                        artist = args[2];
                    }
                }

                album = album.replace("|", "");
                genre = genre.replace("|", "");
                artist = artist.replace("|", "");
                if (mediaMetadata != null) {
                    MediaMetadataCompat mediaMetadataCompat = new MediaMetadataCompat.Builder(mediaMetadata.metadata)
                            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, musicId)
                            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, desc)
                            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
                            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                            .putString(MediaMetadataCompat.METADATA_KEY_GENRE, genre)
                            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
                            .putString(MusicProviderSource.CUSTOM_METADATA_TRACK_SOURCE, src)
                            .build();

                    MutableMediaMetadata mutableMediaMetadata = new MutableMediaMetadata(musicId, mediaMetadataCompat);
                    mMusicListById.put(musicId, mutableMediaMetadata);
                } else {
                    MediaMetadataCompat mediaMetadataCompat = new MediaMetadataCompat.Builder()
                            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, musicId)
                            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, desc)
                            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
                            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                            .putString(MediaMetadataCompat.METADATA_KEY_GENRE, genre)
                            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
                            .putString(MusicProviderSource.CUSTOM_METADATA_TRACK_SOURCE, src)
                            .build();

                    MutableMediaMetadata mutableMediaMetadata = new MutableMediaMetadata(musicId, mediaMetadataCompat);
                    mMusicListById.put(musicId, mutableMediaMetadata);
                }
                cursor.moveToNext();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return mMusicListById;
    }
}

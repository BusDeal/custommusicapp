package com.music.android.uamp.playback;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.support.v4.media.MediaMetadataCompat;

import com.music.android.uamp.model.AudioMetaData;
import com.music.android.uamp.model.MusicProvider;
import com.music.android.uamp.model.MusicProviderSource;
import com.music.android.uamp.model.MutableMediaMetadata;
import com.music.android.uamp.utils.Constants;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import static android.os.AsyncTask.THREAD_POOL_EXECUTOR;

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



    public Boolean downLoad(final String musicId, final BroadcastReceiver broadcastReceiver) {


        Map<String, MutableMediaMetadata> downloadedMedia = getDownloadedMedia(context,musicProvider);
        if (downloadedMedia.containsKey(musicId)) {
            return false;
        }
        new AsyncTask<String, Void, AudioMetaData>() {
            @Override
            protected AudioMetaData doInBackground(String... params) {
                return musicProvider.getSourceUrl(musicId);

            }

            @Override
            protected void onPostExecute(AudioMetaData source) {
                if (source == null) {
                    return;
                }
                MediaMetadataCompat mediaMetadataCompat = musicProvider.getMusic(musicId);
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(source.getUrl()));
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
        }.executeOnExecutor(THREAD_POOL_EXECUTOR);

        return true;
    }


    public static Boolean downLoadImage(final String musicId,final Context context,final MusicProvider musicProvider) {

        new AsyncTask<String, Void, AudioMetaData>() {
            @Override
            protected AudioMetaData doInBackground(String... params) {
                return musicProvider.getSourceUrl(musicId);

            }

            @Override
            protected void onPostExecute(AudioMetaData source) {
                if (source == null) {
                    return;
                }
                MediaMetadataCompat mediaMetadataCompat = musicProvider.getMusic(musicId);
                DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                DownloadManager.Request request = new DownloadManager.Request(mediaMetadataCompat.getDescription().getIconUri());
                request.setDescription(mediaMetadataCompat.getDescription().getDescription());
                request.setTitle(mediaMetadataCompat.getDescription().getTitle() + "::" + musicId);
                request.setMimeType("img");
                request.setVisibleInDownloadsUi(false);
                request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, mediaMetadataCompat.getDescription().getMediaId() + "/images/" + mediaMetadataCompat.getDescription().getTitle() + ".jpg");
                manager.enqueue(request);

            }
        }.executeOnExecutor(THREAD_POOL_EXECUTOR);

        return true;
    }


    private static String getImageFromSourceFile(String src,String musicId,Context context,MusicProvider musicProvider){
        File file=new File(src);
        if(file.exists()){
            String fileName=file.getName();
            int i = fileName.lastIndexOf('.');
            if (i > 0) {
                fileName=fileName.substring(0,i);
                fileName=fileName+".jpg";
                String filePath=file.getPath();
                filePath=filePath.substring(0,filePath.lastIndexOf("/"));
                filePath+="/images/";
                String fullFile=filePath+ fileName;
                file=new File(fullFile);
                if(!file.exists()){
                    return "https://img.youtube.com/vi/"+musicId+"/hqdefault.jpg";
                }
                return fullFile;
            }
            return "https://img.youtube.com/vi/"+musicId+"/hqdefault.jpg";
        }else{
            return "https://img.youtube.com/vi/"+musicId+"/hqdefault.jpg";
        }
    }

    public static Map<String, MutableMediaMetadata> getDownloadedMedia(Context context,MusicProvider provider) {

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


                String src = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
                File file = new File(Uri.parse(src).getPath());
                if (!file.exists()) {
                    cursor.moveToNext();
                    continue;
                }
                title = data[0];
                String id = data[1];
                Long duration = 0l;
                if (data.length > 2) {
                    duration = new Long(data[2]);
                    if(duration%1000 != 0){
                        duration = duration * 1000;
                    }
                }
                String musicId = id;
                MutableMediaMetadata mediaMetadata = mMusicListById.get(musicId);
                if (mimeType.equalsIgnoreCase("img")) {

                    if (mediaMetadata != null) {
                        String url = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
                        MediaMetadataCompat compat = new MediaMetadataCompat.Builder(mediaMetadata.metadata)
                                .putString(MediaMetadataCompat.METADATA_KEY_ART_URI, Uri.parse(url).getPath())
                                .build();
                        mediaMetadata.metadata = compat;
                    } else {
                        String url = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
                        MediaMetadataCompat compat = new MediaMetadataCompat.Builder()
                                .putString(MediaMetadataCompat.METADATA_KEY_ART_URI, Uri.parse(url).getPath())
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
                            .putString(MediaMetadataCompat.METADATA_KEY_ART_URI, getImageFromSourceFile(src,musicId,context,provider))
                            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, desc)
                            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
                            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                            .putString(MediaMetadataCompat.METADATA_KEY_GENRE, genre)
                            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
                            .putString(Constants.CUSTOM_METADATA_TRACK_SOURCE, src)
                            .build();

                    MutableMediaMetadata mutableMediaMetadata = new MutableMediaMetadata(musicId, mediaMetadataCompat);
                    mMusicListById.put(musicId, mutableMediaMetadata);
                } else {
                    MediaMetadataCompat mediaMetadataCompat = new MediaMetadataCompat.Builder()
                            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, musicId)
                            .putString(MediaMetadataCompat.METADATA_KEY_ART_URI, getImageFromSourceFile(src,musicId,context,provider))
                            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, desc)
                            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
                            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                            .putString(MediaMetadataCompat.METADATA_KEY_GENRE, genre)
                            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
                            .putString(Constants.CUSTOM_METADATA_TRACK_SOURCE, src)
                            .build();

                    MutableMediaMetadata mutableMediaMetadata = new MutableMediaMetadata(musicId, mediaMetadataCompat);
                    mMusicListById.put(musicId, mutableMediaMetadata);
                }
                cursor.moveToNext();
            }
            cursor.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return mMusicListById;
    }
}

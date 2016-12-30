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

package com.music.android.uamp.model;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.v4.media.MediaMetadataCompat;

import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.StandardExceptionParser;
import com.google.android.gms.analytics.Tracker;
import com.music.android.uamp.AnalyticsApplication;
import com.music.android.uamp.utils.LogHelper;
import com.music.android.uamp.utils.ParserHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static android.content.Context.MODE_PRIVATE;

/**
 * Utility class to get a list of MusicTrack's based on a server-side JSON
 * configuration.
 */
public class RemoteJSONSource implements MusicProviderSource {

    private static final String TAG = LogHelper.makeLogTag(RemoteJSONSource.class);

    private static String audioUrl = "http://kmusic.in:8080/watch?";
    private static String duration = "https://www.googleapis.com/youtube/v3/videos?part=contentDetails&key=AIzaSyD3UusulV2oYNHYwKjPBrv0ZDXdZ3CX6Ys&id=";
    protected static String CATALOG_URL =
            "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&key=AIzaSyD3UusulV2oYNHYwKjPBrv0ZDXdZ3CX6Ys&maxResults=20";

    private static final String JSON_MUSIC = "music";
    private static final String JSON_TITLE = "title";
    private static final String JSON_ALBUM = "album";
    private static final String JSON_ARTIST = "artist";
    private static final String JSON_DESC = "description";
    private static final String JSON_GENRE = "genre";
    private static final String JSON_SOURCE = "source";
    private static final String JSON_IMAGE = "image";
    private static final String JSON_TRACK_NUMBER = "trackNumber";
    private static final String JSON_TOTAL_TRACK_COUNT = "totalTrackCount";
    private static final String JSON_DURATION = "duration";
    private static String queryList[] = {"songs", "top 20 songs", "latest songs", "top 10 songs of the week", "melody songs", "best 20 songs"};
    private final Tracker mTracker;
    private Context context;

    public RemoteJSONSource(Context context) {
        this.context = context;
        GoogleAnalytics analytics = GoogleAnalytics.getInstance(context);
        analytics.setLocalDispatchPeriod(30);
        mTracker = analytics.newTracker("UA-88784216-1"); // Replace with actual tracker id
        mTracker.enableExceptionReporting(true);
        mTracker.enableAdvertisingIdCollection(true);
        mTracker.enableAutoActivityTracking(true);
        //mTracker = application.getDefaultTracker();
    }

    @Override
    public Iterator<MediaMetadataCompat> iterator(RetrieveType retrieveType, String... params) {
        try {
            SharedPreferences sharedPreferences = context.getSharedPreferences("languageSelection", MODE_PRIVATE);
            Boolean isLanguageSelected = sharedPreferences.getBoolean("isLanguageSelected", false);
            Set<String> language = new HashSet<>();
            if (isLanguageSelected) {
                language = sharedPreferences.getStringSet("languageSelectionSet", language);
            }
            String apiUrl = "";
            if (retrieveType.DEFAULT == retrieveType) {
                Random r = new Random();
                int num = r.nextInt(queryList.length);
                try {
                    String query = queryList[num];
                    if (!language.isEmpty()) {
                        query = getRandomLang(language) + " " + query;
                    }
                    apiUrl = CATALOG_URL + "&q=" + URLEncoder.encode(query, "utf-8");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            } else if (retrieveType.SEARCH == retrieveType) {
                StringBuilder sb = new StringBuilder();
                for (String param : params) {
                    sb.append(param);
                }
                if (!(sb.toString().contains("songs") || sb.toString().contains("song") || sb.toString().contains("jukebox"))) {
                    sb.append("songs");
                }
                String query = sb.toString();
                try {
                    query = URLEncoder.encode(sb.toString(), "utf-8");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
                apiUrl = CATALOG_URL + ("&q=" + query);
            } else if (retrieveType.VIDEOID == retrieveType) {
                StringBuilder sb = new StringBuilder();
                for (String param : params) {
                    sb.append(param);
                }
                apiUrl = CATALOG_URL + ("&relatedToVideoId=" + sb.toString());
            }

            LogHelper.e(TAG, "catalog url " + apiUrl);
            int slashPos = CATALOG_URL.lastIndexOf('/');
            String path = CATALOG_URL.substring(0, slashPos + 1);
            JSONObject jsonObj = fetchJSONFromUrl(apiUrl);
            ArrayList<MediaMetadataCompat> tracks = new ArrayList<>();
            if (jsonObj != null) {
                JSONArray jsonTracks = jsonObj.getJSONArray("items");

                if (jsonTracks != null) {
                    for (int j = 0; j < jsonTracks.length(); j++) {
                        tracks.add(buildFromJSON(jsonTracks.getJSONObject(j), path));
                    }
                }
            }
            return tracks.iterator();
        } catch (JSONException e) {
            LogHelper.e(TAG, e, "Could not retrieve music list");
            throw new RuntimeException("Could not retrieve music list", e);
        } catch (Exception e) {
            mTracker.send(new HitBuilders.ExceptionBuilder()
                    .setDescription(new StandardExceptionParser(this.context, null)
                            .getDescription(Thread.currentThread().getName(), e))
                    .setFatal(false)
                    .build());
            throw new RuntimeException("Could not retrieve music list", e);
        }
    }

    private String getRandomLang(Set<String> myHashSet) {
        int size = myHashSet.size();
        int item = new Random().nextInt(size);
        int i = 0;
        for (String obj : myHashSet) {
            if (i == item)
                return obj;
            i = i + 1;
        }
        return null;
    }

    @Override
    public AudioMetaData getAudioSourceUrl(String videoId) {

        LogHelper.e(TAG, "", audioUrl + "source=" + videoId);
        JSONObject jsonObject = null;
        try {
            Long currentTimeMilisec = System.currentTimeMillis();
            CrawlYouTube crawlYouTube = new CrawlYouTube();
            String data = crawlYouTube.run(videoId);
            LogHelper.e("Tag","Total time took to crawl " + (System.currentTimeMillis() - currentTimeMilisec) / 1000);
            if (data != null) {
                jsonObject = new JSONObject(data);
            } else {
                mTracker.send(new HitBuilders.EventBuilder()
                        .setCategory("server")
                        .setAction("audioUrl")
                        .setLabel(videoId)
                        .build());
                jsonObject = fetchJSONFromUrl(audioUrl + "source=" + videoId);
            }
            return getAudioUrl(jsonObject);
        } catch (JSONException e) {
            mTracker.send(new HitBuilders.ExceptionBuilder()
                    .setDescription(new StandardExceptionParser(this.context, null)
                            .getDescription(Thread.currentThread().getName(), e))
                    .setFatal(false)
                    .build());
            e.printStackTrace();
        } catch (Exception e) {
            mTracker.send(new HitBuilders.ExceptionBuilder()
                    .setDescription(new StandardExceptionParser(this.context, null)
                            .getDescription(Thread.currentThread().getName(), e))
                    .setFatal(false)
                    .build());
            e.printStackTrace();
        }
        return null;
    }

    public void updateDuration(Map<String, MutableMediaMetadata> metaData) {

        String id = "";
        for (String musicId : metaData.keySet()) {
            id += musicId + ",";
        }

        try {
            String url = duration + URLEncoder.encode(id, "utf-8");
            JSONObject jsonObject = fetchJSONFromUrl(url);
            JSONArray jsonArray = jsonObject.getJSONArray("items");
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject item = jsonArray.getJSONObject(i);
                String musicid = item.getString("id");
                JSONObject contentDetails = item.getJSONObject("contentDetails");
                if (contentDetails != null) {
                    String duration = contentDetails.getString("duration");

                    MutableMediaMetadata mutableMediaMetadata = metaData.get(musicid);
                    MediaMetadataCompat mediaMetadataCompat = new MediaMetadataCompat.Builder(mutableMediaMetadata.metadata)
                            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, ParserHelper.getDuration(duration))

                            .build();
                    mutableMediaMetadata.metadata = mediaMetadataCompat;
                    metaData.put(musicid, mutableMediaMetadata);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
            //throw new RuntimeException("Unable to get duration ", e);
        }
    }


    private AudioMetaData getAudioUrl(JSONObject jsonObject) {
        try {
            AudioMetaData audioMetaData = new AudioMetaData();
            JSONArray jsonTracks = jsonObject.getJSONArray("urls");
            if (jsonTracks != null) {
                for (int j = 0; j < jsonTracks.length(); j++) {
                    JSONObject data = jsonTracks.getJSONObject(j);
                    if (data.getString("url").contains("audio")) {
                        audioMetaData.setUrl(data.getString("url"));
                        break;
                    }
                }
            }
            if (jsonObject.has("durations")) {
                jsonTracks = jsonObject.getJSONArray("durations");
                List<Long> durations = new ArrayList<>();
                if (jsonTracks != null) {
                    for (int j = 0; j < jsonTracks.length(); j++) {
                        Long data = jsonTracks.getLong(j);
                        durations.add(data);
                    }
                }
                audioMetaData.setDurations(durations);
            }
            return audioMetaData;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private MediaMetadataCompat buildFromJSON(JSONObject json, String basePath) throws JSONException {
        JSONObject idDetails = json.getJSONObject("id");
        String videoId = idDetails.getString("videoId");
        JSONObject snippet = json.getJSONObject("snippet");
        String title = snippet.getString(JSON_TITLE);
        String channelTitle = snippet.getString("channelTitle");
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
            if (args.length > 1) {
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
        String desc = snippet.getString(JSON_DESC);
        JSONObject thumbnail = snippet.getJSONObject("thumbnails");
        String iconUrl = thumbnail.getJSONObject("default").getString("url");
        String medium = thumbnail.getJSONObject("medium").getString("url");
        String high = thumbnail.getJSONObject("high").getString("url");


        LogHelper.d(TAG, "Found music track: ", json);

        // Media is stored relative to JSON file
        if (!iconUrl.startsWith("http")) {
            iconUrl = basePath + iconUrl;
        }


        // Since we don't have a unique ID in the server, we fake one using the hashcode of
        // the music source. In a real world app, this could come from the server.
        String id = videoId;

        // Adding the music source to the MediaMetadata (and consequently using it in the
        // mediaSession.setMetadata) is not a good idea for a real world music app, because
        // the session metadata can be accessed by notification listeners. This is done in this
        // sample for convenience only.
        //noinspection ResourceType
        return new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, id)
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, desc)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                .putString(MediaMetadataCompat.METADATA_KEY_GENRE, genre)
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, channelTitle)
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, medium)
                .putString(MediaMetadataCompat.METADATA_KEY_ART_URI, high)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, iconUrl)
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .build();
    }

    /**
     * Download a JSON file from a server, parse the content and return the JSON
     * object.
     *
     * @return result JSONObject containing the parsed representation.
     */
    public static JSONObject fetchJSONFromUrl(String urlString) throws JSONException {
        BufferedReader reader = null;
        try {
            URLConnection urlConnection = new URL(urlString).openConnection();
            HttpURLConnection httpURLConnection = (HttpURLConnection) urlConnection;
            int status = httpURLConnection.getResponseCode();
            InputStream in;
            if (status >= 400) {
                in = httpURLConnection.getErrorStream();
            } else {
                in = urlConnection.getInputStream();
            }
            reader = new BufferedReader(new InputStreamReader(
                    in, "iso-8859-1"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return new JSONObject(sb.toString());
        } catch (JSONException e) {
            throw e;
        } catch (Exception e) {
            LogHelper.e(TAG, "Failed to parse the json for media list", e);
            return null;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    public static JSONArray fetchJSONArrayFromUrl(String urlString) throws JSONException {
        BufferedReader reader = null;
        try {
            URLConnection urlConnection = new URL(urlString).openConnection();
            reader = new BufferedReader(new InputStreamReader(
                    urlConnection.getInputStream(), "iso-8859-1"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return new JSONArray(sb.toString());
        } catch (JSONException e) {
            throw e;
        } catch (Exception e) {
            LogHelper.e(TAG, "Failed to parse the json for media list", e);
            return null;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }
}

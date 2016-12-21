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

import android.support.v4.media.MediaMetadataCompat;

import com.music.android.uamp.utils.LogHelper;

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
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

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

    @Override
    public Iterator<MediaMetadataCompat> iterator(RetrieveType retrieveType, String... params) {
        try {
            String apiUrl = "";
            if (retrieveType.DEFAULT == retrieveType) {
                Random r = new Random();
                int num = r.nextInt(queryList.length);
                try {
                    apiUrl = CATALOG_URL + "&q=" + URLEncoder.encode(queryList[num], "utf-8");
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
        }
    }

    @Override
    public String getAudioSourceUrl(String videoId) {

        LogHelper.e(TAG, "", audioUrl + "source=" + videoId);
        JSONObject jsonObject = null;
        try {
            CrawlYouTube crawlYouTube = new CrawlYouTube();
            String data = crawlYouTube.run(videoId);
            if(data != null) {
                jsonObject = new JSONObject(data);
            }else {
                jsonObject = fetchJSONFromUrl(audioUrl + "source=" + videoId);
            }
            return getAudioUrl(jsonObject);
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (Exception e) {
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
                            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, getDuration(duration))

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

    public long getDuration(String dur) {
        String time = dur.substring(2);
        long duration = 0L;
        Object[][] indexs = new Object[][]{{"H", 3600}, {"M", 60}, {"S", 1}};
        for (int i = 0; i < indexs.length; i++) {
            int index = time.indexOf((String) indexs[i][0]);
            if (index != -1) {
                String value = time.substring(0, index);
                duration += Integer.parseInt(value) * (int) indexs[i][1] * 1000;
                time = time.substring(value.length() + 1);
            }
        }
        return duration;
    }

    private String getAudioUrl(JSONObject jsonObject) {
        try {
            JSONArray jsonTracks = jsonObject.getJSONArray("urls");
            if (jsonTracks != null) {
                for (int j = 0; j < jsonTracks.length(); j++) {
                    JSONObject data = jsonTracks.getJSONObject(j);
                    if (data.getString("url").contains("audio")) {
                        return data.getString("url");
                    }
                }
            }
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

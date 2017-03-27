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
package com.music.android.uamp.ui;

import android.app.FragmentTransaction;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.wifi.WifiInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v7.widget.SearchView;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.Menu;
import android.view.View;
import android.view.WindowManager;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.loveplusplus.update.AppUtils;
import com.loveplusplus.update.UpdateChecker;
import com.music.android.uamp.AnalyticsApplication;
import com.music.android.uamp.R;
import com.music.android.uamp.model.RemoteJSONSource;
import com.music.android.uamp.utils.LogHelper;
import com.music.android.uamp.utils.MediaIDHelper;
import com.music.android.uamp.utils.NetworkHelper;
import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.music.android.uamp.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_DOWNLOAD;
import static com.music.android.uamp.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_FAVOURITE;
import static com.music.android.uamp.utils.MediaIDHelper.MEDIA_ID_ROOT;

/**
 * Main activity for the music player.
 * This class hold the MediaBrowser and the MediaController instances. It will create a MediaBrowser
 * when it is created and connect/disconnect on start/stop. Thus, a MediaBrowser will be always
 * connected while this activity is running.
 */
public class MusicPlayerActivity extends BaseActivity
        implements MediaBrowserFragment.MediaFragmentListener {

    private static final String TAG = LogHelper.makeLogTag(MusicPlayerActivity.class);
    private static final String SAVED_MEDIA_ID = "com.music.android.uamp.MEDIA_ID";
    private static final String FRAGMENT_TAG = "uamp_list_container";

    public static final String EXTRA_START_FULLSCREEN =
            "com.music.android.uamp.EXTRA_START_FULLSCREEN";

    /**
     * Optionally used with {@link #EXTRA_START_FULLSCREEN} to carry a MediaDescription to
     * the {@link FullScreenPlayerActivity}, speeding up the screen rendering
     * while the {@link android.support.v4.media.session.MediaControllerCompat} is connecting.
     */
    public static final String EXTRA_CURRENT_MEDIA_DESCRIPTION =
            "com.music.android.uamp.CURRENT_MEDIA_DESCRIPTION";

    private Bundle mVoiceSearchParams;
    private SearchView searchView;
    //private Tracker mTracker;
    private static LruCache<String, Integer> suggestionSelected = new LruCache<>(20);
    private FirebaseAnalytics mFirebaseAnalytics;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        SharedPreferences sharedPreferences = getApplicationContext().getSharedPreferences("versions", MODE_PRIVATE);
        try {
            Boolean isForceUpdateRequired = sharedPreferences.getBoolean("isForceUpdateRequired", false);
            Boolean isPartialUpdateRequired = sharedPreferences.getBoolean("isPartialUpdateRequired", false);
            if (isPartialUpdateRequired) {
                ProgressDialog progress = new ProgressDialog(this);
                progress.setMessage("Please update to latest version");
                progress.setCancelable(true); // disable dismiss by tapping outside of the dialog
                progress.show();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        NetworkHelper.setScreenOff(this);
        LogHelper.d(TAG, "Activity onCreate");
        AnalyticsApplication application = (AnalyticsApplication) getApplication();
        //mTracker = application.getDefaultTracker();
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);
        initializeToolbar();

        initializeFromParams(savedInstanceState, getIntent());


        // Only check if a full screen player is needed on the first time:
        if (savedInstanceState == null) {
            startFullScreenActivityIfNeeded(getIntent());
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        SharedPreferences prefs = getSharedPreferences("MY_SUGGESTIONS", MODE_PRIVATE);
        String restoredText = prefs.getString("suggestions", null);
        if (restoredText != null) {
            Gson gson = new Gson();
            SearchSuggestion.addSelectedSuggestions(gson.fromJson(restoredText, LruCache.class));
        }

    }

    @Override
    protected void onStop() {
        super.onStop();
        SharedPreferences.Editor editor = getSharedPreferences("MY_SUGGESTIONS", MODE_PRIVATE).edit();
        Gson gson = new Gson();
        String json = gson.toJson(SearchSuggestion.getSuggestionSelected());
        editor.putString("suggestions", json);
        editor.commit();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        String mediaId = getMediaId();
        if (mediaId != null) {
            outState.putString(SAVED_MEDIA_ID, mediaId);
        }
        //super.onSaveInstanceState(outState);
    }

    @Override
    public void onMediaItemSelected(MediaBrowserCompat.MediaItem item) {
        LogHelper.d(TAG, "onMediaItemSelected, mediaId=" + item.getMediaId());
        if (item != null && item.isPlayable()) {
            Intent intent = new Intent(MusicPlayerActivity.this, FullScreenPlayerActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

            intent.putExtra(MusicPlayerActivity.EXTRA_CURRENT_MEDIA_DESCRIPTION,
                    item.getDescription());

            Bundle bundle = new Bundle();
            bundle.putString(FirebaseAnalytics.Param.ITEM_ID, item.getMediaId());
            bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, MediaIDHelper.extractBrowseCategoryTypeFromMediaID(item.getMediaId()));
            bundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, "play");
            bundle.putString(FirebaseAnalytics.Param.ITEM_CATEGORY,MediaIDHelper.extractBrowseCategoryTypeFromMediaID(item.getMediaId()));
            bundle.putString(FirebaseAnalytics.Param.CONTENT, "MusicPlayerActivity");
            mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle);
            getSupportMediaController().getTransportControls()
                    .prepareFromMediaId(item.getMediaId(), null);
            startActivity(intent);
        } else if (item.isBrowsable()) {
            Bundle bundle = new Bundle();
            bundle.putString(FirebaseAnalytics.Param.ITEM_ID, item.getMediaId());
            bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, MediaIDHelper.extractBrowseCategoryTypeFromMediaID(item.getMediaId()));
            bundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, "browse");
            bundle.putString(FirebaseAnalytics.Param.ITEM_CATEGORY,MediaIDHelper.extractBrowseCategoryTypeFromMediaID(item.getMediaId()));
            bundle.putString(FirebaseAnalytics.Param.CONTENT, item.getMediaId());
            mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.VIEW_ITEM_LIST, bundle);
            navigateToBrowser(item.getMediaId());
        } else {
            LogHelper.w(TAG, "Ignoring MediaItem that is neither browsable nor playable: ",
                    "mediaId=", item.getMediaId());
        }
    }

    @Override
    public void setToolbarTitle(CharSequence title) {
        LogHelper.d(TAG, "Setting toolbar title to ", title);
        if (title == null) {
            title = getString(R.string.app_name);
        }
        setTitle(title);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            // Handle the normal search query case
            String query = intent.getStringExtra(SearchManager.QUERY);
        } else if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            //showResult(data);
        } else {
            LogHelper.d(TAG, "onNewIntent, intent=" + intent);
            initializeFromParams(null, intent);
            startFullScreenActivityIfNeeded(intent);
        }
    }


    private void startFullScreenActivityIfNeeded(Intent intent) {
        if (intent != null && intent.getBooleanExtra(EXTRA_START_FULLSCREEN, false)) {
            Intent fullScreenIntent = new Intent(this, FullScreenPlayerActivity.class)
                    .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP |
                            Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(EXTRA_CURRENT_MEDIA_DESCRIPTION,
                            intent.getParcelableExtra(EXTRA_CURRENT_MEDIA_DESCRIPTION));
            startActivity(fullScreenIntent);
        }
    }

    protected void initializeFromParams(Bundle savedInstanceState, Intent intent) {
        String mediaId = null;
        // check if we were started from a "Play XYZ" voice search. If so, we save the extras
        // (which contain the query details) in a parameter, so we can reuse it later, when the
        // MediaSession is connected.
        if (intent.getAction() != null
                && intent.getAction().equals(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)) {
            mVoiceSearchParams = intent.getExtras();
            LogHelper.d(TAG, "Starting from voice search query=",
                    mVoiceSearchParams.getString(SearchManager.QUERY));
        } else if (intent.getAction() != null && Intent.ACTION_SEARCH.equals(getIntent().getAction())) {
            // Handle the normal search query case
            // mVoiceSearchParams = intent.getExtras();
            String query = getIntent().getStringExtra(SearchManager.QUERY);
            ActionBarCastActivity.searchQuery = query;
            mediaId = MediaIDHelper.createMediaID(null, MediaIDHelper.MEDIA_ID_MUSICS_BY_SEARCH, query);
        } else {
            ActionBarCastActivity.searchQuery = null;
            if (savedInstanceState != null) {
                // If there is a saved media ID, use it
                mediaId = savedInstanceState.getString(SAVED_MEDIA_ID);
            } else if (intent.getExtras() != null && intent.getExtras().getString(SAVED_MEDIA_ID) != null) {
                mediaId = intent.getExtras().getString(SAVED_MEDIA_ID);
            } else {
                mediaId = MEDIA_ID_ROOT;
            }
        }
        navigateToBrowser(mediaId);
    }

    private void navigateToBrowser(String mediaId) {
        LogHelper.d(TAG, "navigateToBrowser, mediaId=" + mediaId);
        MediaBrowserFragment fragment = getBrowseFragment();

        if (fragment == null || !TextUtils.equals(fragment.getMediaId(), mediaId)) {
            fragment = new MediaBrowserFragment();
            fragment.setMediaId(mediaId);
            FragmentTransaction transaction = getFragmentManager().beginTransaction();
            transaction.setCustomAnimations(
                    R.animator.slide_in_from_right, R.animator.slide_out_to_left,
                    R.animator.slide_in_from_left, R.animator.slide_out_to_right);
            transaction.replace(R.id.container, fragment, FRAGMENT_TAG);
            // If this is not the top level media (root), we add it to the fragment back stack,
            // so that actionbar toggle and Back will work appropriately:
            /*if (mediaId != null && !(MediaIDHelper.extractBrowseCategoryTypeFromMediaID(mediaId).equalsIgnoreCase(MEDIA_ID_ROOT) || MediaIDHelper.extractBrowseCategoryTypeFromMediaID(mediaId).equalsIgnoreCase(MEDIA_ID_MUSICS_BY_DOWNLOAD) || MediaIDHelper.extractBrowseCategoryTypeFromMediaID(mediaId).equalsIgnoreCase(MEDIA_ID_MUSICS_BY_FAVOURITE))) {
                //transaction.addToBackStack("Tag");
            }*/
            transaction.commit();
        }
    }

    public String getMediaId() {
        MediaBrowserFragment fragment = getBrowseFragment();
        if (fragment == null) {
            return null;
        }
        return fragment.getMediaId();
    }

    private MediaBrowserFragment getBrowseFragment() {
        return (MediaBrowserFragment) getFragmentManager().findFragmentByTag(FRAGMENT_TAG);
    }

    @Override
    protected void onMediaControllerConnected() {
        if (mVoiceSearchParams != null) {
            // If there is a bootstrap parameter to start from a search query, we
            // send it to the media session and set it to null, so it won't play again
            // when the activity is stopped/started or recreated:
            String query = mVoiceSearchParams.getString(SearchManager.QUERY);
            getSupportMediaController().getTransportControls()
                    .playFromSearch(query, mVoiceSearchParams);
            mVoiceSearchParams = null;
        }
        getBrowseFragment().onConnected();
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        SearchManager searchManager =
                (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        searchView =
                (SearchView) menu.findItem(R.id.search).getActionView();
        ComponentName cn = new ComponentName(this, MusicPlayerActivity.class);
        searchView.setSearchableInfo(
                searchManager.getSearchableInfo(cn));

        SearchSuggestion searchSuggestion = new SearchSuggestion(this, searchView);
        searchSuggestion.addSearchSuggestions();
        return true;
    }


}

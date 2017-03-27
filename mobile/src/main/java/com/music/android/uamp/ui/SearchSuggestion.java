package com.music.android.uamp.ui;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.widget.SearchView;
import android.util.LruCache;
import android.view.Menu;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.music.android.uamp.AnalyticsApplication;
import com.music.android.uamp.R;
import com.music.android.uamp.model.RemoteJSONSource;
import com.music.android.uamp.utils.MediaIDHelper;
import com.music.android.uamp.utils.NetworkHelper;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by sagar on 12/12/16.
 */

public class SearchSuggestion {

    private static LruCache<String, Integer> suggestionSelected = new LruCache<>(20);
    private static LruCache<String, List<String>> suggestionAutoText = new LruCache<>(100);
    //private final Tracker mTracker;

    private SearchView searchView;
    private Context context;
    private FirebaseAnalytics mFirebaseAnalytics;

    public SearchSuggestion(Context context, SearchView searchView) {
        this.searchView = searchView;
        this.context = context;
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(context);

        /*GoogleAnalytics analytics = GoogleAnalytics.getInstance(context);
        analytics.setLocalDispatchPeriod(30);*/
    }

    public static void addSelectedSuggestions(LruCache<String, Integer> suggestionSelected) {
        suggestionSelected = suggestionSelected;
    }

    public static LruCache<String, Integer> getSuggestionSelected() {
        return suggestionSelected;
    }

    public void addSearchSuggestions() {
        searchView.setMaxWidth(Integer.MAX_VALUE);

        AutoCompleteTextView searchTextView = (AutoCompleteTextView) searchView.findViewById(android.support.v7.appcompat.R.id.search_src_text);
        try {
            Field mCursorDrawableRes = TextView.class.getDeclaredField("mCursorDrawableRes");
            mCursorDrawableRes.setAccessible(true);
            mCursorDrawableRes.set(searchTextView, R.drawable.cursor); //This sets the cursor resource ID to 0 or @null which will make it visible on white background
        } catch (Exception e) {
        }

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {

            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(final String query) {

                if (query == null || query.isEmpty()) {
                    Map<String, Integer> list = suggestionSelected.snapshot();
                    List<String> items = new ArrayList<String>();
                    for (String suggestion : list.keySet()) {
                        items.add(suggestion);
                    }
                    loadSearchSuggestions(query, items);
                    return true;
                }
                if (query.length() < 1) {
                    return false;
                }
                List<String> items = suggestionAutoText.get(query);
                if (items != null) {
                    loadSearchSuggestions(query, items);
                    return true;
                }
                new AsyncTask<String, Void, JSONArray>() {
                    @Override
                    protected JSONArray doInBackground(String... params) {
                        String searchquery = query;
                        try {
                            searchquery = URLEncoder.encode(query, "utf-8");
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                        }
                        String url = "http://suggestqueries.google.com/complete/search?hl=en&ds=yt&client=youtube&hjson=t&cp=1&key=AIzaSyD3UusulV2oYNHYwKjPBrv0ZDXdZ3CX6Ys&format=5&alt=json&q=" + searchquery;
                        try {
                            JSONArray jsonArray = RemoteJSONSource.fetchJSONArrayFromUrl(url);
                            return jsonArray;
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        return null;
                    }

                    @Override
                    protected void onPostExecute(JSONArray jsonArray) {
                        try {
                            if (jsonArray == null) {
                                return;
                            }
                            JSONArray listArray = jsonArray.getJSONArray(1);
                            final List<String> items = new ArrayList<String>();
                            for (int i = 0; i < listArray.length(); i++) {
                                JSONArray data = listArray.getJSONArray(i);
                                items.add(data.get(0).toString());
                            }
                            suggestionAutoText.put(query, items);
                            loadSearchSuggestions(query, items);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }.execute();


                return true;

            }

        });

        searchView.setOnSearchClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Map<String, Integer> list = suggestionSelected.snapshot();
                List<String> items = new ArrayList<String>();
                for (String suggestion : list.keySet()) {
                    items.add(suggestion);
                }
                loadSearchSuggestions("", items);
            }
        });


        searchView.setOnSuggestionListener(new SearchView.OnSuggestionListener() {
            @Override
            public boolean onSuggestionSelect(int position) {
                return true;
            }

            @Override
            public boolean onSuggestionClick(int position) {
                String suggestion = getSuggestion(position);
                if (!NetworkHelper.isOnline(context)) {
                    searchView.setQuery(suggestion, true);
                    Toast.makeText(context, "Please Check internet connection.", Toast.LENGTH_LONG).show();
                    return true;
                }
                //Integer oldSuggestion = suggestionSelected.get(suggestion);
                suggestionSelected.put(suggestion, 1);
                /*if (oldSuggestion == null) {
                    suggestionSelected.put(suggestion, 1);
                } else {
                    suggestionSelected.put(suggestion, oldSuggestion + 1);
                }*/

                Bundle bundle = new Bundle();
                bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, suggestion);
                bundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, "search");
                bundle.putString(FirebaseAnalytics.Param.ITEM_CATEGORY,"Global search");
                bundle.putString(FirebaseAnalytics.Param.CONTENT, suggestion);
                mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.SEARCH, bundle);
                searchView.setQuery(suggestion, true);
                Intent intent = new Intent(context, MusicPlayerActivity.class);
                intent.putExtra(SearchManager.QUERY, suggestion);
                intent.setAction(Intent.ACTION_SEARCH);
                context.startActivity(intent);
                return true;
            }
        });


    }

    private String getSuggestion(int position) {
        Cursor cursor = (Cursor) searchView.getSuggestionsAdapter().getItem(position);
        return cursor.getString(cursor.getColumnIndex("text"));
    }

    private void loadSearchSuggestions(String query, List<String> items) {

        String[] columns = new String[]{"_id", "text"};
        Object[] temp = new Object[]{0, "default"};
        MatrixCursor cursor = new MatrixCursor(columns);
        for (int i = 0; i < items.size(); i++) {
            temp[0] = i;
            temp[1] = items.get(i);
            cursor.addRow(temp);
        }

        //final SearchView search = (SearchView) menu.findItem(R.id.search).getActionView();
        searchView.setSuggestionsAdapter(new SuggestionsAdapter(searchView, context, cursor, items));


    }
}

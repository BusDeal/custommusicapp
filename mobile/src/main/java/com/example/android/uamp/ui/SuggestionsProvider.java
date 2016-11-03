package com.example.android.uamp.ui;

import android.app.SearchManager;
import android.content.SearchRecentSuggestionsProvider;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

/**
 * Created by sagar on 25/10/16.
 */

public class SuggestionsProvider extends SearchRecentSuggestionsProvider {
    public static final String AUTHORITY =
            SuggestionsProvider.class.getName();

    public static final int MODE = DATABASE_MODE_QUERIES;

    public SuggestionsProvider() {
        setupSuggestions(AUTHORITY, MODE);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        String query = uri.getLastPathSegment();
        if (SearchManager.SUGGEST_URI_PATH_QUERY.equals(query)) {
            // user hasn’t entered anything
            // thus return a default cursor
            String[] columns = new String[] { "_id", "text" };
            Object[] temp = new Object[] { 0, "default" };
            MatrixCursor cursor = new MatrixCursor(columns);
            return cursor;
        }
        else {

            String[] columns = new String[] { "_id", "text" };
            Object[] temp = new Object[] { 0, "default" };
            MatrixCursor cursor = new MatrixCursor(columns);
            temp[0] = 1;
            temp[1] = "sagar";
            cursor.addRow(temp);
            return cursor;
            // query contains the users search
            // return a cursor with appropriate data
        }


    }
}

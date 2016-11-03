package com.example.android.uamp.ui;

import android.content.Context;
import android.database.Cursor;
import android.support.v4.widget.CursorAdapter;
import android.support.v7.widget.SearchView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.android.uamp.R;

import java.util.List;

/**
 * Created by sagar on 25/10/16.
 */

public class SuggestionsAdapter extends CursorAdapter {

    private List<String> items;

    private TextView text;

    private SearchView searchView;

    public SuggestionsAdapter(SearchView searchView, Context context, Cursor cursor, List<String> items) {

        super(context, cursor, false);
        this.items = items;
        this.searchView=searchView;
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {

        TextView text = (TextView) view.findViewById(R.id.item);
        text.setText(items.get(cursor.getPosition()));

    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {

        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        View view = inflater.inflate(R.layout.suggestion, parent, false);

        text = (TextView) view.findViewById(R.id.item);
        ImageView appendText = (ImageView) view.findViewById(R.id.assign);
        appendText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                searchView.setQuery(text.getText(),false);
            }
        });

        return view;

    }

}
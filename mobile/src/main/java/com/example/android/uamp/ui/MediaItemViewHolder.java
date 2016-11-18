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
package com.example.android.uamp.ui;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.android.uamp.AlbumArtCache;
import com.example.android.uamp.R;

import java.io.File;

public class MediaItemViewHolder {

    static final int STATE_INVALID = -1;
    static final int STATE_NONE = 0;
    static final int STATE_PLAYABLE = 1;
    static final int STATE_PAUSED = 2;
    static final int STATE_PLAYING = 3;

    private static ColorStateList sColorStatePlaying;
    private static ColorStateList sColorStateNotPlaying;

    ImageView alubmImageview;
    ImageView mImageView;
    TextView mTitleView;
    TextView mDescriptionView;

    static View setupView(Activity activity, View convertView, ViewGroup parent,
                          MediaDescriptionCompat description, int state) {

        if (sColorStateNotPlaying == null || sColorStatePlaying == null) {
            initializeColorStateLists(activity);
        }

        MediaItemViewHolder holder;

        Integer cachedState = STATE_INVALID;

        if (convertView == null) {
            convertView = LayoutInflater.from(activity)
                    .inflate(R.layout.media_list_item, parent, false);
            holder = new MediaItemViewHolder();
            holder.mImageView = (ImageView) convertView.findViewById(R.id.play_eq);
            holder.alubmImageview = (ImageView) convertView.findViewById(R.id.alubm_uri);
            holder.mTitleView = (TextView) convertView.findViewById(R.id.title);
            holder.mDescriptionView = (TextView) convertView.findViewById(R.id.description);
            convertView.setTag(holder);
        } else {
            holder = (MediaItemViewHolder) convertView.getTag();
            cachedState = (Integer) convertView.getTag(R.id.tag_mediaitem_state_cache);
        }

        holder.mTitleView.setText(description.getTitle());
        holder.mDescriptionView.setText(description.getSubtitle());
        fetchImageAsync(holder,description);

        // If the state of convertView is different, we need to adapt the view to the
        // new state.
        if (cachedState == null || cachedState != state) {
            switch (state) {
                case STATE_PLAYABLE:
                    Drawable pauseDrawable = ContextCompat.getDrawable(activity,
                            R.drawable.ic_play_arrow_black_36dp);
                    DrawableCompat.setTintList(pauseDrawable, sColorStateNotPlaying);
                    holder.mImageView.setImageDrawable(pauseDrawable);
                    holder.mImageView.setVisibility(View.INVISIBLE);
                    break;
                case STATE_PLAYING:
                    AnimationDrawable animation = (AnimationDrawable)
                            ContextCompat.getDrawable(activity, R.drawable.ic_equalizer_white_36dp);
                    DrawableCompat.setTintList(animation, sColorStatePlaying);
                    holder.mImageView.setImageDrawable(animation);
                    holder.mImageView.setVisibility(View.VISIBLE);
                    animation.start();
                    break;
                case STATE_PAUSED:
                    Drawable playDrawable = ContextCompat.getDrawable(activity,
                            R.drawable.ic_equalizer1_white_36dp);
                    DrawableCompat.setTintList(playDrawable, sColorStatePlaying);
                    holder.mImageView.setImageDrawable(playDrawable);
                    holder.mImageView.setVisibility(View.VISIBLE);
                    break;
                default:
                    holder.mImageView.setVisibility(View.GONE);
            }
            convertView.setTag(R.id.tag_mediaitem_state_cache, state);
        }

        return convertView;
    }

    private static void fetchImageAsync(final MediaItemViewHolder holder, @NonNull final MediaDescriptionCompat description) {
        if (description.getIconUri() == null) {
            return;
        }
        String artUrl = description.getIconUri().toString();
        AlbumArtCache cache = AlbumArtCache.getInstance();
        Bitmap art = cache.getIconImage(artUrl);
        if (art == null) {
            art = description.getIconBitmap();
        }
        if(artUrl.startsWith("/")){
            File imgFile = new File(artUrl);

            if(imgFile.exists()) {

                art = BitmapFactory.decodeFile(imgFile.getAbsolutePath());
                art=Bitmap.createScaledBitmap(art, 200, 120, false);
            }
        }
        if (art != null) {
            // if we have the art cached or from the MediaDescription, use it:
            holder.alubmImageview.setImageBitmap(art);
        } else {
            // otherwise, fetch a high res version and update:
            cache.fetch(artUrl, new AlbumArtCache.FetchListener() {
                @Override
                public void onFetched(String artUrl, Bitmap bitmap, Bitmap icon) {
                    // sanity check, in case a new fetch request has been done while
                    // the previous hasn't yet returned:
                    if (artUrl.equals(description.getIconUri().toString())) {
                        holder.alubmImageview.setImageBitmap(bitmap);
                    }
                }
            });
        }
    }

    static private void initializeColorStateLists(Context ctx) {
        sColorStateNotPlaying = ColorStateList.valueOf(ctx.getResources().getColor(
                R.color.media_item_icon_not_playing));
        sColorStatePlaying = ColorStateList.valueOf(ctx.getResources().getColor(
                R.color.media_item_icon_playing));
    }
}

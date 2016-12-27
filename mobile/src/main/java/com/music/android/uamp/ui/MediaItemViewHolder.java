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

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.music.android.uamp.AlbumArtCache;
import com.music.android.uamp.R;
import com.music.android.uamp.model.MusicProviderSource;
import com.music.android.uamp.utils.MediaIDHelper;
import com.pnikosis.materialishprogress.ProgressWheel;

import java.io.File;

import static com.music.android.uamp.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_DOWNLOAD;
import static com.music.android.uamp.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_VIDEOID;

public class MediaItemViewHolder {

    static final int STATE_INVALID = -1;
    static final int STATE_NONE = 0;
    static final int STATE_PLAYABLE = 1;
    static final int STATE_PAUSED = 2;
    static final int STATE_PLAYING = 3;

    private static ColorStateList sColorStatePlaying;
    private static ColorStateList sColorStateNotPlaying;

    private static int imageWidth;
    private static int imageHeight;

    ImageView alubmImageview;
    ImageView mImageView;
    TextView mTitleView;
    TextView mDescriptionView;
    TextView duration;
    ImageView download;
    private ProgressWheel mProgressWeel;

    static View setupView(Activity activity, View convertView, ViewGroup parent,
                          MediaDescriptionCompat description, int state, MediaBrowserCompat mediaBrowser) {

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
            holder.duration = (TextView) convertView.findViewById(R.id.duration);
            holder.download = (ImageView) convertView.findViewById(R.id.download);
            holder.mProgressWeel = (ProgressWheel)convertView.findViewById(R.id.progress_wheel);
            if(description.getExtras().getString(MusicProviderSource.CUSTOM_METADATA_DOWNLOADED) != null){
                holder.download.setVisibility(View.GONE);
            }

            convertView.setTag(holder);
        } else {
            holder = (MediaItemViewHolder) convertView.getTag();
            cachedState = (Integer) convertView.getTag(R.id.tag_mediaitem_state_cache);
        }
        downloadListner(holder.download,holder.mProgressWeel,description.getMediaId(),mediaBrowser);
        Long duration=description.getExtras().getLong(MediaMetadataCompat.METADATA_KEY_DURATION);
        String time=DateUtils.formatElapsedTime(duration / 1000);
        holder.duration.setText(time+"");
        holder.mTitleView.setText(description.getTitle());
        holder.mDescriptionView.setText(description.getSubtitle());
        imageWidth= (int)(convertView.getResources().getDimension(R.dimen.imageWidth)/convertView.getResources().getDisplayMetrics().density);
        imageHeight= (int)(convertView.getResources().getDimension(R.dimen.imageHeight)/convertView.getResources().getDisplayMetrics().density);
        fetchImageAsync(convertView,holder,description);
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

    private static void downloadListner(final ImageView mDownLoad, final ProgressWheel progressWheel,final String mediaId,final MediaBrowserCompat mMediaBrowser){
        mDownLoad.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //mDownLoad.setImageResource(R.drawable.ic_downloader);
                mDownLoad.setClickable(false);
                mDownLoad.setVisibility(View.GONE);
                progressWheel.setVisibility(View.VISIBLE);
                String downloadMediaId = MediaIDHelper.createMediaID(MediaIDHelper.extractMusicIDFromMediaID(mediaId), MEDIA_ID_MUSICS_BY_DOWNLOAD, MediaIDHelper.extractBrowseCategoryValueFromMediaID(MEDIA_ID_MUSICS_BY_VIDEOID));
                mMediaBrowser.getItem(downloadMediaId, new MediaBrowserCompat.ItemCallback() {
                    /**
                     * Called when the item has been returned by the browser service.
                     *
                     * @param item The item that was returned or null if it doesn't exist.
                     */
                    @Override
                    public void onItemLoaded(MediaBrowserCompat.MediaItem item) {
                        mDownLoad.setImageResource(R.drawable.ic_download);
                    }

                    /**
                     * Called when the item doesn't exist or there was an error retrieving it.
                     *
                     * @param itemId The media id of the media item which could not be loaded.
                     */
                    @Override
                    public void onError(@NonNull String message) {
                        //Toast.makeText(MediaItemViewHolder.this., "Unable to download music", Toast.LENGTH_LONG);
                        mDownLoad.setImageResource(R.drawable.ic_download);
                    }
                });
                final Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        progressWheel.setVisibility(View.GONE);
                    }
                }, 15*1000);
            }
        });
    }

    private static void fetchImageAsync(final View view, final MediaItemViewHolder holder, @NonNull final MediaDescriptionCompat description) {
        if (description.getIconUri() == null) {
            return;
        }

        String artUrl = description.getIconUri().toString();
        AlbumArtCache cache = AlbumArtCache.getInstance();
        Bitmap art = cache.getBigImage(artUrl);
        if (art == null) {
            art = description.getIconBitmap();
        }
        if(artUrl.startsWith("/")){
            File imgFile = new File(artUrl);

            if(imgFile.exists()) {
                art = BitmapFactory.decodeFile(imgFile.getAbsolutePath());
            }
        }
        if (art != null) {
            //art=Bitmap.createScaledBitmap(art, imageWidth, imageHeight, false);
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
                        //bitmap=Bitmap.createScaledBitmap(bitmap, imageWidth, imageHeight, false);
                        holder.alubmImageview.setImageBitmap(bitmap);
                    }
                }

                @Override
                public void onError(String artUrl, Exception e){
                    Bitmap bm = BitmapFactory.decodeResource(view.getResources(), R.drawable.ic_launcher);
                    bm=Bitmap.createScaledBitmap(bm, imageWidth, imageHeight, false);
                    holder.alubmImageview.setImageBitmap(bm);
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

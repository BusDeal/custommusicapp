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
import android.app.DownloadManager;
import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentActivity;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.baoyz.swipemenulistview.SwipeMenu;
import com.baoyz.swipemenulistview.SwipeMenuCreator;
import com.baoyz.swipemenulistview.SwipeMenuItem;
import com.baoyz.swipemenulistview.SwipeMenuListView;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;
import com.loveplusplus.update.UpdateChecker;
import com.music.android.uamp.AnalyticsApplication;
import com.music.android.uamp.R;
import com.music.android.uamp.model.MusicProviderSource;
import com.music.android.uamp.playback.PlaybackManager;
import com.music.android.uamp.utils.Constants;
import com.music.android.uamp.utils.LogHelper;
import com.music.android.uamp.utils.MediaIDHelper;
import com.music.android.uamp.utils.NetworkHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static android.content.Context.MODE_PRIVATE;
import static com.music.android.uamp.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_DOWNLOAD;
import static com.music.android.uamp.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_DOWNLOAD_VIDEOID;
import static com.music.android.uamp.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_FAVOURITE;
import static com.music.android.uamp.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_FAVOURITE_VIDEOID;
import static com.music.android.uamp.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_VIDEOID;

/**
 * A Fragment that lists all the various browsable queues available
 * from a {@link android.service.media.MediaBrowserService}.
 * <p/>
 * It uses a {@link MediaBrowserCompat} to connect to the {@link com.music.android.uamp.MusicService}.
 * Once connected, the fragment subscribes to get all the children.
 * All {@link MediaBrowserCompat.MediaItem}'s that can be browsed are shown in a ListView.
 */
public class MediaBrowserFragment extends Fragment {

    private static final String TAG = LogHelper.makeLogTag(MediaBrowserFragment.class);

    private static final String ARG_MEDIA_ID = "media_id";

    private BrowseAdapter mBrowserAdapter;
    private String mMediaId;
    private MediaFragmentListener mMediaFragmentListener;
    private View mErrorView;
    private TextView mErrorMessage;
    private Tracker mTracker;
    private List<MediaSessionCompat.QueueItem> queueItems;
    private com.baoyz.swipemenulistview.SwipeMenuListView listView;

    private ProgressDialog progress;
    private final BroadcastReceiver mConnectivityChangeReceiver = new BroadcastReceiver() {
        private boolean oldOnline = false;

        @Override
        public void onReceive(Context context, Intent intent) {
            // We don't care about network changes while this fragment is not associated
            // with a media ID (for example, while it is being initialized )
            if (mMediaId != null) {
                boolean isOnline = NetworkHelper.isOnline(context);
                if (isOnline != oldOnline) {
                    oldOnline = isOnline;
                    checkForUserVisibleErrors(false);
                    if (isOnline) {
                        mBrowserAdapter.notifyDataSetChanged();
                    }
                }
            }
        }
    };

    // Receive callbacks from the MediaController. Here we update our state such as which queue
    // is being shown, the current title and description and the PlaybackState.
    private final MediaControllerCompat.Callback mMediaControllerCallback =
            new MediaControllerCompat.Callback() {
                @Override
                public void onMetadataChanged(MediaMetadataCompat metadata) {
                    super.onMetadataChanged(metadata);
                    if (metadata == null) {
                        return;
                    }
                    LogHelper.d(TAG, "Received metadata change to media ",
                            metadata.getDescription().getMediaId());
                    mBrowserAdapter.notifyDataSetChanged();
                }

                @Override
                public void onPlaybackStateChanged(@NonNull PlaybackStateCompat state) {
                    super.onPlaybackStateChanged(state);
                    LogHelper.d(TAG, "Received state change: ", state);
                    checkForUserVisibleErrors(false);
                    mBrowserAdapter.notifyDataSetChanged();
                }

                @Override
                public void onQueueChanged(List<MediaSessionCompat.QueueItem> queue) {
                    queueItems = queue;
                }
            };

    private final MediaBrowserCompat.SubscriptionCallback mSubscriptionCallback =
            new MediaBrowserCompat.SubscriptionCallback() {
                @Override
                public void onChildrenLoaded(@NonNull String parentId,
                                             @NonNull List<MediaBrowserCompat.MediaItem> children) {
                    try {
                        progress.dismiss();
                        LogHelper.d(TAG, "fragment onChildrenLoaded, parentId=" + parentId +
                                "  count=" + children.size());
                        checkForUserVisibleErrors(children.isEmpty());
                        if (!children.isEmpty()) {
                            mErrorView.setVisibility(View.GONE);
                        }
                        listView.setVisibility(View.VISIBLE);
                        mBrowserAdapter.clear();
                        for (MediaBrowserCompat.MediaItem item : children) {
                            mBrowserAdapter.add(item);
                        }
                        mBrowserAdapter.notifyDataSetChanged();
                        SharedPreferences sharedPreferences = getActivity().getSharedPreferences("versions", MODE_PRIVATE);
                        Long time = sharedPreferences.getLong("versionCheckTime", 0L);
                        Long milliseconds=(System.currentTimeMillis()-time);
                        if( time == 0L || (int) ((milliseconds / (1000*60*60)) % 24) > 2) {
                            UpdateChecker.checkForDialog(getActivity());
                        }
                    } catch (Throwable t) {
                        LogHelper.e(TAG, "Error on childrenloaded", t);
                    }
                }

                @Override
                public void onError(@NonNull String id) {
                    mBrowserAdapter.clear();
                    mBrowserAdapter.notifyDataSetChanged();
                    progress.dismiss();
                    LogHelper.e(TAG, "browse fragment subscription onError, id=" + id);
                    Toast.makeText(getActivity(), R.string.error_loading_media, Toast.LENGTH_LONG).show();
                    checkForUserVisibleErrors(true);
                }
            };


    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        // If used on an activity that doesn't implement MediaFragmentListener, it
        // will throw an exception as expected:
        mMediaFragmentListener = (MediaFragmentListener) activity;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        LogHelper.d(TAG, "fragment.onCreateView");
        View rootView = inflater.inflate(R.layout.fragment_list, container, false);

        mErrorView = rootView.findViewById(R.id.playback_error);
        mErrorMessage = (TextView) mErrorView.findViewById(R.id.error_message);

        mBrowserAdapter = new BrowseAdapter(getActivity());

        progress = new ProgressDialog(getActivity());
        //progress.setTitle("Loading");
        progress.setMessage("Loading Music...");
        progress.setCancelable(false); // disable dismiss by tapping outside of the dialog
        progress.show();
        AnalyticsApplication application = (AnalyticsApplication) getActivity().getApplication();
        mTracker = application.getDefaultTracker();
        listView = (com.baoyz.swipemenulistview.SwipeMenuListView) rootView.findViewById(R.id.list_view);
        listView.setAdapter(mBrowserAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                checkForUserVisibleErrors(false);
                MediaBrowserCompat.MediaItem item = mBrowserAdapter.getItem(position);
                mMediaFragmentListener.onMediaItemSelected(item);
            }
        });


        listView.setOnMenuItemClickListener(new SwipeMenuListView.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(int position, SwipeMenu menu, int index) {
                switch (index) {
                    case 0:
                        Boolean isFavourite = menu.getViewType() == 1 ? true : false;
                        addAsFavourite(mBrowserAdapter.getItem(position), isFavourite);
                        break;
                    case 1:
                        MediaBrowserCompat.MediaItem mediaItem = mBrowserAdapter.getItem(position);
                        if(mediaItem == null){
                            return false;
                        }
                        String srcUrl = mediaItem.getDescription().getExtras().getString(Constants.CUSTOM_METADATA_TRACK_SOURCE);
                        if(srcUrl == null){
                            return false;
                        }
                        File file = new File(srcUrl);
                        if (srcUrl.startsWith("/") && file.exists()) {
                            boolean deleted = file.delete();
                            if(mediaItem.getDescription().getIconUri() == null){
                                return true;
                            }
                            String artUrl = mediaItem.getDescription().getIconUri().toString();
                            file = new File(artUrl);
                            if (deleted && artUrl != null && artUrl.startsWith("/") && file.exists()) {
                                deleted = file.delete();
                            }
                            //DownloadManager.removeMedia();
                        }
                        mBrowserAdapter.remove(mediaItem);
                        // delete
                        break;
                    case 2:
                       /* mediaItem = mBrowserAdapter.getItem(position);
                        MediaControllerCompat controller = ((FragmentActivity) getActivity())
                                .getSupportMediaController();
                        if (controller != null) {
                            Bundle bundle = new Bundle();
                            bundle.putString("mediaId", mediaItem.getMediaId());
                            controller.getTransportControls().sendCustomAction(PlaybackManager.CUSTOM_ACTION_ADD_TO_QUEUE, bundle);
                        }
                        break;*/
                }
                // false : close the menu; true : not close the menu
                return false;
            }
        });

        listView.setSwipeDirection(SwipeMenuListView.DIRECTION_LEFT);
        listView.setMenuCreator(getItemMenuCreator());
        listView.setOnSwipeListener(new SwipeMenuListView.OnSwipeListener() {

            @Override
            public void onSwipeStart(int position) {
                MediaBrowserCompat.MediaItem mediaItem = mBrowserAdapter.getItem(position);
            }

            @Override
            public void onSwipeEnd(int position) {
                // swipe end
            }
        });
        return rootView;
    }


    @Override
    public void onPause(){
        super.onPause();
    }

    private void addAsFavourite(MediaBrowserCompat.MediaItem mediaItem, Boolean isFavourite) {
        String favouriteMediaId = MediaIDHelper.createMediaID(MediaIDHelper.extractMusicIDFromMediaID(mediaItem.getMediaId()), MEDIA_ID_MUSICS_BY_FAVOURITE_VIDEOID, (!isFavourite) + "");
        mMediaFragmentListener.getMediaBrowser().getItem(favouriteMediaId, new MediaBrowserCompat.ItemCallback() {
            /**
             * Called when the item has been returned by the browser service.
             *
             * @param item The item that was returned or null if it doesn't exist.
             */
            @Override
            public void onItemLoaded(MediaBrowserCompat.MediaItem item) {
                onConnected();
            }

            /**
             * Called when the item doesn't exist or there was an error retrieving it.
             *
             * @param itemId The media id of the media item which could not be loaded.
             */
            @Override
            public void onError(@NonNull String message) {
            }
        });
    }

    protected boolean shouldShowAddPlayList() {
        MediaControllerCompat mediaController = ((FragmentActivity) getActivity())
                .getSupportMediaController();
        if (mediaController == null ||
                mediaController.getMetadata() == null ||
                mediaController.getPlaybackState() == null) {
            return false;
        }
        switch (mediaController.getPlaybackState().getState()) {
            case PlaybackStateCompat.STATE_ERROR:
            case PlaybackStateCompat.STATE_NONE:
            case PlaybackStateCompat.STATE_STOPPED:
                return false;
            default:
                return true;
        }
    }

    private SwipeMenuCreator getItemMenuCreator() {
        SwipeMenuCreator creator = new SwipeMenuCreator() {

            @Override
            public void create(SwipeMenu menu) {


                // create "open" item
                SwipeMenuItem openItem = new SwipeMenuItem(getActivity().
                        getApplicationContext());
                // set item background
                openItem.setBackground(new ColorDrawable(Color.rgb(0xC9, 0xC9,
                        0xCE)));
                // set item width
                openItem.setWidth(250);
                // set a icon
                openItem.setIcon(R.drawable.ic_favorite_border);
                openItem.setTitle(R.string.add_favorite);
                if (menu.getViewType() == 1) {
                    openItem.setIcon(R.drawable.ic_favorite);
                    openItem.setTitle(R.string.remove_favorite);
                }
                // add to menu
                menu.addMenuItem(openItem);

                String mediaId = getMediaId();
                if (mediaId != null && MediaIDHelper.extractBrowseCategoryTypeFromMediaID(mediaId).equalsIgnoreCase(MEDIA_ID_MUSICS_BY_DOWNLOAD)) {
                    SwipeMenuItem deleteItem = new SwipeMenuItem(
                            getActivity().getApplicationContext());
                    // set item background
                    deleteItem.setBackground(new ColorDrawable(Color.rgb(0xF9,
                            0x3F, 0x25)));
                    // set item width
                    deleteItem.setWidth(250);
                    // set a icon
                    deleteItem.setTitle(R.string.delete_item);
                    deleteItem.setIcon(R.drawable.ic_delete);
                    // add to menu
                    menu.addMenuItem(deleteItem);
                }

               /* if (shouldShowAddPlayList()) {
                    SwipeMenuItem addPlayItem = new SwipeMenuItem(
                            getActivity().getApplicationContext());
                    // set item background
                    addPlayItem.setBackground(new ColorDrawable(Color.rgb(0xF9,
                            0x3F, 0x25)));
                    // set item width
                    addPlayItem.setWidth(90);
                    // set a icon
                    addPlayItem.setIcon(R.drawable.ic_playlist_add_black_48dp);
                    // add to menu
                    menu.addMenuItem(addPlayItem);
                }*/


            }
        };
        return creator;
    }

    @Override
    public void onStart() {
        super.onStart();

        // fetch browsing information to fill the listview:
        MediaBrowserCompat mediaBrowser = mMediaFragmentListener.getMediaBrowser();

        LogHelper.d(TAG, "fragment.onStart, mediaId=", mMediaId,
                "  onConnected=" + mediaBrowser.isConnected());

        if (mediaBrowser.isConnected()) {
            onConnected();
        }

        // Registers BroadcastReceiver to track network connection changes.
        this.getActivity().registerReceiver(mConnectivityChangeReceiver,
                new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    @Override
    public void onStop() {
        super.onStop();
        MediaBrowserCompat mediaBrowser = mMediaFragmentListener.getMediaBrowser();
        if (mediaBrowser != null && mediaBrowser.isConnected() && mMediaId != null) {
            mediaBrowser.unsubscribe(mMediaId);
        }
        MediaControllerCompat controller = ((FragmentActivity) getActivity())
                .getSupportMediaController();
        if (controller != null) {
            controller.unregisterCallback(mMediaControllerCallback);
        }
        this.getActivity().unregisterReceiver(mConnectivityChangeReceiver);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        if(progress != null){
            progress.dismiss();
        }
        mMediaFragmentListener = null;
    }

    public String getMediaId() {
        Bundle args = getArguments();
        if (args != null) {
            return args.getString(ARG_MEDIA_ID);
        }
        return null;
    }

    public void setMediaId(String mediaId) {
        Bundle args = new Bundle(1);
        args.putString(MediaBrowserFragment.ARG_MEDIA_ID, mediaId);
        setArguments(args);
    }

    // Called when the MediaBrowser is connected. This method is either called by the
    // fragment.onStart() or explicitly by the activity in the case where the connection
    // completes after the onStart()
    public void onConnected() {
        if (isDetached()) {
            return;
        }
        mMediaId = getMediaId();
        if (mMediaId == null) {
            mMediaId = mMediaFragmentListener.getMediaBrowser().getRoot();
        }
        updateTitle();

        // Unsubscribing before subscribing is required if this mediaId already has a subscriber
        // on this MediaBrowser instance. Subscribing to an already subscribed mediaId will replace
        // the callback, but won't trigger the initial callback.onChildrenLoaded.
        //
        // This is temporary: A bug is being fixed that will make subscribe
        // consistently call onChildrenLoaded initially, no matter if it is replacing an existing
        // subscriber or not. Currently this only happens if the mediaID has no previous
        // subscriber or if the media content changes on the service side, so we need to
        // unsubscribe first.
        mMediaFragmentListener.getMediaBrowser().unsubscribe(mMediaId);

        mMediaFragmentListener.getMediaBrowser().subscribe(mMediaId, mSubscriptionCallback);

        // Add MediaController callback so we can redraw the list when metadata changes:
        MediaControllerCompat controller = ((FragmentActivity) getActivity())
                .getSupportMediaController();
        if (controller != null) {
            controller.registerCallback(mMediaControllerCallback);
        }
    }

    private void checkForUserVisibleErrors(boolean forceError) {

        boolean showError = forceError;
        if (mMediaId != null && (MediaIDHelper.extractBrowseCategoryTypeFromMediaID(mMediaId).equalsIgnoreCase(MEDIA_ID_MUSICS_BY_DOWNLOAD) || MediaIDHelper.extractBrowseCategoryTypeFromMediaID(mMediaId).equalsIgnoreCase(MEDIA_ID_MUSICS_BY_DOWNLOAD_VIDEOID))) {
            mErrorMessage.setText("No downloads, Please download songs from All music.");
        } else if (mMediaId != null && (MediaIDHelper.extractBrowseCategoryTypeFromMediaID(mMediaId).equalsIgnoreCase(MEDIA_ID_MUSICS_BY_FAVOURITE) || MediaIDHelper.extractBrowseCategoryTypeFromMediaID(mMediaId).equalsIgnoreCase(MEDIA_ID_MUSICS_BY_FAVOURITE_VIDEOID))) {
            mErrorMessage.setText("No Favourites, Please add favourites to this list.");
        }
        // If offline, message is about the lack of connectivity:
        else if (!NetworkHelper.isOnline(getActivity())) {
            mErrorMessage.setText(R.string.error_no_connection);
            showError = true;
        } else {
            // otherwise, if state is ERROR and metadata!=null, use playback state error message:
            MediaControllerCompat controller = ((FragmentActivity) getActivity())
                    .getSupportMediaController();
            if (controller != null
                    && controller.getMetadata() != null
                    && controller.getPlaybackState() != null
                    && controller.getPlaybackState().getState() == PlaybackStateCompat.STATE_ERROR
                    && controller.getPlaybackState().getErrorMessage() != null) {
                mErrorMessage.setText(controller.getPlaybackState().getErrorMessage());
                mTracker.send(new HitBuilders.ExceptionBuilder()
                        .setDescription(controller.getPlaybackState().getErrorMessage().toString())
                        .build());
                showError = false;
            } else if (forceError) {
                // Finally, if the caller requested to show error, show a generic message:
                mErrorMessage.setText(R.string.error_loading_media);
                showError = true;
            }
        }
        if(showError){
            listView.setVisibility(View.GONE);
        }
        mErrorView.setVisibility(showError ? View.VISIBLE : View.GONE);
        LogHelper.d(TAG, "checkForUserVisibleErrors. forceError=", forceError,
                " showError=", showError,
                " isOnline=", NetworkHelper.isOnline(getActivity()));
    }

    private void updateTitle() {
        if (MediaIDHelper.MEDIA_ID_ROOT.equals(mMediaId)) {
            mMediaFragmentListener.setToolbarTitle(null);
            return;
        }

        MediaBrowserCompat mediaBrowser = mMediaFragmentListener.getMediaBrowser();
        mediaBrowser.getItem(mMediaId, new MediaBrowserCompat.ItemCallback() {
            @Override
            public void onItemLoaded(MediaBrowserCompat.MediaItem item) {
                mMediaFragmentListener.setToolbarTitle(
                        item.getDescription().getTitle());
            }
        });
    }

    // An adapter for showing the list of browsed MediaItem's
    private class BrowseAdapter extends ArrayAdapter<MediaBrowserCompat.MediaItem> {

        public BrowseAdapter(Activity context) {
            super(context, R.layout.media_list_item, new ArrayList<MediaBrowserCompat.MediaItem>());
        }

        @Override
        public int getViewTypeCount() {
            // menu type count
            return 3;
        }

        @Override
        public int getItemViewType(int position) {
            // current menu type

            MediaBrowserCompat.MediaItem item = getItem(position);
            if (item.getDescription().getExtras().getString(Constants.CUSTOM_METADATA_FAVOURITE) != null) {
                return 1;
            }
            if (item.getDescription().getExtras().getString(Constants.CUSTOM_METADATA_DOWNLOADED) != null) {
                return 2;
            }

            return 0;
        }


        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            MediaBrowserCompat.MediaItem item = getItem(position);
            int itemState = MediaItemViewHolder.STATE_NONE;
            if (item.isPlayable()) {
                itemState = MediaItemViewHolder.STATE_PLAYABLE;
                MediaControllerCompat controller = ((FragmentActivity) getContext())
                        .getSupportMediaController();
                if (controller != null && controller.getMetadata() != null) {
                    String currentPlaying = controller.getMetadata().getDescription().getMediaId();
                    String musicId = MediaIDHelper.extractMusicIDFromMediaID(
                            item.getDescription().getMediaId());
                    if (currentPlaying != null && currentPlaying.equals(musicId)) {
                        PlaybackStateCompat pbState = controller.getPlaybackState();
                        if (pbState == null ||
                                pbState.getState() == PlaybackStateCompat.STATE_ERROR) {
                            itemState = MediaItemViewHolder.STATE_NONE;
                        } else if (pbState.getState() == PlaybackStateCompat.STATE_PLAYING) {
                            itemState = MediaItemViewHolder.STATE_PLAYING;
                        } else {
                            itemState = MediaItemViewHolder.STATE_PAUSED;
                        }
                    }
                }
            }
            return MediaItemViewHolder.setupView((Activity) getContext(), convertView, parent,
                    item.getDescription(), itemState, mMediaFragmentListener.getMediaBrowser());
        }
    }



    public interface MediaFragmentListener extends MediaBrowserProvider {
        void onMediaItemSelected(MediaBrowserCompat.MediaItem item);

        void setToolbarTitle(CharSequence title);
    }

}

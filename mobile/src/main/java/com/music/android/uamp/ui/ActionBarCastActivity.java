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
import android.app.ActivityOptions;
import android.app.FragmentManager;
import android.app.SearchManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.MediaRouteButton;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.music.android.uamp.AlbumArtCache;
import com.music.android.uamp.R;
import com.music.android.uamp.utils.BitmapHelper;
import com.music.android.uamp.utils.LogHelper;
import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastState;
import com.google.android.gms.cast.framework.CastStateListener;
import com.google.android.gms.cast.framework.IntroductoryOverlay;

import static com.music.android.uamp.ui.tv.TvBrowseActivity.SAVED_MEDIA_ID;
import static com.music.android.uamp.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_DOWNLOAD;
import static com.music.android.uamp.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_FAVOURITE;
import static com.music.android.uamp.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_HISTORY;
import static com.music.android.uamp.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_LOCAL;
import static com.music.android.uamp.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_SEARCH;

/**
 * Abstract activity with toolbar, navigation drawer and cast support. Needs to be extended by
 * any activity that wants to be shown as a top level activity.
 *
 * The requirements for a subclass is to call {@link #initializeToolbar()} on onCreate, after
 * setContentView() is called and have three mandatory layout elements:
 * a {@link android.support.v7.widget.Toolbar} with id 'toolbar',
 * a {@link android.support.v4.widget.DrawerLayout} with id 'drawerLayout' and
 * a {@link android.widget.ListView} with id 'drawerList'.
 */
public abstract class ActionBarCastActivity extends AppCompatActivity {

    private static final String TAG = LogHelper.makeLogTag(ActionBarCastActivity.class);

    private static final int DELAY_MILLIS = 1000;
    private static final int MY_SIGNIN_ACTIVITY = 1;
    private static int MY_ACTIVE_ACTIVITY = 1;
    public static String searchQuery;

    private CastContext mCastContext;
    private MenuItem mMediaRouteMenuItem;
    private Toolbar mToolbar;
    private ActionBarDrawerToggle mDrawerToggle;
    private DrawerLayout mDrawerLayout;

    private boolean mToolbarInitialized;

    private int mItemToOpenWhenDrawerCloses = -1;

    private CastStateListener mCastStateListener = new CastStateListener() {
        @Override
        public void onCastStateChanged(int newState) {
            if (newState != CastState.NO_DEVICES_AVAILABLE) {
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (mMediaRouteMenuItem != null && mMediaRouteMenuItem.isVisible()) {
                            LogHelper.d(TAG, "Cast Icon is visible");
                            showFtu();
                        }
                    }
                }, DELAY_MILLIS);
            }
        }
    };

    private final DrawerLayout.DrawerListener mDrawerListener = new DrawerLayout.DrawerListener() {
        @Override
        public void onDrawerClosed(View drawerView) {
            if (mDrawerToggle != null) mDrawerToggle.onDrawerClosed(drawerView);
            if (mItemToOpenWhenDrawerCloses >= 0) {
                Bundle extras = ActivityOptions.makeCustomAnimation(
                    ActionBarCastActivity.this, R.anim.fade_in, R.anim.fade_out).toBundle();

                Class activityClass = null;
                Intent intent=null;
                switch (mItemToOpenWhenDrawerCloses) {
                    case R.id.navigation_allmusic:
                        activityClass = MusicPlayerActivity.class;
                        intent=new Intent(ActionBarCastActivity.this, activityClass);
                        if(searchQuery != null){
                            intent.setAction(Intent.ACTION_SEARCH);
                            intent.putExtra(SearchManager.QUERY,searchQuery);
                        }
                        MY_ACTIVE_ACTIVITY=1;
                        break;
                    case R.id.navigation_downloads:
                        activityClass = MusicPlayerActivity.class;
                        intent=new Intent(ActionBarCastActivity.this, activityClass);
                        intent.putExtra(SAVED_MEDIA_ID,MEDIA_ID_MUSICS_BY_DOWNLOAD);
                        MY_ACTIVE_ACTIVITY=2;
                        break;
                    case R.id.navigation_favorites:
                        activityClass = MusicPlayerActivity.class;
                        intent=new Intent(ActionBarCastActivity.this, activityClass);
                        intent.putExtra(SAVED_MEDIA_ID,MEDIA_ID_MUSICS_BY_FAVOURITE);
                        MY_ACTIVE_ACTIVITY=3;
                        break;
                    case R.id.navigation_history:
                        activityClass = MusicPlayerActivity.class;
                        intent=new Intent(ActionBarCastActivity.this, activityClass);
                        intent.putExtra(SAVED_MEDIA_ID,MEDIA_ID_MUSICS_BY_HISTORY);
                        MY_ACTIVE_ACTIVITY=4;
                        break;
                    case R.id.navigation_local:
                        activityClass = MusicPlayerActivity.class;
                        intent=new Intent(ActionBarCastActivity.this, activityClass);
                        intent.putExtra(SAVED_MEDIA_ID,MEDIA_ID_MUSICS_BY_LOCAL);
                        MY_ACTIVE_ACTIVITY=5;
                        break;
                    case R.id.navigation_feedback:
                        Intent Email = new Intent(Intent.ACTION_SEND);
                        Email.setType("text/email");
                        Email   .putExtra(Intent.EXTRA_EMAIL, new String[] { "vidyasagarkota@gmail.com" });
                        Email.putExtra(Intent.EXTRA_SUBJECT, "Music App Feedback");
                        Email.putExtra(Intent.EXTRA_TEXT, "Hi," + "");
                        startActivity(Intent.createChooser(Email, "Send Feedback:"));
                        return;
                }
                if (activityClass != null) {
                    startActivity(intent, extras);
                    finish();
                }
            }
        }

        @Override
        public void onDrawerStateChanged(int newState) {
            if (mDrawerToggle != null) mDrawerToggle.onDrawerStateChanged(newState);
        }

        @Override
        public void onDrawerSlide(View drawerView, float slideOffset) {
            if (mDrawerToggle != null) mDrawerToggle.onDrawerSlide(drawerView, slideOffset);
        }

        @Override
        public void onDrawerOpened(View drawerView) {
            if (mDrawerToggle != null) mDrawerToggle.onDrawerOpened(drawerView);
            if (getSupportActionBar() != null) getSupportActionBar()
                    .setTitle(R.string.app_name);
        }
    };

    private final FragmentManager.OnBackStackChangedListener mBackStackChangedListener =
        new FragmentManager.OnBackStackChangedListener() {
            @Override
            public void onBackStackChanged() {
                updateDrawerToggle();
            }
        };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LogHelper.d(TAG, "Activity onCreate");

        mCastContext = CastContext.getSharedInstance(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!mToolbarInitialized) {
            throw new IllegalStateException("You must run super.initializeToolbar at " +
                "the end of your onCreate method");
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        if (mDrawerToggle != null) {
            mDrawerToggle.syncState();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mCastContext.addCastStateListener(mCastStateListener);

        // Whenever the fragment back stack changes, we may need to update the
        // action bar toggle: only top level screens show the hamburger-like icon, inner
        // screens - either Activities or fragments - show the "Up" icon instead.
        getFragmentManager().addOnBackStackChangedListener(mBackStackChangedListener);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mDrawerToggle != null) {
            mDrawerToggle.onConfigurationChanged(newConfig);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mCastContext.removeCastStateListener(mCastStateListener);
        getFragmentManager().removeOnBackStackChangedListener(mBackStackChangedListener);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.main, menu);
        mMediaRouteMenuItem = CastButtonFactory.setUpMediaRouteButton(getApplicationContext(),
                menu, R.id.media_route_menu_item);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (mDrawerToggle != null && mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        // If not handled by drawerToggle, home needs to be handled by returning to previous
        if (item != null && item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        // If the drawer is open, back will close it
        if (mDrawerLayout != null && mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            mDrawerLayout.closeDrawers();
            return;
        }
        // Otherwise, it may return to the previous fragment stack
        FragmentManager fragmentManager = getFragmentManager();
        if (fragmentManager.getBackStackEntryCount() > 0) {
            fragmentManager.popBackStack();
        } else {
            // Lastly, it will rely on the system behavior for back
            super.onBackPressed();
        }
    }

    @Override
    public void setTitle(CharSequence title) {
        super.setTitle(title);
        mToolbar.setTitle(title);
    }

    @Override
    public void setTitle(int titleId) {
        super.setTitle(titleId);
        mToolbar.setTitle(titleId);
    }

    protected void initializeToolbar() {
        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        if (mToolbar == null) {
            throw new IllegalStateException("Layout is required to include a Toolbar with id " +
                "'toolbar'");
        }
        mToolbar.inflateMenu(R.menu.main);

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (mDrawerLayout != null) {
            NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
            if (navigationView == null) {
                throw new IllegalStateException("Layout requires a NavigationView " +
                        "with id 'nav_view'");
            }

            // Create an ActionBarDrawerToggle that will handle opening/closing of the drawer:
            mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout,
                mToolbar, R.string.open_content_drawer, R.string.close_content_drawer);
            mDrawerLayout.setDrawerListener(mDrawerListener);
            populateDrawerItems(navigationView);
            setSupportActionBar(mToolbar);
            updateDrawerToggle();
        } else {
            setSupportActionBar(mToolbar);
        }

        mToolbarInitialized = true;
    }

    private void populateDrawerItems(NavigationView navigationView) {
        navigationView.setNavigationItemSelectedListener(
                new NavigationView.OnNavigationItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(MenuItem menuItem) {
                        menuItem.setChecked(true);
                        mItemToOpenWhenDrawerCloses = menuItem.getItemId();
                        mDrawerLayout.closeDrawers();
                        return true;
                    }
                });
        switch (MY_ACTIVE_ACTIVITY){
            case 1:
                navigationView.setCheckedItem(R.id.navigation_allmusic);
                break;
            case 2:
                navigationView.setCheckedItem(R.id.navigation_downloads);
                break;
            case 3:
                navigationView.setCheckedItem(R.id.navigation_favorites);
                break;
            case 4:
                navigationView.setCheckedItem(R.id.navigation_history);
                break;
            case 5:
                navigationView.setCheckedItem(R.id.navigation_local);
                break;
            case 6:
                navigationView.setCheckedItem(R.id.navigation_feedback);
        }
        SharedPreferences sharedPreferences = this.getSharedPreferences("GOOGLE_ACCOUNT", MODE_PRIVATE);
        String displayName=sharedPreferences.getString("displayName",null);
        String photoUrl=sharedPreferences.getString("photoUrl",null);
        final ImageView signIn=(ImageView)navigationView.getHeaderView(0).findViewById(R.id.sign_in);
        if(signIn != null) {
            signIn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent(ActionBarCastActivity.this, SignInActivity.class);
                    startActivityForResult(intent,MY_SIGNIN_ACTIVITY);
                }
            });
        }
        if(displayName != null ) {
            TextView displayText = (TextView) navigationView.getHeaderView(0).findViewById(R.id.display);
            displayText.setText(displayName);
        }
        if(photoUrl != null){
            AlbumArtCache.getInstance().fetch(photoUrl, new AlbumArtCache.FetchListener() {
                @Override
                public void onFetched(String artUrl, Bitmap bigImage, Bitmap iconImage) {
                    signIn.setImageBitmap(iconImage);
                }
            });
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch(requestCode) {
            case (MY_SIGNIN_ACTIVITY) : {
                if (resultCode == Activity.RESULT_OK) {
                    NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
                    if (navigationView == null) {
                        throw new IllegalStateException("Layout requires a NavigationView " +
                                "with id 'nav_view'");
                    }
                    populateDrawerItems(navigationView);
                }
                break;
            }
        }
    }

    protected void updateDrawerToggle() {
        if (mDrawerToggle == null) {
            return;
        }
        boolean isRoot = getFragmentManager().getBackStackEntryCount() == 0;
        mDrawerToggle.setDrawerIndicatorEnabled(isRoot);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowHomeEnabled(!isRoot);
            getSupportActionBar().setDisplayHomeAsUpEnabled(!isRoot);
            getSupportActionBar().setHomeButtonEnabled(!isRoot);
        }
        if (isRoot) {
            mDrawerToggle.syncState();
        }
    }

    /**
     * Shows the Cast First Time User experience to the user (an overlay that explains what is
     * the Cast icon)
     */
    private void showFtu() {
        Menu menu = mToolbar.getMenu();
        View view = menu.findItem(R.id.media_route_menu_item).getActionView();
        if (view != null && view instanceof MediaRouteButton) {
            IntroductoryOverlay overlay = new IntroductoryOverlay.Builder(this, mMediaRouteMenuItem)
                    .setTitleText(R.string.touch_to_cast)
                    .setSingleTime()
                    .build();
            overlay.show();
        }
    }

    public String getSearchQuery() {
        return searchQuery;
    }

}

package com.music.android.uamp.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.Handler;
import android.widget.ImageView;

import com.music.android.uamp.R;

public class Splash extends Activity {

    private final int SPLASH_DISPLAY_LENGHT = 2500;

    /** Called when the activity is first created. */
    @Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		
		// try to get value
		SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
		int currentSplash = sharedPref.getInt(
				getString(R.string.current_splash), 0);
		int nextIndex = currentSplash + 1;
		/*TypedArray imgs = getResources().obtainTypedArray(R.array.random_imgs);
		if (nextIndex == imgs.length())
			nextIndex = 0;
		SharedPreferences.Editor editor = sharedPref.edit();
		editor.putInt(getString(R.string.current_splash), nextIndex);
		editor.commit();*/
		setContentView(R.layout.splashscreen);
		//ImageView ivSplash = (ImageView) findViewById(R.id.ivSplash);
		// or set you ImageView's resource to the id
		try {
			//ivSplash.setImageResource(ivSplash);
			//imgs.recycle();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		/*
		 * New Handler to start the Menu-Activity and close this Splash-Screen
		 * after some seconds.
		 */
		new Handler().postDelayed(new Runnable() {
			@Override
			public void run() {
				/* Create an Intent that will start the Menu-Activity. */
				Intent mainIntent = new Intent(Splash.this, MusicPlayerActivity.class);
				Splash.this.startActivity(mainIntent);
				Splash.this.finish();
			}
		}, SPLASH_DISPLAY_LENGHT);
	}

    
 }
/*
 * Copyright Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.music.android.uamp;

import android.app.Application;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.util.Log;

import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.Tracker;
import com.music.android.uamp.model.MusicProvider;
import com.music.android.uamp.model.RemoteJSONSource;
import com.parse.Parse;
import com.parse.ParseException;
import com.parse.ParseInstallation;
import com.parse.ParsePush;
import com.parse.SaveCallback;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/**
 * This is a subclass of {@link Application} used to provide shared objects for this app, such as
 * the {@link Tracker}.
 */
public class AnalyticsApplication extends Application {
    private Tracker mTracker;

    /**
     * Gets the default {@link Tracker} for this {@link Application}.
     *
     * @return tracker
     */
    synchronized public Tracker getDefaultTracker() {
        if (mTracker == null) {

            GoogleAnalytics  analytics = GoogleAnalytics.getInstance(this);
            analytics.setLocalDispatchPeriod(10*60);
            mTracker = analytics.newTracker("UA-88784216-1"); // Replace with actual tracker id
            mTracker.enableExceptionReporting(true);
            //mTracker.enableAdvertisingIdCollection(true);
            mTracker.enableAutoActivityTracking(true);
            // To enable debug logging use: adb shell setprop log.tag.GAv4 DEBUG
            //mTracker = analytics.newTracker(R.xml.global_tracker);
        }
        return mTracker;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        PackageInfo pInfo = null;
        try {
            pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            final int verCode = pInfo.versionCode;
            final String version = pInfo.versionName;
            new AsyncTask<Void, Void, JSONObject>() {

                @Override
                protected JSONObject doInBackground(Void... voids) {
                    try {
                        JSONObject jsonObject = RemoteJSONSource.fetchJSONFromUrl("http://kmusic.in:8080/versions/?buildVersion=" + version);
                        return jsonObject;
                    } catch (JSONException e) {
                        e.printStackTrace();
                    } catch (Exception e) {

                    }
                    return null;
                }

                @Override
                protected void onPostExecute(JSONObject jsonObject) {
                    if (jsonObject != null) {
                        SharedPreferences.Editor editor = getApplicationContext().getSharedPreferences("versions", MODE_PRIVATE).edit();
                        try {
                            editor.putBoolean("isForceUpdateRequired", jsonObject.getBoolean("isForceUpdateRequired"));
                            editor.putBoolean("isPartialUpdateRequired", jsonObject.getBoolean("isPartialUpdateRequired"));
                            editor.commit();
                        } catch (JSONException e) {
                            e.printStackTrace();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            };//.execute();
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        } catch (Exception e) {

        }
    }
}
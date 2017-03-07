package com.loveplusplus.update;

class Constants {


    // json {"url":"http://192.168.205.33:8080/Hello/app_v3.0.1_Other_20150116.apk","versionCode":2,"updateMessage":"版本更新信息"}

    static final String APK_DOWNLOAD_URL = "url";
    static final String APK_UPDATE_CONTENT = "updateMessage";
    static final String APK_VERSION_CODE = "versionCode";
    static final String APK_VERSION_CODE_STRING="versionCodeString";
    static final String APK_FORCE_UPDATE="APK_FORCE_UPDATE";

    static final int TYPE_NOTIFICATION = 2;

    static final int TYPE_VERIFICATION = 3;

    static final int TYPE_DIALOG = 1;

    static final String TAG = "UpdateChecker";

    static final String UPDATE_URL = "http://kmusic.in/version/update.json";
}

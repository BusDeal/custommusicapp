package com.music.android.uamp.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by sagar on 16/12/16.
 */

public class YoutubeMetaDataList {

    private List<YoutubeMetaData> urls=new ArrayList<>();

    private List<Long> durations;

    public List<YoutubeMetaData> getUrls() {
        return urls;
    }

    public void setUrls(List<YoutubeMetaData> urls) {
        this.urls = urls;
    }

    public List<Long> getDurations() {
        return durations;
    }

    public void setDurations(List<Long> durations) {
        this.durations = durations;
    }
}

package com.music.android.uamp.model;

import java.util.List;

/**
 * Created by sagar on 29/12/16.
 */

public class AudioMetaData {
    private String url;

    private List<Long> durations;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public List<Long> getDurations() {
        return durations;
    }

    public void setDurations(List<Long> durations) {
        this.durations = durations;
    }
}

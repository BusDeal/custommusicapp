package com.music.android.uamp.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by sagar on 16/12/16.
 */

public class YoutubeMetaDataList {

    private List<YoutubeMetaData> youtubeMetaDataList=new ArrayList<>();

    public List<YoutubeMetaData> getYoutubeMetaDataList() {
        return youtubeMetaDataList;
    }

    public void setYoutubeMetaDataList(List<YoutubeMetaData> youtubeMetaDataList) {
        this.youtubeMetaDataList = youtubeMetaDataList;
    }
}

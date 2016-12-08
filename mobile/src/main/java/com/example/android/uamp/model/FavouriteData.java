package com.example.android.uamp.model;

import android.support.v4.media.MediaMetadataCompat;

import java.util.Map;

/**
 * Created by sagar on 7/12/16.
 */

public class FavouriteData {

    private String MusicId;
    private Map<String,String> metadata;

    private String METADATA_KEY_MEDIA_ID;
    private String METADATA_KEY_DISPLAY_DESCRIPTION;
    private String METADATA_KEY_ALBUM;
    private String METADATA_KEY_ARTIST;
    private String METADATA_KEY_GENRE;
    private String METADATA_KEY_DISPLAY_SUBTITLE;
    private String METADATA_KEY_DISPLAY_ICON_URI;
    private String METADATA_KEY_ART_URI;
    private String METADATA_KEY_ALBUM_ART_URI;
    private String METADATA_KEY_TITLE;


    public String getMETADATA_KEY_MEDIA_ID() {
        return METADATA_KEY_MEDIA_ID;
    }

    public void setMETADATA_KEY_MEDIA_ID(String METADATA_KEY_MEDIA_ID) {
        this.METADATA_KEY_MEDIA_ID = METADATA_KEY_MEDIA_ID;
    }

    public String getMETADATA_KEY_DISPLAY_DESCRIPTION() {
        return METADATA_KEY_DISPLAY_DESCRIPTION;
    }

    public void setMETADATA_KEY_DISPLAY_DESCRIPTION(String METADATA_KEY_DISPLAY_DESCRIPTION) {
        this.METADATA_KEY_DISPLAY_DESCRIPTION = METADATA_KEY_DISPLAY_DESCRIPTION;
    }

    public String getMETADATA_KEY_ALBUM() {
        return METADATA_KEY_ALBUM;
    }

    public void setMETADATA_KEY_ALBUM(String METADATA_KEY_ALBUM) {
        this.METADATA_KEY_ALBUM = METADATA_KEY_ALBUM;
    }

    public String getMETADATA_KEY_ARTIST() {
        return METADATA_KEY_ARTIST;
    }

    public void setMETADATA_KEY_ARTIST(String METADATA_KEY_ARTIST) {
        this.METADATA_KEY_ARTIST = METADATA_KEY_ARTIST;
    }

    public String getMETADATA_KEY_GENRE() {
        return METADATA_KEY_GENRE;
    }

    public void setMETADATA_KEY_GENRE(String METADATA_KEY_GENRE) {
        this.METADATA_KEY_GENRE = METADATA_KEY_GENRE;
    }

    public String getMETADATA_KEY_DISPLAY_SUBTITLE() {
        return METADATA_KEY_DISPLAY_SUBTITLE;
    }

    public void setMETADATA_KEY_DISPLAY_SUBTITLE(String METADATA_KEY_DISPLAY_SUBTITLE) {
        this.METADATA_KEY_DISPLAY_SUBTITLE = METADATA_KEY_DISPLAY_SUBTITLE;
    }

    public String getMETADATA_KEY_DISPLAY_ICON_URI() {
        return METADATA_KEY_DISPLAY_ICON_URI;
    }

    public void setMETADATA_KEY_DISPLAY_ICON_URI(String METADATA_KEY_DISPLAY_ICON_URI) {
        this.METADATA_KEY_DISPLAY_ICON_URI = METADATA_KEY_DISPLAY_ICON_URI;
    }

    public String getMETADATA_KEY_ART_URI() {
        return METADATA_KEY_ART_URI;
    }

    public void setMETADATA_KEY_ART_URI(String METADATA_KEY_ART_URI) {
        this.METADATA_KEY_ART_URI = METADATA_KEY_ART_URI;
    }

    public String getMETADATA_KEY_ALBUM_ART_URI() {
        return METADATA_KEY_ALBUM_ART_URI;
    }

    public void setMETADATA_KEY_ALBUM_ART_URI(String METADATA_KEY_ALBUM_ART_URI) {
        this.METADATA_KEY_ALBUM_ART_URI = METADATA_KEY_ALBUM_ART_URI;
    }

    public String getMETADATA_KEY_TITLE() {
        return METADATA_KEY_TITLE;
    }

    public void setMETADATA_KEY_TITLE(String METADATA_KEY_TITLE) {
        this.METADATA_KEY_TITLE = METADATA_KEY_TITLE;
    }

    public String getMusicId() {
        return MusicId;
    }

    public void setMusicId(String musicId) {
        MusicId = musicId;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }
}

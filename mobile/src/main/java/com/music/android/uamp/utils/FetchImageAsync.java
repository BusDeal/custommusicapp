package com.music.android.uamp.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.support.annotation.NonNull;
import android.support.v4.media.MediaDescriptionCompat;
import android.view.View;
import android.widget.ImageView;

import com.music.android.uamp.AlbumArtCache;
import com.music.android.uamp.R;
import com.music.android.uamp.ui.MediaItemViewHolder;

import java.io.File;

/**
 * Created by sagar on 26/7/17.
 */

public class FetchImageAsync {

    public enum ImageSize{
        BigImage,IconImage;
    }



    public static void fetchImageAsync(final View view, final ImageView holder, @NonNull final MediaDescriptionCompat description, final ImageSize imageSize) {
        if (description.getIconUri() == null && description.getIconBitmap() == null ) {
            return;
        }
        Bitmap art=null;

        AlbumArtCache cache = AlbumArtCache.getInstance();
        String artUrl=null;
        if(description.getIconUri() != null) {
            artUrl = description.getIconUri().toString();
            art = cache.getBigImage(artUrl);
            if(artUrl.startsWith("/")){
                File imgFile = new File(artUrl);
                if(imgFile.exists()) {
                    try {
                        art = BitmapFactory.decodeFile(imgFile.getAbsolutePath());
                        if(art == null){
                            MediaMetadataRetriever metaRetriver = new MediaMetadataRetriever();
                            metaRetriver.setDataSource(artUrl);
                            try {
                                byte artByte[] = metaRetriver.getEmbeddedPicture();
                                if(artByte != null) {
                                    art = BitmapFactory.decodeByteArray(artByte, 0, artByte.length);
                                    art = BitmapHelper.scaleBitmap(art,
                                            800, 480);
                                }

                            }catch(Exception e1){
                                e1.printStackTrace();
                            }
                        }
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                }
            }
        }

       /* if(description.getExtras() != null && description.getExtras().getString(Constants.CUSTOM_METADATA_LOCAL) != null && description.getExtras().getString(Constants.CUSTOM_METADATA_TRACK_SOURCE) != null){
            MediaMetadataRetriever metaRetriver = new MediaMetadataRetriever();
            metaRetriver.setDataSource(description.getExtras().getString(Constants.CUSTOM_METADATA_TRACK_SOURCE));
            try {
                byte artByte[] = metaRetriver.getEmbeddedPicture();
                if(artByte != null) {
                    art = BitmapFactory.decodeByteArray(artByte, 0, artByte.length);
                    art = BitmapHelper.scaleBitmap(art,
                            800, 480);
                    //art=Bitmap.createScaledBitmap(art, imageWidth, imageHeight, false);
                }

            }catch(Exception e){
                e.printStackTrace();
            }
        }*/
        if (art == null) {
            art = description.getIconBitmap();
        }
        if (art != null) {
            holder.setImageBitmap(art);
        } else if(artUrl != null) {
            // otherwise, fetch a high res version and update:
            cache.fetch(artUrl, new AlbumArtCache.FetchListener() {
                @Override
                public void onFetched(String artUrl, Bitmap bitmap, Bitmap icon) {
                    // sanity check, in case a new fetch request has been done while
                    // the previous hasn't yet returned:
                    if (artUrl.equals(description.getIconUri().toString())) {
                        holder.setImageBitmap(icon);
                        if(imageSize == ImageSize.BigImage){
                            holder.setImageBitmap(bitmap);
                        }
                    }
                }

                @Override
                public void onError(String artUrl, Exception e){
                    Bitmap bm = BitmapFactory.decodeResource(view.getResources(), R.drawable.ic_launcher);
                    //bm=Bitmap.createScaledBitmap(bm, imageWidth, imageHeight, false);
                    holder.setImageBitmap(bm);
                }
            });
        }else{
            Bitmap bm = BitmapFactory.decodeResource(view.getResources(), R.drawable.ic_launcher);
            //bm=Bitmap.createScaledBitmap(bm, imageWidth, imageHeight, false);
            holder.setImageBitmap(bm);
        }
    }
}

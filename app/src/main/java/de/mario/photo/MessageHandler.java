package de.mario.photo;

import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.io.File;
import java.util.Map;

import de.mario.photo.exif.ExifTag;
import de.mario.photo.exif.ExifWriter;
import de.mario.photo.exif.GeoTagFactory;

/**
 */
class MessageHandler extends Handler {

    private final PhotoActivity activity;

    MessageHandler(PhotoActivity activity) {
        super(Looper.getMainLooper());
        this.activity = activity;
    }

    @Override
    public void handleMessage(Message message) {
        Bundle bundle = message.getData();
        if(bundle.isEmpty()) {
            String msg = message.obj.toString();
            activity.toast(msg);
        }else{
            String[] pictures = bundle.getStringArray(
                    PhotoActivable.PICTURES);
            String folder = bundle.getString(PhotoActivable.SAVE_FOLDER);
            updateExif(pictures);
            activity.processHdr(pictures);

            activity.prepareForNextShot();
            informAboutPictures(pictures, folder);
            Log.d(PhotoActivable.DEBUG_TAG, "ready for next photo session");
        }
    }

    private void informAboutPictures(String[] pictures, String folder) {
        int len = pictures.length;
        activity.toast(String.format(activity.getString(R.string.photos_saved), len, folder));
    }

    private void updateExif(String [] pictures){
        Location location = activity.getCurrentLocation();
        Log.d(PhotoActivable.DEBUG_TAG, "location: " + location);
        if(location != null) {
            GeoTagFactory tagFactory = new GeoTagFactory();
            Map<ExifTag, String> tags = tagFactory.create(location);
            ExifWriter writer = new ExifWriter();
            for (String name : pictures) {
                File file = new File(name);
                writer.addTags(file, tags);
            }
        }
    }

}
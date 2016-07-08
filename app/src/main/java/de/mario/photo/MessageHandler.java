package de.mario.photo;

import android.content.Intent;
import android.location.Location;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import java.io.File;
import java.util.Map;

import de.mario.photo.exif.ExifTag;
import de.mario.photo.exif.ExifWriter;
import de.mario.photo.exif.GeoTagFactory;
import roboguice.util.Ln;

/**
 * This class handles incoming messages from the sub parts.
 */
class MessageHandler extends Handler {

    private static final int PHOTOS_SAVED = R.string.photos_saved;
    private final PhotoActivity activity;

    MessageHandler(PhotoActivity activity) {
        super(Looper.getMainLooper());
        this.activity = activity;
    }

    @Override
    public void handleMessage(Message message) {
       handleMessage(new MessageWrapper(message));
    }

    void handleMessage(MessageWrapper wrapper){
        if(wrapper.isDataEmpty()) {
            String msg = wrapper.getParcelAsString();
            activity.toast(msg);
        }else{
            handleMessageAsPictureInfo(wrapper);
        }
    }

    private void handleMessageAsPictureInfo(MessageWrapper wrapper){
        String[] pictures = wrapper.getStringArray(
                PhotoActivable.PICTURES);

        String folder = wrapper.getString(PhotoActivable.SAVE_FOLDER);
        updateExif(pictures);

        activity.processHdr(pictures);

        activity.prepareForNextShot();
        informAboutPictures(pictures, folder);

        Ln.d("ready for next photo session");
    }

    private void informAboutPictures(String[] pictures, String folder) {
        int len = pictures.length;
        activity.toast(String.format(activity.getString(PHOTOS_SAVED), len, folder));

        for(String pic : pictures) {
            refreshPictureFolder(pic);
        }
    }

    private void refreshPictureFolder(String path){
        File file = new File(path);
        Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file));
        activity.sendBroadcast(intent);
    }

    private void updateExif(String [] pictures){
        Location location = activity.getCurrentLocation();
        Ln.d("location: %s", location);
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

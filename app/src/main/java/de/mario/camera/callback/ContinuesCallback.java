package de.mario.camera.callback;

import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.location.Location;
import android.os.Message;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import de.mario.camera.PhotoActivable;
import de.mario.camera.R;
import de.mario.camera.exif.ExifTag;
import de.mario.camera.exif.ExifWriter;
import de.mario.camera.exif.GeoTagFactory;


/**
 * This class takes pictures for each given exposure values and saves them.
 *
 * @author Mario
 */
class ContinuesCallback implements PictureCallback {

    private static final String PATTERN = "yyyy-MM-dd_HH:mm:ss";

    private final InternalMemoryAccessor memAccessor;
    private final Queue<Integer> exposureValues;
    private final int defaultExposure;
    private final PhotoActivable activity;
    private final Date date;

    private File pictureFileDir;
    private int imageCounter;
    private List<String> imagesNames = new ArrayList<>();

    ContinuesCallback(PhotoActivable activity) {
        this.activity = activity;
        this.exposureValues = activity.getExposureValues();
        this.defaultExposure = exposureValues.poll(); //remove the first value and keep it for later
        this.memAccessor = new InternalMemoryAccessor(activity.getInternalDirectory());
        this.pictureFileDir = activity.getPicturesDirectory(); //image path on external storage
        this.date = new Date();
    }

    private String getResource(int key) {
        return activity.getResource(key);
    }

    private void toast(final String message) {
        MessageSender sender = new MessageSender(activity);
        sender.toast(message);
    }

    @Override
    public void onPictureTaken(byte[] data, Camera camera) {
        saveInternal(data);
        if (exposureValues.isEmpty()) {
            moveExternal();
        }

        imageCounter++;
        nextPhoto(camera);
    }

    private void saveInternal(byte[] data) {
        String name = createFileName();
        try {
            memAccessor.save(data, name);
        } catch (IOException e) {
            Log.e(PhotoActivable.DEBUG_TAG,
                    "File " + name + " not saved: " + e.getMessage());
            toast(getResource(R.string.save_error));
        }
    }

    private void moveExternal() {

        try {
            String path = pictureFileDir.getAbsolutePath();
            imagesNames.addAll(memAccessor.moveAll(path));
            updateExif();
            sendFinishedInfo();
        } catch (IOException exc) {
            Log.e(PhotoActivable.DEBUG_TAG, exc.getMessage());
            toast(getResource(R.string.save_error));
        }
    }

    private void updateExif(){
        Location location = activity.getCurrentLocation();
        Log.d(PhotoActivable.DEBUG_TAG, "location: " + location);
        if(location != null) {
            GeoTagFactory tagFactory = new GeoTagFactory();
            Map<ExifTag, String> tags = tagFactory.create(location);
            ExifWriter writer = new ExifWriter();
            for (String name : imagesNames) {
                File file = new File(imagesNames.get(0));
                writer.addTags(file, tags);
            }
        }
    }

    private void sendFinishedInfo() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Message msg = Message.obtain();
                msg.getData().putStringArray(PhotoActivable.PICTURES, imagesNames.toArray(new String[imagesNames.size()]));
                activity.getHandler().sendMessage(msg);
            }
        }).start();
    }

    private String createFileName() {
        DateFormat dateFormat = new SimpleDateFormat(PATTERN);
        return String.format("IMG_%s_%s.jpg", dateFormat.format(date), imageCounter);
    }

    private void nextPhoto(Camera camera) {

        activity.getPreview().setEnabled(true);
        camera.startPreview();

        if (!exposureValues.isEmpty()) {
            int ev = exposureValues.poll();
            setExposureCompensation(camera, ev);
            camera.takePicture(new ShutterCallback(), new LoggingPictureCallback(), this);
        } else {
            //reset
            setExposureCompensation(camera, defaultExposure);
        }
    }

    private void setExposureCompensation(Camera camera, int ev) {
        Camera.Parameters params = camera.getParameters();
        params.setExposureCompensation(ev);
        camera.setParameters(params);
    }
}

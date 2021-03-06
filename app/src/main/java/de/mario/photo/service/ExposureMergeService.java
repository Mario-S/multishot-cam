package de.mario.photo.service;

import android.app.IntentService;
import android.content.Intent;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Imgcodecs;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import de.mario.photo.R;
import de.mario.photo.exif.ExifWriter;
import de.mario.photo.glue.PhotoActivable;
import de.mario.photo.settings.SettingsAccess;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 * helper methods.
 */
public class ExposureMergeService extends OpenCvService {

    private Merger merger;

    private ExifWriter exifWriter;

    private SettingsAccess settingsAccess;

    public ExposureMergeService() {
        this(new MertensMerger());
    }

    public ExposureMergeService(Merger merger) {
        super("ExposureMergeService");
        this.exifWriter = new ExifWriter();
        this.settingsAccess = new SettingsAccess(this);
        this.merger = merger;
    }


    @Override
    protected void onHandleIntent(Intent intent) {
        handleProcessing(intent.getStringArrayExtra(PARAM_PICS));
    }

    /**
     * Handle action Foo in the provided background thread with the provided
     * parameters.
     */
    private void handleProcessing(String [] pictures) {

       List<Mat> images = loadImages(pictures);

        Mat fusion = merger.merge(images);

        String firstPic = pictures[0];
        File out = new File(createFileName(firstPic));
        write(fusion, out);
        copyExif(firstPic, out);

        sendNotification(out);
    }

    private void copyExif(String src, File target) {
        File srcFile = new File(src);
        exifWriter.copy(srcFile, target);
    }

    private void sendNotification(File file) {
        //message for the app
        Intent intent = new Intent(PhotoActivable.EXPOSURE_MERGE);
        String path = file.getAbsolutePath();
        intent.putExtra(PhotoActivable.MERGED, path);
        sendBroadcast(intent);

        //general notification
        if(settingsAccess.isEnabled(R.string.notifyHdr)) {
            NotificationSender sender = new NotificationSender(this);
            sender.send(path);
        }
    }

    private String createFileName(String src) {
        int pos = src.lastIndexOf(".") ;
        String prefix = src.substring(0, pos - 1);
        String suffix = src.substring(pos);
        return prefix + PhotoActivable.MERGED + suffix;
    }

    void write(Mat fusion, File out) {
        Mat result = multiply(fusion);
        Imgcodecs.imwrite(out.getPath(), result);
    }

    private Mat multiply(Mat fusion) {
        Mat result = new Mat();
        Scalar scalar = new Scalar(255, 255, 255);
        Core.multiply(fusion, scalar, result);
        return result;
    }

    private List<Mat> loadImages(String[] pics) {
        List<Mat> imgs = new ArrayList<>();

        for(String pic : pics){
            File f = new File(pic);
            imgs.add(read(f.getPath()));
        }

        return imgs;
    }

    Mat read(String path) {
        return Imgcodecs.imread(path);
    }

}

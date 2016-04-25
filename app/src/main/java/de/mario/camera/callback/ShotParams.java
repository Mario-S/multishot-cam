package de.mario.camera.callback;

import android.content.Context;
import android.os.Handler;
import android.view.View;

import java.io.File;

import de.mario.camera.PhotoActivable;

/**
 */
final class ShotParams {

    private Shot[] shots;

    private final PhotoActivable activity;


    ShotParams(PhotoActivable activity) {
        this.activity = activity;
    }

    void setShots(Shot[] shots) {
        this.shots = shots;
    }

    String [] getNames(){
        String[] names = new String[shots.length];
        for (int i = 0; i < names.length; i++){
            names[i] = shots[i].getName();
        }
        return names;
    }

    int [] getExposures() {
        int[] exposures = new int[shots.length];
        for (int i = 0; i < exposures.length; i++){
            exposures[i] = shots[i].getExposure();
        }
        return exposures;
    }

    Context getContext() {
        return (Context) activity;
    }

    /**
     * Return the image path on external storage.
     * @return path as string
     */
    File getPictureFileDir() {
        return activity.getPicturesDirectory();
    }

    File getInternalDirectory() {
        return activity.getInternalDirectory();
    }

    Handler getHandler(){
        return activity.getHandler();
    }

    String getResource(int key) {
        return activity.getResource(key);
    }

    View getPreview() {
        return activity.getPreview();
    }
}
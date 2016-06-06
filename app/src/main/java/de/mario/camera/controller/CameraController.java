package de.mario.camera.controller;

import android.hardware.Camera;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import de.mario.camera.PhotoActivable;
import de.mario.camera.controller.lookup.CameraLookup;
import de.mario.camera.controller.preview.FocusView;
import de.mario.camera.controller.preview.Preview;
import de.mario.camera.controller.support.IsoSupport;
import de.mario.camera.controller.support.PicturesSizeSupport;

/**
 */
public class CameraController implements CameraControlable{
    private static final int MIN = 0;

    private PhotoActivable activity;
    private int camId = CameraLookup.NO_CAM_ID;
    private boolean canDisableShutterSound;

    private Camera camera;
    private Preview preview;
    private FocusView focusView;

    private IsoSupport isoSupport;
    private PicturesSizeSupport sizeSupport;
    private ScheduledExecutorService executor;
    private OrientationListener orientationListener;
    private CameraLookup cameraLookup;
    private CameraFactory cameraFactory;

    public CameraController(PhotoActivable activity) {
        this.activity = activity;
        executor = new ScheduledThreadPoolExecutor(1);
        cameraLookup = new CameraLookup();
        cameraFactory = new CameraFactory();
    }

    /**
     * Look for a camera and return true if we got one.
     * @return
     */
    @Override
    public boolean lookupCamera() {
        camId = cameraLookup.findBackCamera();
        if (camId != CameraLookup.NO_CAM_ID) {
            canDisableShutterSound = cameraLookup.canDisableShutterSound(camId);
            return true;
        }
        return false;
    }

    /**
     * Initialize the camera. Make sure that you called lookup before and got a true value.
     */
    @Override
    public void initialize() {
        camera = cameraFactory.getCamera(camId);
        sizeSupport = new PicturesSizeSupport(camera);
        isoSupport = new IsoSupport(camera);

        orientationListener = new OrientationListener(activity.getContext());
        if (orientationListener.canDetectOrientation()) {
            orientationListener.setCamera(camera);
            orientationListener.enable();
        }

        preview = new Preview(activity.getContext(), camera);
        focusView = new FocusView(activity.getContext());
    }


    @Override
    public void reinitialize(){
        if(camera == null) {
            initialize();
        }
    }

    @Override
    public void releaseCamera() {
        preview = null;
        orientationListener.disable();
        if (camera != null) {
            camera.stopPreview();
            camera.setPreviewCallback(null);
            camera.release();
            camera = null;
        }
    }

    @Override
    public void shot(final int delay) {
        camera.autoFocus(new Camera.AutoFocusCallback() {
            @Override
            public void onAutoFocus(boolean success, Camera camera) {
                focusView.focused(success);
                if (success) {
                    Runnable command = new PhotoCommand(activity, camera);

                    if (delay > MIN) {
                        executor.schedule(command, delay, TimeUnit.SECONDS);
                    } else {
                        executor.execute(command);
                    }
                } else {
                    prepareNextShot();
                }
            }
        });
    }

    private void prepareNextShot() {
        focusView.resetFocus();
        activity.prepareForNextShot();
    }

    @Override
    public void enableShutterSound(boolean enable) {
        if(canDisableShutterSound) {
            camera.enableShutterSound(enable);
        }
    }

    @Override
    public Preview getPreview() {
        return preview;
    }

    @Override
    public FocusView getFocusView() {
        return focusView;
    }

    @Override
    public String[] getIsoValues() {
        return isoSupport.getIsoValues();
    }

    @Override
    public String getSelectedIsoValue(String isoKey) {
        return isoSupport.getSelectedIsoValue(isoKey);
    }

    @Override
    public String[] getSupportedPicturesSizes(){
        return sizeSupport.getSupportedPicturesSizes();
    }

    @Override
    public String getSelectedPictureSize() {
        return sizeSupport.getSelectedPictureSize(camera);
    }

    @Override
    public String findIsoKey(){
        return isoSupport.findIsoKey();
    }
}

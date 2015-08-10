package de.mario.camera;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.os.Bundle;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;

import java.io.File;
import java.util.LinkedList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import de.mario.camera.service.ExposureMergeService;
import de.mario.camera.service.ProcessHdrService;

import static android.os.Environment.DIRECTORY_DCIM;
import static android.os.Environment.getExternalStoragePublicDirectory;
import static java.lang.Integer.parseInt;

/**
 * Main activity.
 * 
 * @author Mario
 * 
 */
public class PhotoActivity extends Activity implements PhotoActivable{

	private static final int NO_CAM_ID = -1;
	public static final int MIN = 0;
	private Camera camera;
	private Preview preview;
	private ProgressBar progressBar;
	private final LinkedList<Integer> exposureValues;
	private Handler handler;
	private ProcessReceiver receiver;
	private ScheduledExecutorService executor;
	private int camId = NO_CAM_ID;

	public PhotoActivity() {
		exposureValues = new LinkedList<>();
		handler = new MessageHandler(this);
		receiver = new ProcessReceiver();
		executor = new ScheduledThreadPoolExecutor(1);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_photo);
		progressBar = (ProgressBar) findViewById(R.id.progress_bar);


		if (!getPackageManager()
				.hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
			toast(getResource(R.string.no_cam));
		} else {
			camId = findBackCamera();
		}
	}

	@Override
	protected void onStart() {
		super.onStart();
		if (camId == NO_CAM_ID) {
			toast(getResource(R.string.no_back_cam));
		} else {
			camera = Camera.open(camId);
			preview = new Preview(this, camera);
			getFrameLayout().addView(preview);

			fillExposuresValues();
		}

		registerReceiver(receiver, new IntentFilter(EXPOSURE_MERGE));
	}

	private FrameLayout getFrameLayout() {
		return (FrameLayout) findViewById(R.id.preview);
	}

	private int findBackCamera() {
		int cameraId = NO_CAM_ID;
		// Search for the back facing camera
		int numberOfCameras = Camera.getNumberOfCameras();
		for (int i = MIN; i < numberOfCameras; i++) {
			CameraInfo info = new CameraInfo();
			Camera.getCameraInfo(i, info);
			if (info.facing == CameraInfo.CAMERA_FACING_BACK) {
				cameraId = i;
				break;
			}
		}
		return cameraId;
	}

	@Override
	public String getResource(int key) {
		return getApplicationContext().getResources().getString(key);
	}

	private void toast(String msg) {
		Toast.makeText(this, msg, Toast.LENGTH_LONG)
				.show();
	}

	private void fillExposuresValues() {
		Camera.Parameters params = camera.getParameters();
		exposureValues.clear();
		exposureValues.add(params.getExposureCompensation());
		exposureValues.add(params.getMinExposureCompensation());
		exposureValues.add(params.getMaxExposureCompensation());
	}

	@Override
	protected void onPause() {
		getFrameLayout().removeView(preview);
		preview = null;
		releaseCamera();
		unregisterReceiver(receiver);
		super.onPause();
	}

	private void releaseCamera() {
		if (camera != null) {
			camera.stopPreview();
			camera.setPreviewCallback(null);
			camera.release();
			camera = null;
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.photo, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		if(id == R.id.action_settings){
			startActivity(new Intent(this, SettingsActivity.class));
			return true;
		}

		return super.onOptionsItemSelected(item);
	}

	public void onClick(View view) {
		PhotoCommand command = new PhotoCommand(this, camera);
		int delay = getDelay();
		if(delay > MIN){
			executor.schedule(command, delay, TimeUnit.SECONDS);
		}else {
			executor.execute(command);
		}
	}

	private int getDelay() {
		return parseInt(getPreferences().getString("shutterDelayTime", "0"));
	}

	private boolean isProcessingEnabled() {
		return getPreferences().getBoolean("processHdr", false);
	}

	private SharedPreferences getPreferences() {
		return PreferenceManager.getDefaultSharedPreferences(this);
	}

	@Override
	public LinkedList<Integer> getExposureValues() {
		return new LinkedList<>(exposureValues);
	}

	@Override
	public Handler getHandler() {
		return handler;
	}

	@Override
	public File getPicturesDirectory() {
		return getExternalStoragePublicDirectory(DIRECTORY_DCIM);
	}

	@Override
	public File getInternalDirectory() {
		ContextWrapper cw = new ContextWrapper(getApplicationContext());
		return cw.getDir("data", Context.MODE_PRIVATE);
	}

	void setExecutor(ScheduledExecutorService executor) {
		this.executor = executor;
	}

	private void processHdr(String [] pictures){
		if(isProcessingEnabled()) {
			OpenCvLoaderCallback callback = new OpenCvLoaderCallback(this, pictures);
			OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, callback);
		}
	}

	static class MessageHandler extends Handler {

		private final PhotoActivity activity;

		MessageHandler(PhotoActivity activity) {
			this.activity = activity;
		}

		@Override
		public void handleMessage(Message message) {
			activity.progressBar.setVisibility(View.INVISIBLE);
			Bundle bundle = message.getData();
			if(bundle.isEmpty()) {
				String msg = message.obj.toString();
				activity.toast(msg);
			}else{
				String[] pictures = bundle.getStringArray(
						PICTURES);
				activity.processHdr(pictures);

				informAboutPictures(pictures);
			}
		}

		private void informAboutPictures(String[] pictures) {
			int len = pictures.length;
			File dir = activity.getPicturesDirectory();
			activity.toast(String.format(activity.getResource(R.string.photos_saved), len, dir));
		}
	}

	private class ProcessReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			if(intent.getAction().equals(EXPOSURE_MERGE)){
				String result = intent.getStringExtra("merged");
				toast(result);
			}
		}
	}

	class PhotoCommand implements Runnable{

		private final PhotoActivity activity;
		private final Camera camera;

		PhotoCommand(PhotoActivity activity, Camera camera){
			this.activity = activity;
			this.camera = camera;
		}

		@Override
		public void run() {
			progressBar.setVisibility(View.VISIBLE);
			ContinuesCallback callback = new ContinuesCallback(activity);
			camera.takePicture(null, null, callback);
		}
	}

	private class OpenCvLoaderCallback extends BaseLoaderCallback {
		private String [] pictures;

		OpenCvLoaderCallback(PhotoActivity activity, String [] pictures){
			super(activity);
			this.pictures = pictures;
		}

		@Override
		public void onManagerConnected(int status) {
			if (status  == LoaderCallbackInterface.SUCCESS) {
				ExposureMergeService.startProcessing(getApplicationContext(), pictures);
			}else{
				super.onManagerConnected(status);
			}
		}
	}

}

package de.mario.photo;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.google.inject.Inject;

import org.opencv.android.OpenCVLoader;

import java.io.File;

import de.mario.photo.controller.CameraControlable;
import de.mario.photo.preview.CanvasView;
import de.mario.photo.service.ExposureMergeService;
import de.mario.photo.service.OpenCvService;
import de.mario.photo.settings.SettingsAccess;
import de.mario.photo.settings.SettingsIntentFactory;
import de.mario.photo.support.GalleryOpener;
import de.mario.photo.support.ImageOpener;
import de.mario.photo.support.MediaUpdater;
import roboguice.activity.RoboActivity;
import roboguice.inject.ContentView;
import roboguice.inject.InjectView;

/**
 * Main activity.
 * 
 * @author Mario
 * 
 */
@ContentView(R.layout.activity_photo)
public class PhotoActivity extends RoboActivity implements PhotoActivable{

	private static final int[] VIEW_IDS = new int[]{R.id.shutter, R.id.settings, R.id.gallery};

	private static final String VERS = "version";
	private static final String PREFS = "PREFERENCE";
	@InjectView(R.id.progress_bar)
	private View progressBar;
	@Inject
	private CanvasView canvasView;
	@Inject
	private MyLocationListener locationListener;
	@Inject
	private LocationManager locationManager;
	@Inject
	private SettingsAccess settingsAccess;
	@Inject
	private CameraControlable cameraController;
	@Inject
	private SettingsIntentFactory intentFactory;
	@Inject
	private MediaUpdater mediaUpdater;
	@Inject
	private ImageOpener imageOpener;
	@Inject
	private GalleryOpener galleryOpener;

	private MessageHandler handler;
	private ProcessedMessageReceiver receiver;

	private ViewsOrientationListener orientationListener;

	private ImageToast imageToast;

	public PhotoActivity() {
		handler = new MessageHandler(this);
		receiver = new ProcessedMessageReceiver(this);
	}

	@Override
	public Context getContext() {
		return this;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		createImageToast();

		if (!getPackageManager()
				.hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
			toast(getString(R.string.no_cam));
		} else {
			showDialogWhenFirstRun();
		}
	}

	private void createImageToast() {
		View view = getLayoutInflater().inflate(R.layout.toast, (ViewGroup) findViewById(R.id.toast));
		imageToast = new ImageToast(view);
	}

	@Override
	protected void onStart() {
		super.onStart();
		cameraController.setActivity(this);
		boolean hasCam = cameraController.lookupCamera();
		if (!hasCam) {
			toast(getString(R.string.no_back_cam));
		} else {
			initialize();
		}

		registerReceiver(receiver, new IntentFilter(EXPOSURE_MERGE));
	}

	private void initialize() {
		cameraController.initialize();

		getPreviewLayout().addView(cameraController.getPreview(), 0);
		getPreviewLayout().addView(canvasView, 1);
		getPreviewLayout().addView(cameraController.getFocusView(), 2);
	}

	private void registerLocationListener() {
		if(isGeoTaggingEnabled()) {
			locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
		}
	}

	private void registerViewsOrientationListener(){
		if (orientationListener == null) {
			orientationListener = new ViewsOrientationListener(this);
			for (int id : VIEW_IDS) {
				orientationListener.addView(findViewById(id));
			}
			orientationListener.enable();
		}
	}

	private void unregisterLocationListener() {
		locationManager.removeUpdates(locationListener);
	}

	@Override
	protected void onResume() {
		super.onResume();

		registerLocationListener();
		registerViewsOrientationListener();

		cameraController.reinitialize();
	}

	private void showDialogWhenFirstRun() {
		String current = getVersion();
		String stored = getSharedPreferences(PREFS, MODE_PRIVATE).getString(VERS, "");
		if (!stored.equals(current)){
			new StartupDialog(this).show();
			getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(VERS, current).apply();
		}
	}

	private String getVersion() {
		return BuildConfig.VERSION_NAME + BuildConfig.VERSION_CODE;
	}

	private ViewGroup getPreviewLayout() {
		return (ViewGroup) findViewById(R.id.preview);
	}

	private void toast(int id) {
		toast(getString(id));
	}

	private void toast(String msg) {
		Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
	}

	@Override
	protected void onPause() {
		getPreviewLayout().removeAllViews();
		cameraController.releaseCamera();
		unregisterReceiver(receiver);
		unregisterLocationListener();
		super.onPause();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.photo, menu);
		return true;
	}

	/**
	 * Action handler for shutter button.
	 * @param view the {@link View} for this action.
	 */
	public void onShutter(View view) {
		toggleInputs(false);
		cameraController.shot();
	}

	void prepareForNextShot() {
		toggleInputs(true);
	}

	/**
	 * Enables / disables all input elements, seen on the preview.
	 * @param enabled <code>true</code>: enables the elements.
	 */
	private void toggleInputs(boolean enabled) {
		for (int id : VIEW_IDS) {
			findViewById(id).setEnabled(enabled);
		}
	}

	/**
	 * Action handler for settings button.
	 * @param view the {@link View} for this action.
	 */
	public void onSettings(View view) {
		Intent intent = intentFactory.create();

		hideProgress();
		startActivity(intent);
	}

	public void onGallery(View view) {
		File last = mediaUpdater.getLastUpdated();
		//TODO assign to a new image button
		if (last != null) {
			imageOpener.open(last);
		} else {
			galleryOpener.open();
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		if(id == R.id.action_settings){
			onSettings(null);
			return true;
		}

		return super.onOptionsItemSelected(item);
	}

	//combined settings values
	private boolean isGeoTaggingEnabled() { return isGpsEnabled() && settingsAccess.isGeoTaggingEnabled();}

	private boolean isGpsEnabled() { return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);}
	//end settings values

	Location getCurrentLocation() {
		return locationListener.getCurrentLocation();
	}

	@Override
	public Handler getHandler() {
		return handler;
	}

	@Override
	public SettingsAccess getSettingsAccess() {
		return settingsAccess;
	}

	void processHdr(String [] pictures){
		if(settingsAccess.isProcessingEnabled()) {
			showProgress();
			Intent intent = new Intent(this, ExposureMergeService.class);
			intent.putExtra(OpenCvService.PARAM_PICS, pictures);
			OpenCvLoaderCallback callback = new OpenCvLoaderCallback(this, intent);
			OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, callback);
		}
	}

	private void showProgress() {
		progressBar.setVisibility(View.VISIBLE);
	}

	private void hideProgress() {
		progressBar.setVisibility(View.GONE);
	}

	void refreshPictureFolder(String path){
		hideProgress();
		File file = new File(path);
		imageToast.setImage(file).show();
		mediaUpdater.sendUpdate(file);
	}
}

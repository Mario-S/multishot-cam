package de.mario.photo;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import javax.inject.Inject;

import java.io.File;

import de.mario.photo.glue.HdrProcessControlable;
import de.mario.photo.glue.CameraControlable;
import de.mario.photo.glue.MediaUpdateControlable;
import de.mario.photo.glue.PhotoActivable;
import de.mario.photo.glue.SettingsAccessable;
import de.mario.photo.glue.ViewsMediatable;
import de.mario.photo.settings.SettingsIntentFactory;
import de.mario.photo.support.ViewsOrientationListener;
import de.mario.photo.view.ImageToast;


/**
 * Main activity.
 * 
 * @author Mario
 * 
 */
public class PhotoActivity extends Activity implements PhotoActivable {

	private static final int[] VIEW_IDS = new int[]{R.id.shutter_button, R.id.settings_button,
			R.id.gallery_button, R.id.image_button};


	@Inject
	MyLocationListener locationListener;
	@Inject
	LocationManager locationManager;
	@Inject
	CameraControlable cameraController;
	@Inject
	SettingsIntentFactory intentFactory;
	@Inject
	MediaUpdateControlable mediaUpdateController;
	@Inject
	StartupDialog startupDialog;
	@Inject
	HdrProcessControlable processHdrController;
	@Inject
	SettingsAccessable settingsAccess;
	@Inject
	ViewsMediatable viewsMediator;

	private MessageHandler messageHandler;
	private ImageResultListener receiver;

	private ViewsOrientationListener orientationListener;

	private ImageToast imageToast;

	public PhotoActivity() {
		messageHandler = new MessageHandler(this);
		receiver = new ImageResultListener(this);
	}

	@Override
	public Context getContext() {
		return this;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_photo);

		((PhotoApp)getApplication()).getAppComponent().inject(this);

		cameraController.setActivity(this);

		createImageToast();

		if (!getPackageManager()
				.hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
			toast(getString(R.string.no_cam));
		} else {
			startupDialog.showIfFirstRun();

			setup();
		}
	}

	private void createImageToast() {
		View view = getLayoutInflater().inflate(R.layout.toast, (ViewGroup) findViewById(R.id.toast));
		imageToast = new ImageToast(view);
	}

	private void setup() {
		boolean hasCam = cameraController.lookupCamera();
		if (!hasCam) {
			toast(getString(R.string.no_back_cam));
		} else {
			viewsMediator.setPreview((ViewGroup) findViewById(R.id.preview));
			viewsMediator.setProgressBar(findViewById(R.id.progress_bar));
			viewsMediator.setDisplayImageView((ImageView) findViewById(R.id.image_button));

			cameraController.initialize();
		}
	}


	@Override
	protected void onStart() {
		super.onStart();
		registerReceiver(receiver, new IntentFilter(EXPOSURE_MERGE));
	}

	private void registerLocationListener() {
		if(isGeoTaggingEnabled()) {
			locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
		}
	}

	private void registerOrientationListeners() {
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

		cameraController.reinitialize();
		viewsMediator.setupViews();

		registerLocationListener();
		registerOrientationListeners();

		viewsMediator.updatePaintViews();

		updateImageButton();
	}

	private void toast(String msg) {
		Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
	}

	@Override
	protected void onPause() {
		super.onPause();
		cameraController.releaseCamera();
		unregisterLocationListener();
		viewsMediator.removeViews();
	}

	@Override
	protected void onStop() {
		super.onStop();
		unregisterReceiver(receiver);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.photo, menu);
		return true;
	}

	/**
	 * Action messageHandler for shutter button.
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
		//will be enabled after image is processed
		if (!enabled) {
			viewsMediator.updateDisplayImageView(null);
		}
	}

	/**
	 * Action messageHandler for settings button.
	 * @param view the {@link View} for this action.
	 */
	public void onSettings(View view) {
		Intent intent = intentFactory.create();

		hideProgress();
		startActivity(intent);
	}

	public void onGallery(View view) {
		mediaUpdateController.openGallery();
	}

	public void onImage(View view) {
		mediaUpdateController.openImage();
	}

	/**
	 * Activates or deactivates the button to view the merged image depending of the last result.
	 * It also hides the progress bar.
	 */
	void toggleImageButton() {
		hideProgress();
		updateImageButton();
	}

	private void updateImageButton() {
		Bitmap bitmap = mediaUpdateController.getLastUpdated();
		viewsMediator.updateDisplayImageView(bitmap);
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
	private boolean isGeoTaggingEnabled() {
		return isGpsEnabled() && isEnabled(R.string.geotagging);
	}

	private boolean isGpsEnabled() { return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);}
	//end settings values

	Location getCurrentLocation() {
		return locationListener.getCurrentLocation();
	}

	public Handler getMessageHandler() {
		return messageHandler;
	}

	void processHdr(String [] pictures){
		if (isEnabled(R.string.processHdr)) {
			showProgress();
			processHdrController.process(pictures);
		}
	}

	private boolean isEnabled(int key) {
		return settingsAccess.isEnabled(key);
	}

	private void showProgress() {
		viewsMediator.showProgressBar(true);
	}

	private void hideProgress() {
		viewsMediator.showProgressBar(false);
	}

	void refreshPictureFolder(String path){
		File file = new File(path);
		imageToast.setImage(file).show();
		mediaUpdateController.sendUpdate(file);
	}
}

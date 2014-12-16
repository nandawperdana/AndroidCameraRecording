package com.example.cobalagiasd;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.MediaStore.Video.Thumbnails;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.widget.Chronometer;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.Toast;

public class RecordActivity2 extends Activity {
	final static AlphaAnimation buttonClick = new AlphaAnimation(5F, 0.1F);

	/*
	 * camera
	 */
	private SurfaceHolder surfaceHolder;
	private SurfaceView surfaceView;
	private Camera mCamera;
	private MediaRecorder mMediaRecorder = new MediaRecorder();
	private boolean inPreview = false;
	private boolean cameraConfigured = false;
	CamcorderProfile profile;
	public static final int MEDIA_TYPE_IMAGE = 1;
	public static final int MEDIA_TYPE_VIDEO = 2;

	/*
	 * widgets
	 */
	private ProgressDialog progress, pRender;
	private ImageButton btnRecord;
	private Chronometer mChronometer;
	// private FrameLayout preview;

	/*
	 * vars
	 */
	private boolean cameraFront = false;
	private boolean isRecording = false;
	private Uri uriPath, uriThumb;
	private static SharedPreferences pref;
	private Point p;
	private String KEY_RECORD = "";
	public static final String createdDateVideo = ""
			+ System.currentTimeMillis();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);
		setContentView(R.layout.activity_main);
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

		pref = getSharedPreferences("halomama", Context.MODE_PRIVATE);
		KEY_RECORD = pref.getString("KEY_RECORD", "");

		mChronometer = (Chronometer) findViewById(R.id.chronometer);
		btnRecord = (ImageButton) findViewById(R.id.buttonRecord);

		if (!pref.contains("KEY_RECORD")) {// Open popup window
			if (p != null) {
				KEY_RECORD = "record";
				showPopup(RecordActivity2.this, p);
			}
		}

		/*
		 * camera init
		 */
		// mCamera = getCameraInstance();
		profile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
		surfaceView = (SurfaceView) findViewById(R.id.camera_preview);
		surfaceHolder = surfaceView.getHolder();
		surfaceHolder.addCallback((surfaceCallback));
		surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

		// Checking camera availability
		if (!isDeviceSupportCamera()) {
			Toast.makeText(getApplicationContext(),
					"Maaf! Gadget anda tidak memiliki kamera",
					Toast.LENGTH_LONG).show();
			// will close the app if the device does't have camera
			finish();
		}

		btnRecord.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (isRecording) {
					v.startAnimation(buttonClick);
					// stop recording and release camera
					// captureImage();

					mMediaRecorder.stop(); // stop the recording
					releaseMediaRecorder(); // release the MediaRecorder object
					// mCamera.lock(); // take camera access back from
					// MediaRecorder

					mChronometer.stop();
					btnRecord.setBackgroundResource(R.drawable.fab_record_hdpi);
					// inform the user that recording has stopped
					// setCaptureButtonText("Capture");
					isRecording = false;

					pRender = new ProgressDialog(RecordActivity2.this);
					pRender.setMessage("Render video ..."
							+ uriPath.getPath().toString());
					pRender.setProgressStyle(ProgressDialog.STYLE_SPINNER);
					pRender.setIndeterminate(true);
					pRender.show();
					new Thread(new Runnable() {
						@Override
						public void run() {
							try {
								Thread.sleep(2500);
								String path = uriPath.getPath().toString();
								int end = path.length() - 4;
								String thumbName = path.substring(
										path.lastIndexOf("/") + 1, end);
								// Log.e("PATH GAMBAR", path);
								// Log.e("nama thumb", thumbName);
								Bitmap bmp = createThumbnail(path);
								uriThumb = Uri.fromFile(saveThumbnail(
										thumbName, bmp));
								// Log.e("uri", "" + uriThumb);
								// Log.e("bmp thumb", "" + bmp);
							} catch (Exception e) {
							}
							pRender.dismiss();
						}
					}).start();
				} else {
					// initialize video camera
					if (prepareVideoRecorder()) {
						// Camera is available and unlocked, MediaRecorder is
						// prepared,
						// now you can start recording
						btnRecord
								.setBackgroundResource(R.drawable.fab_stop_hdpi);
						mMediaRecorder.start();

						pref.edit().putString("KEY_RECORD", KEY_RECORD);
						pref.edit().commit();

						mChronometer.setBase(SystemClock.elapsedRealtime());
						mChronometer.start();

						// inform the user that recording has started
						// setCaptureButtonText("Stop");
						isRecording = true;
					} else {
						// prepare didn't work, release the camera
						releaseMediaRecorder();
						// inform user
					}
				}
			}
		});
	}

	SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {
		public void surfaceCreated(SurfaceHolder holder) {
			// no-op -- wait until surfaceChanged()
		}

		public void surfaceChanged(SurfaceHolder holder, int format, int width,
				int height) {
			initPreview(width, height);
			startPreview();
		}

		public void surfaceDestroyed(SurfaceHolder holder) {
			// no-op
		}
	};

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		// TODO Auto-generated method stub
		int[] location = new int[2];
		ImageButton button = (ImageButton) findViewById(R.id.buttonRecord);
		// Get the x, y location and store it in the location[] array
		// location[0] = x, location[1] = y.
		button.getLocationOnScreen(location);

		// Initialize the Point with x, and y positions
		p = new Point();
		p.x = location[0];
		p.y = location[1];
	}

	// The method that displays the popup.
	private void showPopup(final Activity context, Point p) {
		int popupWidth = 360;
		int popupHeight = 150;

		// Inflate the popup_layout.xml
		LinearLayout viewGroup = (LinearLayout) context
				.findViewById(R.id.popup);
		LayoutInflater layoutInflater = (LayoutInflater) context
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View layout = layoutInflater.inflate(R.layout.popup_record, viewGroup);

		// Creating the PopupWindow
		final PopupWindow popupRecord = new PopupWindow(RecordActivity2.this);
		popupRecord.setContentView(layout);
		popupRecord.setWidth(popupWidth);
		popupRecord.setHeight(popupHeight);
		popupRecord.setFocusable(true);

		// Some offset to align the popup a bit to the right, and a bit down,
		// relative to button's position.
		int OFFSET_X = 30;
		int OFFSET_Y = 30;

		// Clear the default translucent background
		popupRecord.setBackgroundDrawable(new BitmapDrawable());

		// Displaying the popup at the specified location, + offsets.
		popupRecord.showAtLocation(layout, Gravity.NO_GRAVITY, p.x + OFFSET_X,
				p.y + OFFSET_Y);

		if (isRecording)
			popupRecord.dismiss();
	}

	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		super.onResume();

		mCamera = getCameraInstance();
		if (!isDeviceSupportCamera()) {
			Toast toast = Toast.makeText(RecordActivity2.this,
					"Maaf, gadget anda tidak memiliki kamera!",
					Toast.LENGTH_LONG);
			toast.show();
			finish();
		}
		// mCamera = Camera.open();
		startPreview();
	}

	/**
	 * find selfie camera
	 * 
	 * @return
	 */
	public int findFrontFacingCamera() {
		int cameraId = -1;
		// Search for the front facing camera
		int numberOfCameras = Camera.getNumberOfCameras();
		for (int i = 0; i < numberOfCameras; i++) {
			CameraInfo info = new CameraInfo();
			Camera.getCameraInfo(i, info);
			if (info.facing == CameraInfo.CAMERA_FACING_FRONT) {
				cameraId = i;
				cameraFront = true;
				break;
			}
		}
		return cameraId;
	}

	/**
	 * find back camera
	 * 
	 * @return
	 */
	public int findBackFacingCamera() {
		int cameraId = -1;
		// Search for the back facing camera
		// get the number of cameras
		int numberOfCameras = Camera.getNumberOfCameras();
		// for every camera check
		for (int i = 0; i < numberOfCameras; i++) {
			CameraInfo info = new CameraInfo();
			Camera.getCameraInfo(i, info);
			if (info.facing == CameraInfo.CAMERA_FACING_BACK) {
				cameraId = i;
				cameraFront = false;
				break;
			}
		}
		return cameraId;
	}

	/**
	 * A safe way to get an instance of the Camera object.
	 */
	public Camera getCameraInstance() {
		Camera c = null;
		try {
			c = Camera.open(findFrontFacingCamera()); // attempt to get a Camera
														// instance
			if (findFrontFacingCamera() == -1) {
				c = Camera.open(findBackFacingCamera());
			}
			c.setDisplayOrientation(90);
		} catch (Exception e) {
			// Camera is not available (in use or does not exist)
		}
		return c; // returns null if camera is unavailable
	}

	/**
	 * Checking device has camera hardware or not
	 * */
	private boolean isDeviceSupportCamera() {
		if (getApplicationContext().getPackageManager().hasSystemFeature(
				PackageManager.FEATURE_CAMERA)) {
			// this device has a camera
			return true;
		} else {
			// no camera on this device
			return false;
		}
	}

	/**
	 * prepare video recorder
	 * 
	 * @return
	 */
	private boolean prepareVideoRecorder() {

		// Step 1: Unlock and set camera to MediaRecorder
		if (mMediaRecorder == null)
			mMediaRecorder = new MediaRecorder(); // Works well
		mCamera.unlock();
		mMediaRecorder.setCamera(mCamera);
		mMediaRecorder.setPreviewDisplay(surfaceHolder.getSurface());

		// Step 2: Set sources
		mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
		mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
		// mMediaRecorder.setMaxDuration(10000);

		// Step 3: set camcoder profile
		mMediaRecorder.setProfile(profile);

		// Step 4: Set output file
		uriPath = getOutputMediaFileUri(MEDIA_TYPE_VIDEO);
		mMediaRecorder.setOutputFile(new File(uriPath.getPath()).toString());

		// Step 5: Set the preview output
		mMediaRecorder.setPreviewDisplay(surfaceHolder.getSurface());

		// Step 6: Prepare configured MediaRecorder
		try {
			mMediaRecorder.prepare();
		} catch (IllegalStateException e) {
			// Log.d("ERROR", "IllegalStateException preparing MediaRecorder: "
			// + e.getMessage());
			releaseMediaRecorder();
			return false;
		} catch (IOException e) {
			// Log.d("ERROR",
			// "IOException preparing MediaRecorder: " + e.getMessage());
			releaseMediaRecorder();
			return false;
		}
		return true;
	}

	private void startPreview() {
		if (cameraConfigured && mCamera != null) {
			mCamera.startPreview();
			inPreview = true;
		}
	}

	private void initPreview(int width, int height) {
		if (mCamera != null && surfaceHolder.getSurface() != null) {
			try {
				mCamera.setPreviewDisplay(surfaceHolder);
			} catch (Throwable t) {
				// Log.e("PreviewDemo-surfaceCallback",
				// "Exception in setPreviewDisplay()", t);
				Toast.makeText(RecordActivity2.this, t.getMessage(),
						Toast.LENGTH_LONG).show();
			}

			if (!cameraConfigured) {
				Camera.Parameters parameters = mCamera.getParameters();
				Camera.Size size = getBestPreviewSize(width, height, parameters);

				if (size != null) {
					parameters.setPreviewSize(size.width, size.height);
					// parameters.setPreviewSize(profile.videoFrameWidth,
					// profile.videoFrameHeight);
					parameters.set("orientation", "portrait");
					parameters.setRotation(90);
					mCamera.setParameters(parameters);
					cameraConfigured = true;
				}
			}
		}
	}

	@Override
	protected void onPause() {
		if (inPreview) {
			mCamera.stopPreview();
		}
		releaseMediaRecorder(); // if you are using MediaRecorder, release it
								// first
		releaseCamera(); // release the camera immediately on pause event
		super.onPause();
	}

	private void releaseMediaRecorder() {
		if (mMediaRecorder != null) {
			mMediaRecorder.reset(); // clear recorder configuration
			mMediaRecorder.release(); // release the recorder object
			mMediaRecorder = null;
			// mCamera.lock(); // lock camera for later use
		}
	}

	private void releaseCamera() {
		if (mCamera != null) {
			mCamera.release(); // release the camera for other applications
			mCamera = null;
			inPreview = false;
		}
	}

	/** Create a file Uri for saving an image or video */
	private static Uri getOutputMediaFileUri(int type) {
		return Uri.fromFile(getOutputMediaFile(type));
	}

	/** Create a File for saving an image or video */
	private static File getOutputMediaFile(int type) {
		// To be safe, you should check that the SDCard is mounted
		// using Environment.getExternalStorageState() before doing this.

		File mediaStorageDir = new File(
				Environment
						.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
				"HaloMama");

		// This location works best if you want the created images to be shared
		// between applications and persist after your app has been uninstalled.

		// Create the storage directory if it does not exist
		if (!mediaStorageDir.exists()) {
			if (!mediaStorageDir.mkdirs()) {
				// Log.d("HaloMama", "gagal membuat direktori");
				return null;
			}
		}

		// Create a media file name
		// String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss")
		// .format(new Date());
		File mediaFile;
		if (type == MEDIA_TYPE_IMAGE) {
			mediaFile = new File(mediaStorageDir.getPath() + File.separator
					+ pref.getString("USERNAME", "") + "-" + createdDateVideo
					+ ".jpg");
		} else if (type == MEDIA_TYPE_VIDEO) {
			mediaFile = new File(mediaStorageDir.getPath() + File.separator
					+ pref.getString("USERNAME", "") + "-" + createdDateVideo
					+ ".mp4");
		} else {
			return null;
		}

		return mediaFile;
	}

	/**
	 * create thumbnail
	 * 
	 * @param filePath
	 * @return
	 */
	public Bitmap createThumbnail(String filePath) {
		return ThumbnailUtils.createVideoThumbnail(filePath,
				Thumbnails.FULL_SCREEN_KIND);
	}

	/**
	 * save the thumbnail
	 * 
	 * @param filename
	 * @return
	 */
	public File saveThumbnail(String filename, Bitmap bmp) {
		File mediaStorageDir = new File(
				Environment
						.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
				"HaloMama");
		// This location works best if you want the created images to be shared
		// between applications and persist after your app has been uninstalled.

		// Create the storage directory if it does not exist
		if (!mediaStorageDir.exists()) {
			if (!mediaStorageDir.mkdirs()) {
				// Log.d("HaloMama", "gagal membuat direktori");
				return null;
			}
		}

		File mediaFile = new File(mediaStorageDir.getPath() + File.separator
				+ filename + ".jpg");
		try {
			FileOutputStream out = new FileOutputStream(mediaFile);
			bmp.compress(Bitmap.CompressFormat.JPEG, 90, out);
			out.flush();
			out.close();

		} catch (Exception e) {
			e.printStackTrace();
		}
		return mediaFile;
	}

	private Camera.Size getBestPreviewSize(int width, int height,
			Camera.Parameters parameters) {
		Camera.Size result = null;

		for (Camera.Size size : parameters.getSupportedPreviewSizes()) {
			if (size.width <= width && size.height <= height) {
				if (result == null) {
					result = size;
				} else {
					int resultArea = result.width * result.height;
					int newArea = size.width * size.height;

					if (newArea > resultArea) {
						result = size;
					}
				}
			}
		}

		return (result);
	}
}

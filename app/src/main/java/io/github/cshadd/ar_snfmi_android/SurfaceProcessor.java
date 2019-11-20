package io.github.cshadd.ar_snfmi_android;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.Image;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.EditText;
import androidx.fragment.app.FragmentManager;
import com.google.ar.core.Anchor;
import com.google.ar.core.AugmentedImage;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.NotYetAvailableException;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.ux.TransformableNode;
import com.google.ar.sceneform.ux.TransformationSystem;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.MultiplePermissionsReport;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

public class SurfaceProcessor
        implements CJavaCameraViewWrapper.CvCameraViewListener2, LocationListener,
        SensorEventListener {
    private static final int FALLBACK_THRESHOLD = 32;
    private static final double MIN_OPENGL_VERSION = 3.0;
    private static final String TAG = "KAPLAN-PROCESSOR";

    private CommonActivity activity;
    private CARFragmentWrapper arFragment;
    private Scene.OnUpdateListener arSceneOnUpdateListener;
    private Anchor cautionModelAnchor;
    private AnchorNode cautionModelAnchorNode;
    private TransformableNode cautionModelNode;
    private ModelRenderable cautionModelRenderable;
    private EditText etSurfaceType;
    private EditText etThreshold;
    private boolean isLastDataGood;
    private CJavaCameraViewWrapper javaCameraView;
    private Sensor lightSensor;
    private Location location;
    private LocationManager locationManager;
    private float lux;
    private BaseLoaderCallback openCVLoaderCallback;
    private Mat openCVProcessedMat;
    private SaveData saveData;
    private boolean saveDataNow;
    private SensorManager sensorManager;
    private boolean validARCoreCautionGenerated;
    private int validContourBoxes;

    SurfaceProcessor(CommonActivity activity) {
        this.activity = activity;
    }

    @SuppressLint("ObsoleteSdkInt")
    private void handleARSupport() {
        final ActivityManager activityManager = (ActivityManager) activity
                .getSystemService(Context.ACTIVITY_SERVICE);
        String openGlVersionString = "0.0";
        if (activityManager != null) {
            openGlVersionString = activityManager.getDeviceConfigurationInfo()
                    .getGlEsVersion();
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            activity.handleError(TAG, "Unable to load ARCore. " +
                    "Sceneform requires Android N or later.");
        }
        else if (Double.parseDouble(openGlVersionString) < MIN_OPENGL_VERSION) {
            activity.handleError(TAG, "Unable to load ARCore. " +
                    "Sceneform requires OpenGL ES 3.0 later.");
        }
        else {
            Log.d(TAG, "AR loaded!");
        }
    }

    private void handleOpenCVSupport() {
        if (OpenCVLoader.initDebug()) {
            openCVLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        } else {
            activity.handleError(TAG, "Unable to load OpenCV. " +
                    "Library initialization error.");
        }
    }

    private void handleSupport() {
        handleARSupport();
        handleOpenCVSupport();
    }

    void onCreate() {
        location = null;
        saveData = new SaveData(activity);
        saveDataNow = false;

        final FragmentManager fragmentManager = activity.getSupportFragmentManager();

        arFragment = (CARFragmentWrapper)fragmentManager.findFragmentById(R.id.ar);
        // javaCameraView = activity.findViewById(R.id.java_cam);
        etSurfaceType = activity.findViewById(R.id.et_surface);
        etThreshold = activity.findViewById(R.id.et_threshold);
        sensorManager = (SensorManager)activity.getSystemService(Context.SENSOR_SERVICE);

        if (javaCameraView != null) {
            Log.d(TAG, "OpenCV debug mode.");
            Dexter.withActivity(activity)
                    .withPermissions(
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.CAMERA,
                            Manifest.permission.INTERNET,
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    .withListener(new MultiplePermissionsListener() {
                        private static final String TAG = "WIEGLY";

                        @Override
                        public void onPermissionsChecked(MultiplePermissionsReport report) {
                            if (report.areAllPermissionsGranted()) {
                                Log.d(TAG, "All permissions granted!");
                            }
                        }

                        @Override
                        public void onPermissionRationaleShouldBeShown(
                                List<PermissionRequest> permissions, PermissionToken token) { }
                    }).check();
        }

        arSceneOnUpdateListener = frameTime -> {
            final ArSceneView arSceneView = arFragment.getArSceneView();
            final Scene scene = arSceneView.getScene();
            final Frame frame = arSceneView.getArFrame();
            final TransformationSystem transformationSystem = arFragment.getTransformationSystem();
            if (frame != null) {
                final com.google.ar.core.Camera frameCamera = frame.getCamera();
                final Collection<AugmentedImage> augmentedImages = frame
                        .getUpdatedTrackables(AugmentedImage.class);
                try {
                    final Image image = frame.acquireCameraImage();
                    final Mat mat = openCVConvertYuv420888ToMat(image, false);
                    Core.flip(mat.t(), mat, 1);
                    final Mat greyMat = new Mat();
                    mat.copyTo(greyMat);
                    Imgproc.cvtColor(greyMat, greyMat, Imgproc.COLOR_RGB2GRAY);
                    openCVContourProcessor(greyMat, mat, threshold());
                    greyMat.release();

                    if (cautionModelRenderable != null) {
                        if (frameCamera.getTrackingState() == TrackingState.TRACKING) {
                            final Session session = arSceneView.getSession();
                            if (session != null) {
                                final Config config = session.getConfig();
                                config.setFocusMode(Config.FocusMode.AUTO);
                                session.configure(config);
                                final com.google.ar.sceneform.Camera sceneCamera = scene.getCamera();

                                if (cautionModelAnchor != null) {
                                    cautionModelAnchor.detach();
                                }
                                final Vector3 cameraPos = sceneCamera.getWorldPosition();
                                final Vector3 cameraForward = sceneCamera.getForward();
                                final Quaternion cameraRot = sceneCamera.getWorldRotation();
                                final Vector3 position = Vector3.add(cameraPos,
                                        cameraForward.scaled(1.0f));
                                final Quaternion rotY180 = Quaternion.multiply(cameraRot,
                                        new Quaternion(Vector3.up(), 180f));
                                final Pose pose = Pose.makeTranslation(position.x, position.y,
                                        position.z);
                                cautionModelAnchor = session.createAnchor(pose);

                                cautionModelAnchorNode = new AnchorNode(cautionModelAnchor);
                                cautionModelAnchorNode.setParent(scene);
                                if (cautionModelNode != null) {
                                    scene.removeChild(cautionModelNode);
                                    cautionModelNode.setRenderable(null);
                                }
                                cautionModelNode = new TransformableNode(transformationSystem);
                                cautionModelNode.setParent(cautionModelAnchorNode);
                                validARCoreCautionGenerated = false;
                                if (validContourBoxes > 0) {
                                    validARCoreCautionGenerated = true;
                                    cautionModelNode.setRenderable(cautionModelRenderable);
                                }
                                if (saveDataNow) {
                                    openCVProcessedMat = mat;
                                    saveData();
                                    saveDataNow = false;
                                }

                                cautionModelNode.setLocalRotation(rotY180);
                            }
                        }
                    }
                    else {
                        activity.handleWarning(TAG, "Renderable not loaded.");
                    }
                    mat.release();
                    image.close();
                }
                catch (NotYetAvailableException e) {
                    activity.handleWarning(TAG, e);
                }
            }
        };

        openCVLoaderCallback = new BaseLoaderCallback(activity) {
            @Override
            public void onManagerConnected(int status) {
                if (status == openCVLoaderCallback.SUCCESS) {
                    Log.d(TAG, "OpenCV loaded!");
                    if (javaCameraView != null) {
                        javaCameraView.enableView();
                    }
                } else {
                    activity.handleError(TAG, "OpenCV failed to load with status: "
                            + status);
                    super.onManagerConnected(status);
                }
            }
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            ModelRenderable.builder()
                    .setSource(activity, Uri.parse("caution.sfb"))
                    .build()
                    .thenAccept(renderable -> cautionModelRenderable = renderable)
                    .exceptionally(throwable -> {
                        activity.handleWarning(TAG, throwable);
                        return null;
                    });
        }

        if (javaCameraView != null) {
            javaCameraView.setCvCameraViewListener(this);
        }

        final List<Sensor> lightSensors = sensorManager.getSensorList(Sensor.TYPE_LIGHT);
        if (lightSensors.size() > 0) {
            lightSensor = lightSensors.get(0);
        }

        locationManager = (LocationManager) activity.getSystemService(
                Context.LOCATION_SERVICE);
    }

    void onDestroy() {
        if (arFragment != null) {
            arFragment.onDestroy();
            arFragment.getArSceneView().getScene().removeOnUpdateListener(arSceneOnUpdateListener);
        }
        if (javaCameraView != null) {
            javaCameraView.disableView();
        }
        if (sensorManager != null && lightSensor != null) {
            sensorManager.unregisterListener(this, lightSensor);
        }
        if (locationManager != null) {
            locationManager.removeUpdates(this);
        }
    }

    void onPause() {
        if (arFragment != null) {
            arFragment.onPause();
            arFragment.getArSceneView().getScene().removeOnUpdateListener(arSceneOnUpdateListener);
        }
        if (javaCameraView != null) {
            javaCameraView.disableView();
        }
        if (sensorManager != null && lightSensor != null) {
            sensorManager.unregisterListener(this, lightSensor);
        }
        if (locationManager != null) {
            locationManager.removeUpdates(this);
        }
    }

    void onResume() {
        handleSupport();
        if (arFragment != null) {
            arFragment.onResume();
            arFragment.getArSceneView().getScene().addOnUpdateListener(arSceneOnUpdateListener);
        }
        if (sensorManager != null && lightSensor != null) {
            sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (locationManager != null) {
                if (activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED &&
                        activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                                == PackageManager.PERMISSION_GRANTED) {
                    locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0,
                            this);
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0,
                            this);
                }

            }
        }
    }

    void onStop() {
        if (arFragment != null) {
            arFragment.onStop();
            arFragment.getArSceneView().getScene().removeOnUpdateListener(arSceneOnUpdateListener);
        }
        if (javaCameraView != null) {
            javaCameraView.disableView();
        }
        if (sensorManager != null && lightSensor != null) {
            sensorManager.unregisterListener(this, lightSensor);
        }
        if (locationManager != null) {
            locationManager.removeUpdates(this);
        }
    }

    private Mat openCVConvertYuv420888ToMat(Image image, boolean isGreyOnly) {
        final int width = image.getWidth();
        final int height = image.getHeight();

        final Image.Plane yPlane = image.getPlanes()[0];
        final int ySize = yPlane.getBuffer().remaining();

        if (isGreyOnly) {
            final byte[] data = new byte[ySize];
            yPlane.getBuffer().get(data, 0, ySize);

            final Mat greyMat = new Mat(height, width, CvType.CV_8UC1);
            greyMat.put(0, 0, data);

            return greyMat;
        }

        final Image.Plane uPlane = image.getPlanes()[1];
        final Image.Plane vPlane = image.getPlanes()[2];

        final int uSize = uPlane.getBuffer().remaining();
        final int vSize = vPlane.getBuffer().remaining();

        final byte[] data = new byte[ySize + (ySize/2)];

        yPlane.getBuffer().get(data, 0, ySize);

        final ByteBuffer ub = uPlane.getBuffer();
        final ByteBuffer vb = vPlane.getBuffer();

        final int uvPixelStride = uPlane.getPixelStride();
        if (uvPixelStride == 1) {
            uPlane.getBuffer().get(data, ySize, uSize);
            vPlane.getBuffer().get(data, ySize + uSize, vSize);

            final Mat yuvMat = new Mat(height + (height / 2), width, CvType.CV_8UC1);
            yuvMat.put(0, 0, data);
            final Mat rgbMat = new Mat(height, width, CvType.CV_8UC3);
            Imgproc.cvtColor(yuvMat, rgbMat, Imgproc.COLOR_YUV2RGB_I420, 3);
            yuvMat.release();
            return rgbMat;
        }

        vb.get(data, ySize, vSize);
        for (int i = 0; i < uSize; i += 2) {
            data[ySize + i + 1] = ub.get(i);
        }

        final Mat yuvMat = new Mat(height + (height / 2), width, CvType.CV_8UC1);
        yuvMat.put(0, 0, data);
        final Mat rgbMat = new Mat(height, width, CvType.CV_8UC3);
        Imgproc.cvtColor(yuvMat, rgbMat, Imgproc.COLOR_YUV2RGB_NV21, 3);
        yuvMat.release();
        return rgbMat;
    }

    private void openCVContourProcessor(Mat grey, Mat mat) {
        openCVContourProcessor(grey, mat, 1, grey.height(), grey.width());
    }

    private void openCVContourProcessor(Mat grey, Mat mat, int threshold) {
        openCVContourProcessor(grey, mat, threshold, grey.height(), grey.width());
    }

    private void openCVContourProcessor(Mat grey, Mat mat, int threshold,
                                        int height, int width) {
        validContourBoxes = 0;
        final List<MatOfPoint> contours = new ArrayList<>();
        Imgproc.findContours(grey, contours, new Mat(), Imgproc.RETR_TREE,
                Imgproc.CHAIN_APPROX_SIMPLE);
        if (mat != null) {
            Imgproc.drawContours(mat, contours, -1, new Scalar(0, 250, 0), 1);
        }
        for (MatOfPoint contour : contours) {
            final CRect r = new CRect(Imgproc.boundingRect(contour));
            if (mat != null) {
                if (r.height >= (height / threshold)
                        && r.height < height
                        && r.width >= (width / threshold)
                        && r.width < width) {
                    validContourBoxes++;
                    Imgproc.rectangle(mat, r.tl(), r.br(), new Scalar(0, 0, 250), 4);
                }
                else {
                    Imgproc.rectangle(mat, r.tl(), r.br(), new Scalar(0, 250, 0), 4);
                }

            }
        }
    }

    private int threshold() {
        int threshold = FALLBACK_THRESHOLD;
        try {
            threshold = Integer.parseInt(etThreshold.getText() + "");
        }
        catch (NumberFormatException e) {
            Log.e(TAG, e.getMessage());
            e.fillInStackTrace();
        }
        return threshold;
    }

    private void saveData() {
        if (saveData.isExternalStorageUseable()) {
            final File csvDirectory = saveData.getExternalStorageDirectory(
                    Environment.DIRECTORY_DOCUMENTS,"test_csv");

            final long currentTimeMillis = System.currentTimeMillis();
            final Date date = new java.util.Date(currentTimeMillis);
            String address = "DEFINE";
            String gps = "DEFINE";
            if (location != null) {
                final Geocoder gc = new Geocoder(activity);
                if(Geocoder.isPresent()){
                    try {
                        final List<Address> addressList = gc.getFromLocation(location.getLatitude(),
                                location.getLongitude(), 1);
                        if (addressList.size() > 0) {
                            address = addressList.get(0).getAddressLine(0);
                        }
                    }
                    catch (IOException e) {
                        activity.handleWarning(TAG, e);
                    }
                }
                gps = "Lat: " + location.getLatitude() + "; Long:" + location.getLongitude() + ";"
                        + "Alt: " + location.getAltitude();
            }

            boolean arCoreObjectExpected = validARCoreCautionGenerated;
            if (!isLastDataGood) {
                arCoreObjectExpected = !validARCoreCautionGenerated;
            }

            final File csvFile = new File(csvDirectory, "test.csv");
            if (!csvFile.exists()) {
                saveData.saveCSV(new String[] {
                                "Timestamp (UTC)",
                                "Timestamp 2 (UTC)",
                                "Location GPS",
                                "Location Address",
                                "Lighting (Lux)",
                                "Surface Type",
                                "Threshold",
                                "OpenCV Total Contour Bounding Boxes",
                                "AR Core Object Expected",
                                "AR Core Object Generated"
                        },
                        "test.csv", csvDirectory);
            }

            saveData.saveCSV(new String[] {
                            currentTimeMillis + "",
                            date + "",
                            gps,
                            address,
                            lux + "",
                            etSurfaceType.getText() + "",
                            etThreshold.getText() + "",
                            validContourBoxes + "",
                            arCoreObjectExpected + "",
                            validARCoreCautionGenerated + ""
                    },
                    "test.csv", csvDirectory);

            final File pictureDirectory = saveData.getExternalStorageDirectory(
                    Environment.DIRECTORY_PICTURES,"test_pics");

            final Bitmap bmp = Bitmap.createBitmap(openCVProcessedMat.cols(),
                    openCVProcessedMat.rows(),
                    Bitmap.Config.RGB_565);
            Utils.matToBitmap(openCVProcessedMat, bmp);
            if (arFragment != null) {
                saveData.saveBitmap(bmp, "test_ar_" + System.currentTimeMillis()
                        + ".png", pictureDirectory);
                Log.d(TAG, "test");
            }
            else {
                saveData.saveBitmap(bmp, "test_opencv_" + System.currentTimeMillis()
                        + ".png", pictureDirectory);
                Log.d(TAG, "test");
            }
        }
    }

    void saveDataNow(boolean isGoodData) {
        saveDataNow = true;
        isLastDataGood = isGoodData;
    }

    @Override
    public Mat onCameraFrame(CJavaCameraViewWrapper.CvCameraViewFrame inputFrame) {
        openCVProcessedMat = inputFrame.rgba();
        final Mat greyMat = new Mat(openCVProcessedMat.rows(), openCVProcessedMat.cols(),
                CvType.CV_8UC1);
        openCVProcessedMat.copyTo(greyMat);
        Imgproc.cvtColor(greyMat, greyMat, Imgproc.COLOR_RGB2GRAY);
        openCVContourProcessor(greyMat, openCVProcessedMat, threshold());
        greyMat.release();
        if (saveDataNow) {
            saveData();
            saveDataNow = false;
        }
        return openCVProcessedMat;
    }

    @Override
    public void onCameraViewStarted(int width, int height) { }

    @Override
    public void onCameraViewStopped() {
        if (openCVProcessedMat != null) {
            openCVProcessedMat.release();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    @Override
    public void onLocationChanged(Location location) { this.location = location; }

    @Override
    public void onProviderDisabled(String provider) { }

    @Override
    public void onProviderEnabled(String provider) { }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
            lux = event.values[0];
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) { }
}

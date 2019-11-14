package io.github.cshadd.ar_snfmi_android;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
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
import androidx.fragment.app.FragmentManager;
import com.google.ar.core.Anchor;
import com.google.ar.core.AugmentedImage;
import com.google.ar.core.Camera;
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
import org.opencv.core.Point;
import org.opencv.core.Rect;
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
    private static final double MIN_OPENGL_VERSION = 3.0;
    private static final String TAG = "KAPLAN-PROCESSOR";

    private CommonActivity activity;
    private CARFragmentWrapper arFragment;
    private Scene.OnUpdateListener arSceneOnUpdateListener;
    private Anchor cautionModelAnchor;
    private AnchorNode cautionModelAnchorNode;
    private TransformableNode cautionModelNode;
    private ModelRenderable cautionModelRenderable;
    private List<ContourBoxData> contourBoxData;
    private boolean isLocationAllowed;
    private CJavaCameraViewWrapper javaCameraView;
    private Sensor lightSensor;
    private Location location;
    private LocationManager locationManager;
    private float lux;
    private BaseLoaderCallback openCVLoaderCallback;
    private Mat openCVProcessedMat;
    private SaveData saveData;
    private SensorManager sensorManager;

    boolean saveDataNow;

    SurfaceProcessor(CommonActivity activity) {
        this.activity = activity;
    }

    private static class ContourBoxData {
        private Point bottomRight;
        private Point topLeft;

        ContourBoxData(Point bottomRight, Point topLeft) {
            this.bottomRight = bottomRight;
            this.topLeft = topLeft;
        }

        Point getBottomRight() {
            return bottomRight;
        }

        Point getTopLeft() {
            return topLeft;
        }
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
        } else if (Double.parseDouble(openGlVersionString) < MIN_OPENGL_VERSION) {
            activity.handleError(TAG, "Unable to load ARCore. " +
                    "Sceneform requires OpenGL ES 3.0 later.");
        } else {
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
        final FragmentManager fragmentManager = activity.getSupportFragmentManager();

        location = null;
        saveData = new SaveData(activity);
        saveDataNow = false;

        // arFragment = (CARFragmentWrapper)fragmentManager.findFragmentById(R.id.ar);
        javaCameraView = activity.findViewById(R.id.java_cam);
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
                                Log.d(TAG, "Needed permissions were granted!");
                            } else {
                                activity.handleWarning(TAG, "Needed permissions were not granted!");
                            }
                        }

                        @Override
                        public void onPermissionRationaleShouldBeShown(
                                List<PermissionRequest> permissions, PermissionToken token) {
                        }
                    }).check();
        }

        arSceneOnUpdateListener = frameTime -> {
            final ArSceneView arSceneView = arFragment.getArSceneView();
            final Scene scene = arSceneView.getScene();
            final Frame frame = arSceneView.getArFrame();
            final TransformationSystem transformationSystem = arFragment.getTransformationSystem();
            if (frame != null) {
                final Camera camera = frame.getCamera();
                final Collection<AugmentedImage> augmentedImages = frame
                        .getUpdatedTrackables(AugmentedImage.class);
                try {
                    final Image image = frame.acquireCameraImage();
                    final Mat mat = openCVConvertYuv420888ToMat(image, false);
                    Core.flip(mat.t(), mat, 1);
                    // Imgproc.resize(mat, mat, new Size(arSceneView.getWidth(), arSceneView.getHeight()));
                    final Mat greyMat = new Mat();
                    mat.copyTo(greyMat);
                    Imgproc.cvtColor(greyMat, greyMat, Imgproc.COLOR_RGB2GRAY);
                    contourBoxData = openCVProcessMajorContour(greyMat, mat);
                    greyMat.release();
                    if (saveDataNow) {
                        openCVProcessedMat = mat;
                        saveData();
                        saveDataNow = false;
                    }
                    mat.release();
                    image.close();

                    if (cautionModelRenderable != null) {
                        if (camera.getTrackingState() == TrackingState.TRACKING) {
                            final Session session = arSceneView.getSession();
                            final Config config;
                            if (session != null) {
                                config = session.getConfig();
                                config.setFocusMode(Config.FocusMode.AUTO);
                                session.configure(config);

                                if (cautionModelAnchor != null) {
                                    cautionModelAnchor.detach();
                                }
                                // Translation must be changed here
                                /*cautionModelAnchor = session.createAnchor(camera.getPose()
                                        .compose(Pose.makeTranslation(1,0, 0))
                                        .extractTranslation());*/
                                cautionModelAnchor = session.createAnchor(new Pose(
                                        new float[]{0, 0, -1},
                                        new float[]{0, 0, 0, 0}
                                ));

                                cautionModelAnchorNode = new AnchorNode(cautionModelAnchor);
                                cautionModelAnchorNode.setParent(scene);
                                if (cautionModelNode != null) {
                                    scene.removeChild(cautionModelNode);
                                    cautionModelNode.setRenderable(null);
                                }
                                cautionModelNode = new TransformableNode(transformationSystem);
                                cautionModelNode.setParent(cautionModelAnchorNode);
                                cautionModelNode.setRenderable(cautionModelRenderable);
                                cautionModelNode.setLocalRotation(Quaternion.axisAngle(
                                        new Vector3(0f, 1f, 0), 180f));

                                // Also put here if nodes were shown...
                            }
                        }
                    } else {
                        activity.handleWarning(TAG, "Renderable not loaded.");
                    }
                    Log.d(TAG, "AR scene processed.");
                } catch (NotYetAvailableException e) {
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

        // Be aware that this size does not include the padding at the end, if there is any
        // (e.g. if pixel stride is 2 the size is ySize / 2 - 1).
        final int uSize = uPlane.getBuffer().remaining();
        final int vSize = vPlane.getBuffer().remaining();

        final byte[] data = new byte[ySize + (ySize/2)];

        yPlane.getBuffer().get(data, 0, ySize);

        final ByteBuffer ub = uPlane.getBuffer();
        final ByteBuffer vb = vPlane.getBuffer();

        // Stride guaranteed to be the same for u and v planes
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

        // If pixel stride is 2 there is padding between each pixel
        // converting it to NV21 by filling the gaps of the v plane with the u values
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

    private List<ContourBoxData> openCVProcessContour(Mat grey) {
        return openCVProcessMajorContour(grey, null);
    }

    private List<ContourBoxData> openCVProcessMajorContour(Mat grey, Mat mat) {
        final List<ContourBoxData> data = new ArrayList<>();
        final List<MatOfPoint> contours = new ArrayList<>();
        Imgproc.findContours(grey, contours, new Mat(), Imgproc.RETR_TREE,
                Imgproc.CHAIN_APPROX_SIMPLE);
        if (mat != null) {
            Imgproc.drawContours(mat, contours, -1, new Scalar(0, 250, 0), 1);
        }
        for (int i = 0; i < contours.size(); i++) {
            final Rect r = Imgproc.boundingRect(contours.get(i));
            if (r.height > 10 && r.height < grey.height() - 10
                    && r.width > 10 && r.width < grey.width() - 10) {
                final Point bottomRight = new Point(r.x + r.width, r.y + r.height);
                final Point topLeft = new Point(r.x, r.y);
                data.add(new ContourBoxData(bottomRight, topLeft));
                if (mat != null) {
                    Imgproc.rectangle(mat, topLeft, bottomRight, new Scalar(0, 250, 0), 4);
                }
            }
        }

        Log.d(TAG, contours.size() + " contours processed.");
        return data;
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


            final File csvFile = new File(csvDirectory, "test.csv");
            if (!csvFile.exists()) {
                saveData.saveCSV(new String[] {
                                "Timestamp (UTC)",
                                "Timestamp 2 (UTC)",
                                "Location GPS",
                                "Location Address",
                                "Lighting (Lux)",
                                "Weather",
                                "Surface Type",
                                "OpenCV Valid Contour Bounding Boxes",
                                "OpenCV Total Contour Bounding Boxes",
                                "AR Core Valid Caution Objects",
                                "AR Core Total Caution Objects Generated",
                        },
                        "test.csv", csvDirectory);
            }

            saveData.saveCSV(new String[] {
                            currentTimeMillis + "",
                            date + "",
                            gps,
                            address,
                            lux + "",
                            "DEFINE",
                            "DEFINE",
                            "DEFINE",
                            contourBoxData.size() + "",
                            "DEFINE",
                            "DEFINE"
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

    @Override
    public Mat onCameraFrame(CJavaCameraViewWrapper.CvCameraViewFrame inputFrame) {
        openCVProcessedMat = inputFrame.rgba();
        final Mat greyMat = new Mat(openCVProcessedMat.rows(), openCVProcessedMat.cols(),
                CvType.CV_8UC1);
        openCVProcessedMat.copyTo(greyMat);
        Imgproc.cvtColor(greyMat, greyMat, Imgproc.COLOR_RGB2GRAY);
        contourBoxData = openCVProcessMajorContour(greyMat, openCVProcessedMat);
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

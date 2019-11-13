package io.github.cshadd.ar_snfmi_android;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.YuvImage;
import android.media.Image;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
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
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SurfaceProcessor
    implements JavaCameraView.CvCameraViewListener2 {
    private static final double MIN_OPENGL_VERSION = 3.0;
    private static final String TAG = "KAPLAN-PROCESSOR";

    private CommonActivity activity;
    private CARFragmentWrapper arFragment;
    private Scene.OnUpdateListener arSceneOnUpdateListener;
    private Anchor cautionModelAnchor;
    private AnchorNode cautionModelAnchorNode;
    private TransformableNode cautionModelNode;
    private ModelRenderable cautionModelRenderable;
    private List<ContourData> contourData = new ArrayList<>();
    private CJavaCameraViewWrapper javaCameraView;
    private BaseLoaderCallback openCVLoaderCallback;
    private Mat openCVProcessedMat;
    private SaveData saveData;
    public boolean saveImageNow;

    SurfaceProcessor(CommonActivity activity) { this.activity = activity; }

    private static class ContourData {
        private Point bottomRight;
        private Point topLeft;

        ContourData(Point bottomRight, Point topLeft) {
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
        final ActivityManager activityManager = (ActivityManager)activity
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
            Log.i(TAG, "AR loaded!");
        }
    }

    private void handleOpenCVSupport() {
        if (OpenCVLoader.initDebug()) {
            openCVLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
        else {
            activity.handleError(TAG, "Unable to load OpenCV. " +
                    "Library initialization error.");
        }
    }

    private void handleSupport() {
        handleARSupport();
        handleOpenCVSupport();
    }

    void onCreate() {
        saveData = new SaveData(activity);
        saveImageNow = false;

        arFragment = (CARFragmentWrapper)activity.getSupportFragmentManager().findFragmentById(R.id.ar);

        // javaCameraView = activity.findViewById(R.id.java_cam);

        if (javaCameraView != null) {
            Log.d(TAG, "OpenCV debug mode.");
            Dexter.withActivity(activity)
                    .withPermissions(
                            Manifest.permission.CAMERA,
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    .withListener(new MultiplePermissionsListener() {
                        private static final String TAG = "WIEGLY";

                        @Override
                        public void onPermissionsChecked(MultiplePermissionsReport report) {
                            if (report.areAllPermissionsGranted()) {
                                Log.i(TAG, "Needed permissions were granted!");
                            }
                            else {
                                activity.handleWarning(TAG, "Needed permissions were not granted!");
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
                final Camera camera = frame.getCamera();
                final Collection<AugmentedImage> augmentedImages = frame
                        .getUpdatedTrackables(AugmentedImage.class);
                try {
                    final Image image = frame.acquireCameraImage();
                    final Image.Plane[] planes = image.getPlanes();
                    final ByteBuffer cameraPlaneY = planes[0].getBuffer();
                    final ByteBuffer cameraPlaneU = planes[1].getBuffer();
                    final ByteBuffer cameraPlaneV = planes[2].getBuffer();
                    final byte[] compositeByteArray = new byte[cameraPlaneY.capacity()
                            + cameraPlaneU.capacity()
                            + cameraPlaneV.capacity()];

                    cameraPlaneY.get(compositeByteArray, 0, cameraPlaneY.capacity());
                    cameraPlaneU.get(compositeByteArray, cameraPlaneY.capacity(),
                            cameraPlaneU.capacity());
                    cameraPlaneV.get(compositeByteArray, cameraPlaneY.capacity()
                            + cameraPlaneU.capacity(), cameraPlaneV.capacity());

                    final ByteArrayOutputStream baOutputStream = new ByteArrayOutputStream();
                    final YuvImage yuvImage = new YuvImage(compositeByteArray, ImageFormat.NV21,
                            image.getWidth(), image.getHeight(),null);
                    yuvImage.compressToJpeg(new android.graphics.Rect(0, 0,
                                    image.getWidth(),
                                    image.getHeight()),
                            75, baOutputStream);
                    final byte[] byteForBitmap = baOutputStream.toByteArray();
                    final Bitmap bitmap = BitmapFactory.decodeByteArray(byteForBitmap, 0,
                            byteForBitmap.length);
                    baOutputStream.close();
                    final Mat mat = new Mat();
                    final Bitmap bitmapCopy = bitmap.copy(Bitmap.Config.ARGB_8888,true);
                    Utils.bitmapToMat(bitmapCopy, mat);
                    Imgproc.cvtColor(mat, mat, Imgproc.COLOR_BGR2RGB);
                    openCVProcessContour(mat);
                    Log.d(TAG, "Contours: " + contourData.size());
                    mat.release();
                    image.close();

                    if (cautionModelRenderable != null) {
                        if (camera.getTrackingState() == TrackingState.TRACKING) {
                            final Session session = arSceneView.getSession();
                            final Config config = session.getConfig();
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
                                    new float[] {0, 0, -1},
                                    new float[] {0, 0, 0, 0}
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

                        }
                    }
                    else {
                        activity.handleWarning(TAG, "Renderable not loaded.");
                    }
                    Log.d(TAG, "AR scene processed.");
                }
                catch (NotYetAvailableException | IOException e) {
                    activity.handleWarning(TAG, e);
                }
            }
        };

        openCVLoaderCallback = new BaseLoaderCallback(activity) {
            @Override
            public void onManagerConnected(int status) {
                if (status == openCVLoaderCallback.SUCCESS) {
                    Log.i(TAG, "OpenCV loaded!");
                    if (javaCameraView != null) {
                        javaCameraView.enableView();
                    }
                }
                 else {
                    activity.handleWarning(TAG, "OpenCV failed to load with status: "
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
    }

    void onDestroy() {
        if (arFragment != null) {
            arFragment.onDestroy();
            arFragment.getArSceneView().getScene().removeOnUpdateListener(arSceneOnUpdateListener);
        }
        if (javaCameraView != null) {
            javaCameraView.disableView();
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
    }

    void onResume() {
        handleSupport();
        if (arFragment != null) {
            arFragment.onResume();
            arFragment.getArSceneView().getScene().addOnUpdateListener(arSceneOnUpdateListener);
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
    }

    private void openCVProcessContour(Mat mat) {
        final Mat processed = new Mat();
        mat.copyTo(processed);
        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_BGR2GRAY);
        final List<MatOfPoint> contours = new ArrayList<>();
        Imgproc.findContours(mat, contours, new Mat(), Imgproc.RETR_TREE,
                Imgproc.CHAIN_APPROX_SIMPLE);
        for (int i = 0; i < contours.size(); i++) {
            if (javaCameraView != null) {
                // Imgproc.drawContours(processed, contours, i, new Scalar(0, 255, 0), 1);
            }
            final Rect r = Imgproc.boundingRect(contours.get(i));
            if (r.height > 50 && r.height < mat.height() - 50
                    && r.width > 50 && r.width < mat.width() - 50) {
                final Point bottomRight = new Point(r.x + r.width, r.y + r.height);
                final Point topLeft = new Point(r.x, r.y);
                contourData.add(new ContourData(bottomRight, topLeft));
                // if (javaCameraView != null) {
                    Imgproc.rectangle(processed, topLeft, bottomRight, new Scalar(0, 255, 0), 8);
                // }
            }
        }
        mat.release();
        openCVProcessedMat = processed;

        if (saveImageNow) {
            if (saveData.isExternalStorageUseable()) {
                final File directory = saveData.getExternalStorageDirectory(
                        Environment.DIRECTORY_PICTURES,"test");
                if (directory != null) {
                    try {
                        final Bitmap bmp = Bitmap.createBitmap(processed.cols(), processed.rows(),
                                Bitmap.Config.ARGB_8888);
                        Utils.matToBitmap(processed, bmp);
                        saveData.saveBitmap(bmp, "test",
                                directory);
                    }
                    catch (IOException e) {
                        activity.handleWarning(TAG, e);
                    }
                }
            }
            saveImageNow = false;
        }
        Log.d(TAG, "Contours processed.");
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        if (openCVProcessedMat != null) {
            openCVProcessedMat.release();
        }
        final Mat mat = inputFrame.rgba();
        openCVProcessContour(mat);
        return openCVProcessedMat;
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        openCVProcessedMat = new Mat(width, height, CvType.CV_8UC4);
    }

    @Override
    public void onCameraViewStopped() {
        if (openCVProcessedMat != null) {
            openCVProcessedMat.release();
        }
    }
}

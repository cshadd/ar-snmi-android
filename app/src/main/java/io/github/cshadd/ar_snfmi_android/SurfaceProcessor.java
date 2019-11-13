package io.github.cshadd.ar_snfmi_android;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.Image;
import android.net.Uri;
import android.os.Build;
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
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
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
    private List<ContourData> contourData;
    private CJavaCameraViewWrapper javaCameraView;
    private BaseLoaderCallback openCVLoaderCallback;
    private Mat openCVProcessedMat;
    private SaveData saveData;
    boolean saveDataNow;

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
        saveDataNow = false;

        final FragmentManager fragmentManager = activity.getSupportFragmentManager();
        arFragment = (CARFragmentWrapper)fragmentManager.findFragmentById(R.id.ar);
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
                    final Mat mat = openCVConvertYuv420888ToMat(image, false);
                    Core.flip(mat.t(), mat, 1);
                    // Imgproc.resize(mat, mat, new Size(arSceneView.getWidth(), arSceneView.getHeight()));
                    final Mat greyMat = new Mat();
                    mat.copyTo(greyMat);
                    Imgproc.cvtColor(greyMat, greyMat, Imgproc.COLOR_RGB2GRAY);
                    contourData = openCVProcessContour(greyMat, mat);
                    greyMat.release();
                    if (saveDataNow) {
                        saveImage(mat);
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

                                // Also put here if nodes were shown...
                            }
                        }
                    }
                    else {
                        activity.handleWarning(TAG, "Renderable not loaded.");
                    }
                    Log.d(TAG, "AR scene processed.");
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
                    Log.i(TAG, "OpenCV loaded!");
                    if (javaCameraView != null) {
                        javaCameraView.enableView();
                    }
                }
                 else {
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

    private List<ContourData> openCVProcessContour(Mat grey) {
        return openCVProcessContour(grey, null);
    }

    private List<ContourData> openCVProcessContour(Mat grey, Mat mat) {
        final List<ContourData> data = new ArrayList<>();
        final List<MatOfPoint> contours = new ArrayList<>();
        Imgproc.findContours(grey, contours, new Mat(), Imgproc.RETR_TREE,
                Imgproc.CHAIN_APPROX_SIMPLE);
        if (mat != null) {
            Imgproc.drawContours(mat, contours, -1, new Scalar(0, 255, 0), 1);
        }
        for (int i = 0; i < contours.size(); i++) {
            final Rect r = Imgproc.boundingRect(contours.get(i));
            if (r.height > 10 && r.height < grey.height() - 10
                    && r.width > 10 && r.width < grey.width() - 10) {
                final Point bottomRight = new Point(r.x + r.width, r.y + r.height);
                final Point topLeft = new Point(r.x, r.y);
                data.add(new ContourData(bottomRight, topLeft));
                if (mat != null) {
                    Imgproc.rectangle(mat, topLeft, bottomRight, new Scalar(0, 255, 0), 4);
                }
            }
        }

        Log.d(TAG, contours.size() + " contours processed.");
        return data;
    }

    private void saveImage(Mat mat) {
        if (saveData.isExternalStorageUseable()) {
            final File directory = saveData.getExternalStorageDirectory(
                    Environment.DIRECTORY_PICTURES,"test");
            if (directory != null) {
                try {
                    final Bitmap bmp = Bitmap.createBitmap(mat.cols(), mat.rows(),
                            Bitmap.Config.ARGB_8888);
                    Utils.matToBitmap(mat, bmp);
                    if (arFragment != null) {
                        saveData.saveBitmap(bmp, "test_ar",
                                directory);
                    }
                    else {
                        saveData.saveBitmap(bmp, "test_opencv",
                                directory);
                    }
                }
                catch (IOException e) {
                    activity.handleWarning(TAG, e);
                }
            }
        }
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        if (openCVProcessedMat != null) {
            openCVProcessedMat.release();
        }
        openCVProcessedMat = inputFrame.rgba();
        final Mat greyMat = new Mat();
        openCVProcessedMat.copyTo(greyMat);
        Imgproc.cvtColor(greyMat, greyMat, Imgproc.COLOR_BGR2GRAY);
        openCVProcessContour(greyMat, openCVProcessedMat);
        greyMat.release();
        if (saveDataNow) {
            saveImage(openCVProcessedMat);
            saveDataNow = false;
        }
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

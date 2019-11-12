package io.github.cshadd.ar_snfmi_android;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.media.Image;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.view.ViewGroup;
import com.google.ar.core.Anchor;
import com.google.ar.core.AugmentedImage;
import com.google.ar.core.Camera;
import com.google.ar.core.Frame;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
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

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import java.nio.ByteBuffer;
import java.util.Collection;

public class SurfaceProcessor
    implements JavaCameraView.CvCameraViewListener2 {
    private static final boolean DEBUG_OPENCV_MODE = true;
    private static final double MIN_OPENGL_VERSION = 3.0;
    private static final String TAG = "KAPLAN-PROCESSOR";

    private CommonActivity activity;
    private CARFragmentWrapper arFragment;
    private Scene.OnUpdateListener arSceneOnUpdateListener;
    private Anchor cautionModelAnchor;
    private AnchorNode cautionModelAnchorNode;
    private TransformableNode cautionModelNode;
    private ModelRenderable cautionModelRenderable;
    private CJavaCameraViewWrapper javaCameraView;
    private BaseLoaderCallback openCVLoaderCallback;
    private Mat openCVMat;

    SurfaceProcessor(CommonActivity activity) { this.activity = activity; }

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
        arFragment = (CARFragmentWrapper)activity.getSupportFragmentManager()
                .findFragmentById(R.id.ar);
        javaCameraView = activity.findViewById(R.id.java_cam);

        if (DEBUG_OPENCV_MODE) {
            if (arFragment != null) {
                activity.getSupportFragmentManager().beginTransaction().remove(arFragment).commit();
                arFragment = null;
            }
        }
        else {
            if (javaCameraView != null) {
                ((ViewGroup) javaCameraView.getParent()).removeView(javaCameraView);
                javaCameraView = null;
            }
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
                    final Mat buf = new Mat(image.getHeight(), image.getWidth(), CvType.CV_8UC1);
                    final ByteBuffer byteBuffer = image.getPlanes()[0].getBuffer();
                    final byte[] bytes = new byte[byteBuffer.remaining()];
                    byteBuffer.get(bytes);
                    buf.put(0, 0, bytes);

                    final Mat mat = Imgcodecs.imdecode(buf, Imgcodecs.IMREAD_COLOR);

                    // OpenCV detection here

                    Log.d(TAG, "Done processing image");

                    if (cautionModelRenderable != null) {
                        if (camera.getTrackingState() == TrackingState.TRACKING) {
                            final Session session = arSceneView.getSession();
                            if (session != null) {
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
                    }
                    else {
                        activity.handleWarning(TAG, "Renderable not loaded.");
                    }

                    buf.release();
                    mat.release();
                    image.close();
                }
                catch (NotYetAvailableException e) {
                    if (activity != null) {
                        activity.handleWarning(TAG, e);
                    }
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
                    // OpenCV Dependencies here
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

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        openCVMat = inputFrame.rgba();
        return openCVMat;
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        openCVMat = new Mat(width, height, CvType.CV_8UC4);
    }

    @Override
    public void onCameraViewStopped() {
        if (openCVMat != null) {
            openCVMat.release();
        }
    }
}

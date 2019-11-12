package io.github.cshadd.ar_snfmi_android;

import android.content.Context;
import android.util.AttributeSet;

import org.opencv.android.JavaCameraView;

public class CJavaCameraViewWrapper
        extends JavaCameraView {
    private static final String TAG = "KAPLAN-JAVACAM";

    public CJavaCameraViewWrapper(Context context, AttributeSet attrs) { super(context, attrs); }

    public CJavaCameraViewWrapper(Context context, int cameraId) {
        super(context, cameraId);
    }
}
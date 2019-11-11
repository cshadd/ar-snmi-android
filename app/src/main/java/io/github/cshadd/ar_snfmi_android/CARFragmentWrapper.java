package io.github.cshadd.ar_snfmi_android;

import android.Manifest;

import com.google.ar.sceneform.ux.ArFragment;

public class CARFragmentWrapper
        extends ArFragment {
    private static final String TAG = "KAPLAN-AR";

    @Override
    public String[] getAdditionalPermissions() {
        super.getAdditionalPermissions();
        return new String[]{ Manifest.permission.WRITE_EXTERNAL_STORAGE };
    }
}
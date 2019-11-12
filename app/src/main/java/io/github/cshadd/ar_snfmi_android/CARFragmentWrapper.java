package io.github.cshadd.ar_snfmi_android;

import android.Manifest;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import com.google.ar.sceneform.ux.ArFragment;

public class CARFragmentWrapper
        extends ArFragment {
    private static final String TAG = "KAPLAN-AR";

    @Override
    public String[] getAdditionalPermissions() {
        super.getAdditionalPermissions();
        return new String[]{ Manifest.permission.WRITE_EXTERNAL_STORAGE };
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final View view = super.onCreateView(inflater, container, savedInstanceState);
        getPlaneDiscoveryController().hide();
        getPlaneDiscoveryController().setInstructionView(null);
        return view;
    }
}
package io.github.cshadd.ar_snfmi_android;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class MainActivity
        extends CommonActivity {
    private static final String TAG = "NOGA";

    private boolean isToggled;
    private SurfaceProcessor processor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        processor = new SurfaceProcessor(this);
        processor.onCreate();

        isToggled = false;

        final EditText etSurfaceType = findViewById(R.id.et_surface);
        final EditText etThreshold = findViewById(R.id.et_threshold);

        final FloatingActionButton fabBad = findViewById(R.id.fab_bad);
        fabBad.setOnClickListener(v -> {
            processor.saveDataNow(false);
            handleInfo(TAG, "Trying to save data.");
        });

        final FloatingActionButton fabGood = findViewById(R.id.fab_good);
        fabGood.setOnClickListener(v -> {
            processor.saveDataNow(true);
            handleInfo(TAG, "Trying to save data.");
        });

        etSurfaceType.setVisibility(View.INVISIBLE);
        etThreshold.setVisibility(View.INVISIBLE);
        fabBad.hide();
        fabGood.hide();

        final FloatingActionButton fabToggle = findViewById(R.id.fab_toggle);
        fabToggle.setOnClickListener(v -> {
            if (isToggled) {
                etSurfaceType.setVisibility(View.INVISIBLE);
                etThreshold.setVisibility(View.INVISIBLE);
                fabBad.hide();
                fabGood.hide();
                handleInfo(TAG, "Hiding toggle.");
                isToggled = false;
            }
            else {
                etSurfaceType.setVisibility(View.VISIBLE);
                etThreshold.setVisibility(View.VISIBLE);
                fabBad.show();
                fabGood.show();
                handleInfo(TAG, "Showing toggle.");
                isToggled = true;
            }
        });

        Log.d(TAG, "I love navigation!");
    }

    @Override
    public void onDestroy() {
        if (processor != null) {
            processor.onDestroy();
        }
        super.onDestroy();
    }

    @Override
    public void onPause() {
        if (processor != null) {
            processor.onPause();
        }
        super.onPause();
    }

    @Override
    public void onResume() {
        if (processor != null) {
            processor.onResume();
        }
        super.onResume();
    }

    @Override
    public void onStop() {
        if (processor != null) {
            processor.onStop();
        }
        super.onStop();
    }
}

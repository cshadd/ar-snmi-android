package io.github.cshadd.ar_snfmi_android;

import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;

import androidx.appcompat.widget.Toolbar;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

public class MainActivity
        extends CommonActivity {
    private static final String TAG = "NOGA";

    private SurfaceProcessor processor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        processor = new SurfaceProcessor(this);
        processor.onCreate();

        final FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(view -> Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show());

        Log.i(TAG, "I love navigation!");
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

package io.github.cshadd.ar_snfmi_android;

import android.util.Log;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public abstract class CommonActivity
        extends AppCompatActivity {
    public void handleError(String TAG, Throwable e) {
        String message = e.getMessage();
        if (message == null) {
            message = e.toString();
        }
        handleError(TAG, message);
        e.printStackTrace();
    }

    public void handleError(String TAG, String error) {
        runOnUiThread(() -> Toast.makeText(this, TAG + " (" +
                        getPackageName() + "): " + error,
                Toast.LENGTH_LONG).show());
        Log.e(TAG, error);
        finish();
    }

    public void handleInfo(String TAG, String info) {
        runOnUiThread(() -> Toast.makeText(this, TAG + " (" +
                        getPackageName() + "): " + info,
                Toast.LENGTH_LONG).show());
        Log.i(TAG, info);
    }

    public void handleWarning(String TAG, Throwable e) {
        String message = e.getMessage();
        if (message == null) {
            message = e.toString();
        }
        handleWarning(TAG, message);
        e.printStackTrace();
    }

    public void handleWarning(String TAG, String warning) {
        runOnUiThread(() -> Toast.makeText(this, TAG + " (" +
                        getPackageName() + "): " + warning,
                Toast.LENGTH_LONG).show());
        Log.w(TAG, warning);
    }
}

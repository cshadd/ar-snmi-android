package io.github.cshadd.ar_snfmi_android;

import android.content.res.Resources;
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
        final Resources res = getResources();
        final String message = res.getString(R.string.warning_prefix, error);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        Log.e(TAG, message);
        finish();
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
        final Resources res = getResources();
        final String message = res.getString(R.string.warning_prefix, warning);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        Log.w(TAG, message);
    }
}

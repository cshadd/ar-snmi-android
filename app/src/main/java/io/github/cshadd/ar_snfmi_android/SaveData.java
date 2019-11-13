package io.github.cshadd.ar_snfmi_android;

import android.graphics.Bitmap;
import android.os.Environment;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class SaveData {
    private static final String TAG = "KAPLAN-SAVE";

    private CommonActivity activity;

    SaveData(CommonActivity activity) { this.activity = activity; }

    boolean isExternalStorageUseable() {
        final String state = Environment.getExternalStorageState();
        return !Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)
                && Environment.MEDIA_MOUNTED.equals(state);
    }

    File getExternalStorageDirectory(String directoryType, String name) {
        final File file = new File(activity.getExternalFilesDir(directoryType), name);
        return file;
    }

    void saveBitmap(Bitmap bmp, String fileName, File directory)
            throws IOException {
        final FileOutputStream out = new FileOutputStream(directory.getPath()
                + "/" + fileName);

        bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
    }
}

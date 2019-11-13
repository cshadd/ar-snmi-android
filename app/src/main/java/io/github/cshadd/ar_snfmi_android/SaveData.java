package io.github.cshadd.ar_snfmi_android;

import android.graphics.Bitmap;
import android.os.Environment;

import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
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
        if (file.isFile() || file.isDirectory()) {
            activity.handleWarning(TAG, "Directory exists or is a file: " + file.getPath());
        }
        else if (!file.mkdir()) {
            activity.handleWarning(TAG, "Unable to create a directory: " + file.getPath());
        }
        return file;
    }

    void saveBitmap(Bitmap bmp, String fileName, File directory) {
        final File file = new File(directory, fileName);
        if (!file.isDirectory()) {
            try {
                final FileOutputStream out = new FileOutputStream(file);
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
                out.close();
            }
            catch (IOException e) {
                activity.handleWarning(TAG, e);
            }
        }
        else {
            activity.handleWarning(TAG, "Filename is directory: " + file.getPath());
        }
    }

    void saveCSV(String[] data, String fileName, File directory) {
        final File file = new File(directory, fileName);
        FileWriter fileWriter = null;
        CSVWriter csvWriter = null;
        try {
            if (file.isFile())
            {
                fileWriter = new FileWriter(file, true);
                csvWriter = new CSVWriter(fileWriter);
                csvWriter.writeNext(data);
                csvWriter.close();
                fileWriter.close();
            }
            else if (!file.isDirectory())
            {
                fileWriter = new FileWriter(file);
                csvWriter = new CSVWriter(fileWriter);
                csvWriter.writeNext(data);
                csvWriter.close();
                fileWriter.close();
            }
            else {
                activity.handleWarning(TAG, "Filename is directory: " + file.getPath());
            }
        }
        catch (IOException e) {
            activity.handleWarning(TAG, e);
            e.printStackTrace();
        }
    }
}

package com.example.insyncrecorder;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class FileTransferActivity extends AppCompatActivity {
    private static final String TAG = "FILE_TRANSFER_ACTIVITY";

    private Activity activity;

    ImageView tickImage;
    TextView progressIndicator;
    ProgressBar progressBar;

    public static DataOutputStream outputStream;
    public static File file;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_filetransfer);

        activity = this;

        tickImage = findViewById(R.id.tickMark);
        progressIndicator = findViewById(R.id.progressIndicator);
        progressBar = findViewById(R.id.progressBar);
        tickImage.setVisibility(View.INVISIBLE);
        progressBar.setProgress(0);

        Thread thread = new Thread(this::transferFile);
        thread.start();
    }

    private void transferFile() {
        double fileSize = file.length();
        int chunkSize = 4 * 1024;
        byte[] bytes = new byte[chunkSize];
        int count = 0;
        double progress = 0;
        InputStream fileStream = null;

        Log.d(TAG, "File size: " + fileSize);

        // check if filestream can be opened
        try {
            fileStream = new FileInputStream(file);
        } catch (IOException e) {
            Log.e(TAG, e.getMessage(), e);
            e.printStackTrace();
            activity.runOnUiThread(() -> Toast.makeText(activity,
                    "Error in opening file stream from given file location",
                    Toast.LENGTH_SHORT).show());
        }

        try {
            while ((count = fileStream.read(bytes)) != -1) {
                outputStream.write(bytes, 0, count);
                outputStream.flush();
                progress += count;
                double fractionProgress = progress / fileSize;
                double percentageProgress = fractionProgress * 100;
                int newProgressBarState = (int) percentageProgress;
                final String progressIndicatorText = "Sending file to host...  " + newProgressBarState + "%";
                activity.runOnUiThread(() -> progressIndicator.setText(progressIndicatorText));
                progressBar.setProgress(newProgressBarState);
            }
            activity.runOnUiThread(() -> progressIndicator.setText("File Transfer Complete! You can now quit the application"));
            fileStream.close();
            outputStream.close();
            activity.runOnUiThread(() -> tickImage.setVisibility(View.VISIBLE));
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, e.getMessage());
            activity.runOnUiThread(() -> Toast.makeText(activity,
                    "Error in IO Operation. Unable to send file. Please transfer the file manually",
                    Toast.LENGTH_LONG).show());
        }
    }
}

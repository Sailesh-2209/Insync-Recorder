package com.example.insyncrecorder;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;

public class FileTransferActivity extends AppCompatActivity {
    private static final String TAG = "FILE_TRANSFER_ACTIVITY";

    private Activity activity;

    ImageView tickImage;
    ProgressBar progressBar;

    public static Socket socket;
    public static File file;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_filetransfer);

        activity = this;

        tickImage = findViewById(R.id.tickMark);
        progressBar = findViewById(R.id.progressBar);
        tickImage.setVisibility(View.INVISIBLE);
        progressBar.setProgress(0);

        Thread thread = new Thread(() -> transferFile());
        thread.start();
    }

    private void transferFile() {
        Log.d(TAG, "Start of transferFile method");
        double fileSize = file.length();
        Log.d(TAG, "File size: " + fileSize);
        int chunkSize = 16 * 1024;
        byte[] bytes = new byte[chunkSize];

        try {
            DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
            InputStream fileStream = new FileInputStream(file);
            int count = 0;
            double progress = 0;
            while ((count = fileStream.read(bytes)) > 0) {
                outputStream.write(bytes, 0, count);
                progress += chunkSize;
                Log.d(TAG, "Progress: " + progress);
                double fractionProgress = progress / fileSize;
                Log.d(TAG, "Fraction Progress: " + fractionProgress);
                double percentageProgress = fractionProgress * 100;
                Log.d(TAG, "Percentage Progress: " + percentageProgress);
                int newProgressBarState = (int) percentageProgress;
                Log.d(TAG, String.valueOf(newProgressBarState));
                progressBar.setProgress(newProgressBarState);
            }
            fileStream.close();
            outputStream.close();
            socket.close();
            activity.runOnUiThread(() -> {
                tickImage.setVisibility(View.VISIBLE);
            });
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, e.getMessage());
            activity.runOnUiThread(() -> {
                Toast.makeText(activity,
                        "Error in IO Operation. Unable to send file. Please transfer the file manually",
                        Toast.LENGTH_LONG).show();
            });
        }
    }
}

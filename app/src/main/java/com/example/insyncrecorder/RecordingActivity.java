package com.example.insyncrecorder;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.ScaleGestureDetector;
import android.view.Surface;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.FileOutputOptions;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public class RecordingActivity extends AppCompatActivity {
    private final String TAG = "RECORDING_ACTIVITY";

    private PreviewView previewView;
    private VideoCapture<Recorder> videoCapture;
    Recording recording;
    Camera camera;
    File vidPath;
    DataOutputStream outputStream;
    DataInputStream inputStream;

    TextView recordingTime;
    LinearLayout recordingTimeContainer;

    public static Socket socket;
    public static String deviceRole;

    volatile boolean updateTime;

    Activity activity;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "Start of Recording Activity");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recorder);

        activity = RecordingActivity.this;

        previewView = findViewById(R.id.previewView);
        recordingTime = findViewById(R.id.recordingTime);
        recordingTimeContainer = findViewById(R.id.recordingTimeContainer);

        recordingTimeContainer.setVisibility(View.INVISIBLE);

        Log.d(TAG, "Request for permissions if they aren't already granted");
        if (!allPermissionsGranted()) requestPermissions();

        Log.d(TAG, "Calling startCamera method");
        startCamera();
        updateTime = false;
        Thread recordingTimeThread = new Thread(this::updateRecordingTime);
        recordingTimeThread.start();
        Thread socketThread = new Thread(this::listenForStartSignal);
        socketThread.start();
    }

    private void listenForStartSignal() {
        // at this point, socket is not null
        // send a greeting message which contains the role of the current device
        // role can either be FRONT_VIEW or BACK_VIEW
        try {
            inputStream = new DataInputStream(socket.getInputStream());
            outputStream = new DataOutputStream(socket.getOutputStream());
            outputStream.writeUTF(deviceRole);
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
            e.printStackTrace();
            activity.runOnUiThread(() -> Toast.makeText(activity, "Error in IO operation", Toast.LENGTH_LONG).show());
        }

        // if there was an error, do something
        // TODO: handle IOException when trying to get inputStream and outputStream from socket

        // listen for confirmation from server about the role of the current device
        try {
            assert inputStream != null;
            assert outputStream != null;
            String message = inputStream.readUTF();
            activity.runOnUiThread(() -> Toast.makeText(activity, message, Toast.LENGTH_LONG).show());
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, e.getMessage());
            activity.runOnUiThread(() -> Toast.makeText(activity, "Error in IO operation", Toast.LENGTH_LONG).show());
        }

        // listen for a START signal to start recording
        try {
            String message = "";
            while (!message.equalsIgnoreCase("START")) message = inputStream.readUTF();
            Log.d(TAG, "Received signal to start recording");
            captureVideo();
            activity.runOnUiThread(() -> Toast.makeText(activity, "Video recording has started", Toast.LENGTH_SHORT).show());

            // TODO: start the timer when the start signal is received
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, e.getMessage());
            activity.runOnUiThread(() -> Toast.makeText(activity, "Error in IO operation", Toast.LENGTH_LONG).show());
        }

        // listen for STOP signal to stop recording
        try {
            String message = "";
            while (!message.equalsIgnoreCase("STOP")) message = inputStream.readUTF();
            Log.d(TAG, "Received signal to stop recording");
            captureVideo();
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
            activity.runOnUiThread(() -> Toast.makeText(activity, "Error in IO operation", Toast.LENGTH_LONG).show());
        }
    }

    private void updateRecordingTime() {
        while (!updateTime);
        int time = 0;
        while (updateTime) {
            time++;
            int minutes = time % 60;
            int hours = time / 60;
            String curTime = String.format(Locale.US, "%02d:%02d", hours, minutes);
            activity.runOnUiThread(() -> recordingTime.setText(curTime));
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
                Log.e(TAG, "Update Recording Time Thread was interrupted");
                break;
            }
        }
    }

    private void captureVideo() {
        if (videoCapture == null) return;
        if (recording != null) {
            Log.d(TAG, "Video capture ends now");
            recording.stop();
            updateTime = false;
            recording = null;
            return;
        }
        File vidDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "SportTrack");
        if (!vidDir.exists()) {
            boolean ok = vidDir.mkdirs();
            if (!ok) {
                activity.runOnUiThread(() -> Toast.makeText(activity, "Unable to create resources", Toast.LENGTH_SHORT).show());
            }
        }
        String FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS";
        String date = new SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis());
        vidPath = new File(vidDir, date + ".mp4");
        FileOutputOptions fileOutputOptions = new FileOutputOptions.Builder(vidPath).build();
        updateTime = true;
        activity.runOnUiThread(() -> recordingTimeContainer.setVisibility(View.VISIBLE));
        recording = videoCapture
                .getOutput()
                .prepareRecording(this, fileOutputOptions)
                .start(ContextCompat.getMainExecutor(RecordingActivity.this), (VideoRecordEvent videoRecordEvent) -> {
                    if (videoRecordEvent instanceof VideoRecordEvent.Start) {
                        Log.d(TAG, "Video capture starts now");
                    } else if (videoRecordEvent instanceof VideoRecordEvent.Finalize) {
                        if (((VideoRecordEvent.Finalize) videoRecordEvent).hasError()) {
                            recording.close();
                            recording = null;
                            Log.e(TAG, "Error " + ((VideoRecordEvent.Finalize) videoRecordEvent).getCause());
                        } else {
                            Toast.makeText(RecordingActivity.this,
                                    "Video captured successfully",
                                    Toast.LENGTH_SHORT)
                                    .show();

                            FileTransferActivity.outputStream = outputStream;
                            FileTransferActivity.file = vidPath;
                            Intent intent = new Intent(activity, FileTransferActivity.class);
                            startActivity(intent);
                        }
                    }
                });
    }

    @SuppressLint("RestrictedApi")
    private void startCamera() {
        Log.d(TAG, "Top of startCamera");
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(RecordingActivity.this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                cameraProvider.unbindAll();
                CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                // TODO: enable pinch to zoom
                ScaleGestureDetector.SimpleOnScaleGestureListener listener = new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        float zoomRatio = 0;
                        try {
                            zoomRatio = camera.getCameraInfo().getZoomState().getValue().getZoomRatio();
                        } catch (NullPointerException e) {
                            e.printStackTrace();
                            Log.e(TAG, e.getMessage());
                        }
                        float delta = detector.getScaleFactor();
                        camera.getCameraControl().setZoomRatio(zoomRatio * delta);
                        return true;
                    }
                };
                ScaleGestureDetector scaleGestureDetector = new ScaleGestureDetector(activity, listener);
                previewView.setOnTouchListener((view, motionEvent) -> {
                    scaleGestureDetector.onTouchEvent(motionEvent);
                    return view.performClick();
                });

                Recorder recorder = new Recorder.Builder()
                        .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                        .build();
                videoCapture = VideoCapture.withOutput(recorder);
                    videoCapture.setTargetRotation(Surface.ROTATION_90);
                Log.d(TAG, "Bottom of startCamera");
                camera = cameraProvider.bindToLifecycle(RecordingActivity.this, cameraSelector, preview, videoCapture);
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void requestPermissions() {
        int CAMERA_REQUEST_CODE = 100;
        ActivityCompat.requestPermissions(this,
                new String[] {Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                CAMERA_REQUEST_CODE);
    }

    private boolean allPermissionsGranted() {
        boolean permission1 = ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        boolean permission2 = ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        return permission1 && permission2;
    }
}

package com.example.insyncrecorder;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Toast;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private final String TAG = "MAIN_ACTIVITY";

    private Activity activity;

    // ip address is in the form of four edit text fields
    private EditText ipAddress1, ipAddress2, ipAddress3, ipAddress4, portNumber;

    private Socket socket;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "Start of Main Activity");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(TAG, "Content View set successfully");

        activity = MainActivity.this;

        // view elements
        ipAddress1 = findViewById(R.id.ipAddressNumber1);
        ipAddress2 = findViewById(R.id.ipAddressNumber2);
        ipAddress3 = findViewById(R.id.ipAddressNumber3);
        ipAddress4 = findViewById(R.id.ipAddressNumber4);
        portNumber = findViewById(R.id.portNumber);
        Button nextButton = findViewById(R.id.button);

        // disable the next button temporarily until the
        // IP address and port number are completely entered
        nextButton.setEnabled(false);

        // all the number input fields are added to a list
        // to make it easier to set onChange listeners
        List<EditText> inputList = new ArrayList<>();
        inputList.add(ipAddress1);
        inputList.add(ipAddress2);
        inputList.add(ipAddress3);
        inputList.add(ipAddress4);
        inputList.add(portNumber);

        for (EditText input : inputList) {
            input.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}
                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                    boolean flag = true;
                    for (EditText otherInput : inputList) {
                        if (otherInput.getText().length() == 0) {
                            flag = false;
                            break;
                        }
                    }
                    nextButton.setEnabled(flag);
                }
                @Override
                public void afterTextChanged(Editable editable) {}
            });
        }

        nextButton.setOnClickListener(this::nextButtonClickListener);
    }

    private void nextButtonClickListener(View view) {
        String hostIpAddress = getHostIpAddress();
        int portNum = getPortNumber();
        String deviceRole = getDeviceRole();
        Log.d(TAG, "hostIpAddress: " + hostIpAddress);
        Log.d(TAG, "portNumber: " + portNum);
        Log.d(TAG, "deviceRole: " + deviceRole);

        ProgressDialog progressDialog = new ProgressDialog(MainActivity.this);
        progressDialog.setIndeterminate(true);
        progressDialog.setTitle("Network operation");
        progressDialog.setMessage("Connecting...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        // make a connection to the socket on a separate thread
        // networking activities should not be done on the main thread
        Thread thread = new Thread(() -> {
            boolean result = makeSocketConnection(hostIpAddress, portNum);
            Log.d(TAG, "Socket connection successful");
            if (result) {
                RecordingActivity.socket = socket;
                RecordingActivity.deviceRole = deviceRole;
                Log.d(TAG, "Starting new intent - RecordingActivity");
                Intent intent = new Intent(MainActivity.this, RecordingActivity.class);
                progressDialog.dismiss();
                startActivity(intent);
            } else {
                progressDialog.dismiss();
            }
        });
        thread.start();
    }

    // ip address is input in four separate number fields
    // this method puts the four fields together and returns the IP
    // address as a string
    private String getHostIpAddress() {
        String byte1 = ipAddress1.getText().toString();
        String byte2 = ipAddress2.getText().toString();
        String byte3 = ipAddress3.getText().toString();
        String byte4 = ipAddress4.getText().toString();
        return byte1 + "." + byte2 + "." + byte3 + "." + byte4;
    }

    private int getPortNumber() {
        String portNum = portNumber.getText().toString();
        int ans = 0;
        try {
            ans = Integer.parseInt(portNum);
        } catch (NumberFormatException e) {
            Log.e(TAG, e.getMessage());
            portNumber.setText(0);
        }
        return ans;
    }

    private String getDeviceRole() {
        int backViewId = R.id.radioOption1;
        RadioGroup radioGroup = findViewById(R.id.radioGroup);
        int curr = radioGroup.getCheckedRadioButtonId();
        if (curr == backViewId) return "BACK_VIEW";
        else return "SIDE_VIEW";
    }

    private boolean makeSocketConnection(String hostIpAddress, int portNum) {
        Log.d(TAG, "Trying to make a socket connection to the host");
        // make a socket connection to the host
        try {
            socket = new Socket(hostIpAddress, portNum);
        } catch (UnknownHostException e) {
            activity.runOnUiThread(() -> Toast.makeText(MainActivity.this,
                    "Unknown Host. Check the IP address and make sure you are connected to the same network as host computer",
                    Toast.LENGTH_LONG).show());
            Log.e(TAG, e.getMessage());
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            activity.runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error in IO Operation. Please try again.", Toast.LENGTH_LONG).show());
            Log.e(TAG, e.getMessage());
            e.printStackTrace();
            return false;
        }

        return true;
    }
}
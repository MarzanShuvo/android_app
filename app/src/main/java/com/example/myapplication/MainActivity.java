package com.example.myapplication;

import android.os.Bundle;
import android.content.pm.ActivityInfo;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.widget.EditText;
import android.os.Handler;
import com.airbnb.lottie.LottieAnimationView;
import androidx.appcompat.app.AlertDialog;
import android.widget.Toast;
import org.zeromq.ZMQ;



public class MainActivity extends AppCompatActivity {

    private LottieAnimationView animationView1;
    private LottieAnimationView animationView2;
    private Handler uiHandler;
    private boolean isAnimation1Visible = true;
    private ZMQ.Context zmqContext;
    private ZMQ.Socket zmqSocket;
    private boolean isDialogDisplayed = false; // Prevent multiple dialogs

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Allow both landscape orientations
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);

        // Enable edge-to-edge rendering
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize LottieAnimationView references
        animationView1 = findViewById(R.id.animationView1);
        animationView2 = findViewById(R.id.animationView2);
        uiHandler = new Handler();

        // Start with animationView1 visible
        animationView1.setVisibility(LottieAnimationView.VISIBLE);
        animationView1.playAnimation();
        animationView2.setVisibility(LottieAnimationView.GONE);

        // Prompt for IP Address
        showIpDialog();
    }


    private void showIpDialog() {
        if (isDialogDisplayed) return; // Prevent multiple dialogs
        isDialogDisplayed = true;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter IP Address");

        // Input field for the IP
        final EditText input = new EditText(this);
        input.setHint("e.g., 10.0.2.2");
        builder.setView(input);

        builder.setPositiveButton("Connect", (dialog, which) -> {
            isDialogDisplayed = false;
            String ipAddress = input.getText().toString().trim();
            if (!ipAddress.isEmpty() && validateIpAddress(ipAddress)) {
                tryToConnect(ipAddress);
            } else {
                Toast.makeText(this, "Invalid IP Address", Toast.LENGTH_SHORT).show();
                showIpDialog(); // Retry if no IP address was entered or invalid
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> {
            isDialogDisplayed = false;
            showIpDialog(); // Retry on cancel
        });

        builder.setCancelable(false); // Force the user to enter an IP address
        builder.show();
    }

    private boolean validateIpAddress(String ipAddress) {
        String ipPattern = "^((25[0-5]|2[0-4][0-9]|[0-1]?[0-9]{1,2})\\.){3}(25[0-5]|2[0-4][0-9]|[0-1]?[0-9]{1,2})$";
        return ipAddress.matches(ipPattern);
    }


    private void tryToConnect(String ipAddress) {
        new Thread(() -> {
            try {
                zmqContext = ZMQ.context(1);
                zmqSocket = zmqContext.socket(ZMQ.SUB);
                zmqSocket.subscribe(""); // Subscribe to all topics
                zmqSocket.connect("tcp://" + ipAddress + ":5556");

                System.out.println("Connected to: tcp://" + ipAddress + ":5556");

                ZMQ.Poller poller = zmqContext.poller(1);
                poller.register(zmqSocket, ZMQ.Poller.POLLIN);

                while (!Thread.currentThread().isInterrupted()) {
                    int events = poller.poll(5000); // Wait for events
                    if (events > 0 && poller.pollin(0)) {
                        String message = zmqSocket.recvStr(0);
                        if (message != null) {
                            System.out.println("Received message: " + message);
                            handleZmqMessage(message.trim());
                        }
                    } else {
                        System.out.println("No events detected. Waiting...");
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                uiHandler.post(() -> {
                    Toast.makeText(this, "Connection error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    showIpDialog(); // Retry connection
                });
            }
        }).start();
    }





    private void handleZmqMessage(String message) {
        uiHandler.post(() -> {
            if ("1".equals(message) && !isAnimation1Visible) {
                // Show animation 1
                animationView2.cancelAnimation();
                animationView2.setVisibility(LottieAnimationView.GONE);
                animationView1.setVisibility(LottieAnimationView.VISIBLE);
                animationView1.playAnimation();
                isAnimation1Visible = true;
            } else if ("0".equals(message) && isAnimation1Visible) {
                // Show animation 2
                animationView1.cancelAnimation();
                animationView1.setVisibility(LottieAnimationView.GONE);
                animationView2.setVisibility(LottieAnimationView.VISIBLE);
                animationView2.playAnimation();
                isAnimation1Visible = false;
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        synchronized (this) {
            if (zmqSocket != null) {
                zmqSocket.close();
                zmqSocket = null; // Set to null to avoid further access
            }
            if (zmqContext != null) {
                zmqContext.close();
                zmqContext = null; // Set to null to avoid further access
            }
        }
    }

}
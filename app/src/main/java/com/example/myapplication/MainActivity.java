package com.example.myapplication;

import android.Manifest;
import android.os.Build;
import android.os.Bundle;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.VideoView;
import android.widget.MediaController;
import android.os.Handler;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.airbnb.lottie.LottieAnimationView;

import org.zeromq.ZMQ;

public class MainActivity extends AppCompatActivity {

    private LottieAnimationView animationView1;
    private LottieAnimationView animationView2;
    private VideoView videoView;
    private Handler uiHandler;
    private boolean isAnimation1Visible = true;
    private boolean isVideoPlaying = false;
    private String lastMessage = "1"; // Always default to animation 1
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

        // Initialize views
        animationView1 = findViewById(R.id.animationView1);
        animationView2 = findViewById(R.id.animationView2);
        videoView = findViewById(R.id.videoView);

        // Configure VideoView with MediaController
        MediaController mediaController = new MediaController(this);
        mediaController.setAnchorView(videoView);
        videoView.setMediaController(mediaController);

        // Handle video completion to switch back to animation
        videoView.setOnCompletionListener(mp -> handleAnimationAfterVideo());

        uiHandler = new Handler();

        // Start with animationView1 visible
        animationView1.setVisibility(LottieAnimationView.VISIBLE);
        animationView1.playAnimation();
        animationView2.setVisibility(LottieAnimationView.GONE);
        videoView.setVisibility(VideoView.GONE);

        // Request necessary media permissions
        requestMediaPermissions();

        String ipAddress = getLocalIpAddress();
        Toast.makeText(this, "Your IP Address: " + ipAddress, Toast.LENGTH_LONG).show();

        // Prompt for IP Address
        showIpDialog();
    }

    private String getLocalIpAddress() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface networkInterface : interfaces) {
                List<InetAddress> addresses = Collections.list(networkInterface.getInetAddresses());
                for (InetAddress address : addresses) {
                    if (!address.isLoopbackAddress() && address instanceof java.net.Inet4Address) {
                        return address.getHostAddress(); // Returns IPv4 address
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "Unable to get IP address";
    }

    private void requestMediaPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ granular media permissions
            boolean hasVideoPermission = checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED;

            if (!hasVideoPermission) {
                requestPermissions(
                        new String[]{Manifest.permission.READ_MEDIA_VIDEO},
                        1
                );
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Older versions: Request READ_EXTERNAL_STORAGE
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        1
                );
            }
        }
    }

    private void showIpDialog() {
        if (isDialogDisplayed) return; // Prevent multiple dialogs
        isDialogDisplayed = true;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter IP Address");

        // Add IP Address display
        String localIpAddress = getLocalIpAddress();
        builder.setMessage("Your IP Address: " + localIpAddress);

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
            if ("1".equals(message) || "0".equals(message)) {
                lastMessage = message; // Update only if the message is "1" or "0"
            }
            if ("1".equals(message)) {
                stopVideoPlayback();
                // Show animation 1
                animationView2.cancelAnimation();
                animationView2.setVisibility(LottieAnimationView.GONE);
                animationView1.setVisibility(LottieAnimationView.VISIBLE);
                animationView1.playAnimation();
                isAnimation1Visible = true;
            } else if ("0".equals(message)) {
                stopVideoPlayback();
                // Show animation 2
                animationView1.cancelAnimation();
                animationView1.setVisibility(LottieAnimationView.GONE);
                animationView2.setVisibility(LottieAnimationView.VISIBLE);
                animationView2.playAnimation();
                isAnimation1Visible = false;
            } else if (message.startsWith("file://")) {
                // Play video
                stopAnimations();
                videoView.setVisibility(VideoView.VISIBLE);
                videoView.setVideoPath(message);
                videoView.start();
                isVideoPlaying = true;
            }
        });
    }

    private void handleAnimationAfterVideo() {
        if ("1".equals(lastMessage)) {
            animationView2.cancelAnimation();
            animationView2.setVisibility(LottieAnimationView.GONE);
            animationView1.setVisibility(LottieAnimationView.VISIBLE);
            animationView1.playAnimation();
            isAnimation1Visible = true;
        } else if ("0".equals(lastMessage)) {
            animationView1.cancelAnimation();
            animationView1.setVisibility(LottieAnimationView.GONE);
            animationView2.setVisibility(LottieAnimationView.VISIBLE);
            animationView2.playAnimation();
            isAnimation1Visible = false;
        }
        videoView.setVisibility(VideoView.GONE);
        isVideoPlaying = false;
    }

    private void stopVideoPlayback() {
        if (isVideoPlaying) {
            videoView.stopPlayback();
            videoView.setVisibility(VideoView.GONE);
            isVideoPlaying = false;
        }
    }

    private void stopAnimations() {
        animationView1.cancelAnimation();
        animationView2.cancelAnimation();
        animationView1.setVisibility(LottieAnimationView.GONE);
        animationView2.setVisibility(LottieAnimationView.GONE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 1) {
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Permission denied: " + permissions[i], Toast.LENGTH_SHORT).show();
                }
            }
        }
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

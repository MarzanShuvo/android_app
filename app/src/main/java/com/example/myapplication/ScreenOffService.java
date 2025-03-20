package com.example.myapplication;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

public class ScreenOffService extends AccessibilityService {
    private static ScreenOffService instance;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    public static void turnOffScreen() {
        if (instance != null) {
            instance.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
        }
    }
}

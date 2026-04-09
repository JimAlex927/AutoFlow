package com.auto.master.floatwin;

import android.content.Context;
import android.view.WindowManager;

import java.io.File;

/**
 * Interface defining common methods needed by extracted helper classes.
 * FloatWindowService implements this to provide access to its resources.
 */
public interface FloatWindowHost {

    /**
     * Get the Android Context
     */
    Context getContext();

    /**
     * Get the WindowManager for managing floating windows
     */
    WindowManager getWindowManager();

    /**
     * Convert dp to pixels
     */
    int dp(int dpValue);

    /**
     * Get the root directory for projects
     */
    File getProjectsRootDir();

    /**
     * Show a toast message
     */
    void showToast(String message);

    /**
     * Get the current project directory
     */
    File getCurrentProjectDir();

    /**
     * Get the current task directory
     */
    File getCurrentTaskDir();
}

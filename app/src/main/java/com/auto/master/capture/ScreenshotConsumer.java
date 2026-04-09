package com.auto.master.capture;

import org.opencv.core.Mat;

public interface ScreenshotConsumer {

    void onScreenshotCaptured(Mat mat);
}

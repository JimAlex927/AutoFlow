package com.auto.master.utils;

import org.opencv.core.Point;

public class MatchResult {
    private Point location;     // 匹配到的左上角坐标
    private double confidence;  // 相似度（0~1）

    public MatchResult(Point location, double confidence) {
        this.location = location;
        this.confidence = confidence;
    }

    public Point getLocation() {
        return location;
    }

    public double getConfidence() {
        return confidence;
    }

    @Override
    public String toString() {
        return "MatchResult{" +
                "location=" + location +
                ", confidence=" + confidence +
                '}';
    }
}
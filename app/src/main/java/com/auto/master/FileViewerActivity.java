package com.auto.master;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.view.ViewGroup;
import android.view.View;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Simple file viewer: images or text. */
public class FileViewerActivity extends Activity {
    private ImageView imageView;
    private TextView textView;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // simple layout with both views; one hidden at a time
        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        root.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        imageView = new ImageView(this);
        imageView.setLayoutParams(new FrameLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        textView = new TextView(this);
        textView.setLayoutParams(new FrameLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        textView.setPadding(16,16,16,16);
        textView.setTextIsSelectable(true);
        ScrollView sv = new ScrollView(this);
        sv.addView(textView);
        root.addView(imageView);
        root.addView(sv);
        setContentView(root);

        String path = getIntent().getStringExtra("path");
        if (path == null) return;
        File f = new File(path);
        String lower = path.toLowerCase();
        if (f.isFile() && (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif") || lower.endsWith(".webp"))) {
            // show image
            try {
                Bitmap bmp = BitmapFactory.decodeFile(f.getAbsolutePath());
                imageView.setImageBitmap(bmp);
                imageView.setVisibility(View.VISIBLE);
                sv.setVisibility(View.GONE);
            } catch (Exception e) {
                textView.setText("无法加载图片: " + e.getMessage());
                imageView.setVisibility(View.GONE);
                sv.setVisibility(View.VISIBLE);
            }
        } else {
            // show text
            try {
                byte[] bytes = Files.readAllBytes(f.toPath());
                String content = new String(bytes, StandardCharsets.UTF_8);
                textView.setText(content);
                textView.setVisibility(View.VISIBLE);
                imageView.setVisibility(View.GONE);
                sv.setVisibility(View.VISIBLE);
            } catch (IOException e) {
                textView.setText("无法读取文件: " + e.getMessage());
                textView.setVisibility(View.VISIBLE);
                imageView.setVisibility(View.GONE);
                sv.setVisibility(View.VISIBLE);
            }
        }
    }

    // simple layout params helper
    static class FrameLayoutParams extends android.widget.FrameLayout.LayoutParams {
        FrameLayoutParams(int w, int h) { super(w, h); }
    }
}

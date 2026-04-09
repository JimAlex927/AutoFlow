package com.auto.master.auto;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
public class CropCaptureActivity extends Activity {

    private Bitmap bmp;
    private CropSelectionView cropView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 取走所有权：Activity 自己负责 recycle
        bmp = BitmapStore.take();
        if (bmp == null || bmp.isRecycled()) {
            finish();
            return;
        }

        String defaultName = getIntent().getStringExtra("saveName");
        if (defaultName == null || defaultName.trim().isEmpty()) {
            defaultName = "region_" + System.currentTimeMillis();
        }

        cropView = new CropSelectionView(this, bmp);

        // 根容器
        FrameLayout root = new FrameLayout(this);
        root.addView(cropView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        // 底部栏（输入框 + 取消/确定）
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(dp(12), dp(10), dp(12), dp(10));
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0xCC000000);

        EditText nameInput = new EditText(this);
        nameInput.setText(defaultName);
        nameInput.setSingleLine(true);
        nameInput.setHint("图片名");
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT);
        nameInput.setTextColor(Color.WHITE);
        nameInput.setHintTextColor(0x80FFFFFF);
        nameInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(40)});

        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        );
        bar.addView(nameInput, nameLp);

        Button btnCancel = new Button(this);
        btnCancel.setText("取消");

        Button btnOk = new Button(this);
        btnOk.setText("确定");

        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        btnLp.leftMargin = dp(10);

        bar.addView(btnCancel, btnLp);
        bar.addView(btnOk, btnLp);

        FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        barLp.gravity = Gravity.BOTTOM;

        root.addView(bar, barLp);

        // 取消：直接退出，不保存
        btnCancel.setOnClickListener(v -> finish());

        // 确定：裁剪 + 保存
        btnOk.setOnClickListener(v -> {
            Rect r = cropView.getSelectedRectOnBitmap();
            if (r == null) {
                Toast.makeText(this, "请先拖动选择区域", Toast.LENGTH_SHORT).show();
                return;
            }

            // 读取用户输入
            String raw = nameInput.getText() != null ? nameInput.getText().toString() : "";
            String base = sanitizeFileName(raw);

            if (base.isEmpty()) {
                Toast.makeText(this, "请输入图片名", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                Bitmap cropped = Bitmap.createBitmap(bmp, r.left, r.top, r.width(), r.height());

                File dir = new File(getExternalFilesDir(null), "templates");
                dir.mkdirs();

                // 自动补 .png
                String finalName = base.endsWith(".png") ? base : (base + ".png");
                File out = new File(dir, finalName);

                // 防止覆盖：存在则自动加 _2, _3...
                out = avoidOverwrite(out);

                try (FileOutputStream fos = new FileOutputStream(out)) {
                    cropped.compress(Bitmap.CompressFormat.PNG, 100, fos);
                }

                cropped.recycle();
                Toast.makeText(this, "已保存: " + out.getAbsolutePath(), Toast.LENGTH_SHORT).show();
                finish();

            } catch (Exception e) {
                Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        setContentView(root);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bmp != null && !bmp.isRecycled()) bmp.recycle();
        bmp = null;
        cropView = null;
    }

    // ----------------- helpers -----------------

    private int dp(int v) {
        return (int) (getResources().getDisplayMetrics().density * v + 0.5f);
    }

    /** 清理非法文件名字符（Windows/Android 常见非法字符） */
    private String sanitizeFileName(String name) {
        name = name == null ? "" : name.trim();

        // 替换非法字符：\/:*?"<>| 以及控制字符
        name = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");

        // 空白压缩为下划线
        name = name.replaceAll("\\s+", "_");

        // 避免只剩下点/下划线
        if (name.equals(".") || name.equals("..")) return "";

        return name;
    }

    /** 防止覆盖：如果同名存在则自动加序号 */
    private File avoidOverwrite(File file) {
        if (!file.exists()) return file;

        String n = file.getName();
        int dot = n.lastIndexOf('.');
        String base = dot >= 0 ? n.substring(0, dot) : n;
        String ext = dot >= 0 ? n.substring(dot) : "";

        File dir = file.getParentFile();
        for (int i = 2; i < 1000; i++) {
            File f = new File(dir, base + "_" + i + ext);
            if (!f.exists()) return f;
        }
        return file;
    }
}
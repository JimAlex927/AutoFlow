package com.auto.master;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class FileEditorActivity extends AppCompatActivity {
    private EditText contentEditText;
    private Button saveButton;
    private Button cancelButton;
    private File currentFile;
    private String originalContent;
    
    public static final String EXTRA_FILE_PATH = "file_path";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_editor);
        
        contentEditText = findViewById(R.id.content_edit_text);
        saveButton = findViewById(R.id.save_button);
        cancelButton = findViewById(R.id.cancel_button);
        
        // 获取文件路径
        String filePath = getIntent().getStringExtra(EXTRA_FILE_PATH);
        if (filePath == null) {
            Toast.makeText(this, "文件路径无效", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        currentFile = new File(filePath);
        if (!currentFile.exists()) {
            Toast.makeText(this, "文件不存在", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // 设置标题
        setTitle("编辑文件: " + currentFile.getName());
        
        // 加载文件内容
        loadFileContent();
        
        // 设置按钮监听器
        saveButton.setOnClickListener(v -> saveFile());
        cancelButton.setOnClickListener(v -> {
            if (hasChanges()) {
                showUnsavedChangesDialog();
            } else {
                finish();
            }
        });
    }
    
    private void loadFileContent() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                byte[] bytes = Files.readAllBytes(currentFile.toPath());
                originalContent = new String(bytes, StandardCharsets.UTF_8);
                contentEditText.setText(originalContent);
            }
        } catch (IOException e) {
            Toast.makeText(this, "读取文件失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            finish();
        }
    }
    
    private void saveFile() {
        try {
            String content = contentEditText.getText().toString();
            try (FileOutputStream fos = new FileOutputStream(currentFile)) {
                fos.write(content.getBytes(StandardCharsets.UTF_8));
            }
            originalContent = content;
            Toast.makeText(this, "文件保存成功", Toast.LENGTH_SHORT).show();
            
            // 返回结果给调用者
            Intent resultIntent = new Intent();
            resultIntent.putExtra("file_saved", true);
            setResult(RESULT_OK, resultIntent);
            
        } catch (IOException e) {
            Toast.makeText(this, "保存文件失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private boolean hasChanges() {
        String currentContent = contentEditText.getText().toString();
        return !currentContent.equals(originalContent);
    }
    
    private void showUnsavedChangesDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("未保存的更改")
            .setMessage("您有未保存的更改，确定要退出吗？")
            .setPositiveButton("保存并退出", (dialog, which) -> {
                saveFile();
                finish();
            })
            .setNegativeButton("直接退出", (dialog, which) -> finish())
            .setNeutralButton("取消", null)
            .show();
    }
    
    @Override
    public void onBackPressed() {
        if (hasChanges()) {
            showUnsavedChangesDialog();
        } else {
            super.onBackPressed();
        }
    }
}
package com.auto.master;

import android.content.Intent;
import android.os.Bundle;
import android.util.AtomicFile;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

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
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBackRequest();
            }
        });
    }
    
    private void loadFileContent() {
        try (FileInputStream input = new FileInputStream(currentFile);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            originalContent = output.toString(StandardCharsets.UTF_8.name());
            contentEditText.setText(originalContent);
        } catch (IOException e) {
            Toast.makeText(this, "读取文件失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            finish();
        }
    }
    
    private boolean saveFile() {
        AtomicFile atomicFile = new AtomicFile(currentFile);
        FileOutputStream output = null;
        try {
            String content = contentEditText.getText().toString();
            output = atomicFile.startWrite();
            output.write(content.getBytes(StandardCharsets.UTF_8));
            atomicFile.finishWrite(output);
            originalContent = content;
            Toast.makeText(this, "文件保存成功", Toast.LENGTH_SHORT).show();
            
            // 返回结果给调用者
            Intent resultIntent = new Intent();
            resultIntent.putExtra("file_saved", true);
            setResult(RESULT_OK, resultIntent);
            return true;
        } catch (IOException e) {
            if (output != null) {
                atomicFile.failWrite(output);
            }
            Toast.makeText(this, "保存文件失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return false;
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
                if (saveFile()) {
                    finish();
                }
            })
            .setNegativeButton("直接退出", (dialog, which) -> finish())
            .setNeutralButton("取消", null)
            .show();
    }
    
    private void handleBackRequest() {
        if (hasChanges()) {
            showUnsavedChangesDialog();
        } else {
            finish();
        }
    }
}

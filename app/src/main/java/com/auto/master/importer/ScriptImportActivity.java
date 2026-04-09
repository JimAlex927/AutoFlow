package com.auto.master.importer;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScriptImportActivity extends Activity {
    private final ExecutorService single = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Uri uri = extractUri(getIntent());
        if (uri == null) {
            Toast.makeText(this, "未检测到可导入脚本包", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        single.execute(() -> {
            try {
                ScriptPackageManager.ImportResult res = ScriptPackageManager.importFromUri(this, uri);
                runOnUiThread(() -> {
                    Toast.makeText(this,
                            "导入完成: 项目 " + res.importedProjects + " 个, 跳过 " + res.skippedEntries + " 项",
                            Toast.LENGTH_LONG).show();
                    notifyServiceRefresh();
                    finish();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "导入失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        });
    }

    private Uri extractUri(Intent intent) {
        if (intent == null) {
            return null;
        }
        Uri data = intent.getData();
        if (data != null) {
            return data;
        }
        Object stream = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (stream instanceof Uri) {
            return (Uri) stream;
        }
        return null;
    }

    private void notifyServiceRefresh() {
        try {
            Intent serviceIntent = new Intent(this, com.auto.master.floatwin.FloatWindowService.class);
            serviceIntent.setAction("com.auto.master.floatwin.action.REFRESH_PROJECTS");
            startService(serviceIntent);
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        single.shutdownNow();
    }
}

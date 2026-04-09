package com.auto.master.importer;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Transparent activity that launches the system file picker, imports the chosen zip
 * using ScriptPackageManager, then notifies FloatWindowService to refresh.
 */
public class ProjectImportPickerActivity extends Activity {

    private static final int REQUEST_PICK = 1001;
    private final ExecutorService single = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
        pick.setType("*/*");
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(pick, "选择项目包 (.zip)"), REQUEST_PICK);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK) {
            return;
        }
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            finish();
            return;
        }
        Uri uri = data.getData();
        single.execute(() -> {
            try {
                ScriptPackageManager.ImportResult res = ScriptPackageManager.importFromUri(this, uri);
                runOnUiThread(() -> {
                    String msg = "导入完成: " + res.importedProjects + " 个项目";
                    if (res.skippedEntries > 0) {
                        msg += "，跳过 " + res.skippedEntries + " 项";
                    }
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
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

package com.auto.master;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import androidx.annotation.Nullable;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import com.auto.master.capture.ScreenCapture;
import com.auto.master.Task.Operation.MetaOperation;
import com.auto.master.Task.Operation.ClickOperation;
import com.auto.master.Task.Operation.DelayOperation;
import com.auto.master.Task.Project.Project;
import com.auto.master.Task.Task;
import com.auto.master.Task.model.Point;
import com.auto.master.auto.ScriptRunner;

/**
 * Grid page using RecyclerView for stable and fast performance.
 */
public class GridPageActivity extends AppCompatActivity implements GridPageAdapter.OnItemClickListener {

    private static final int REQ_MEDIA_PROJECTION = 1001;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_grid_page);

        RecyclerView rv = findViewById(R.id.recyclerView);
        rv.setLayoutManager(new GridLayoutManager(this, 3));

        List<GridPageAdapter.GridItem> items = new ArrayList<>();
        items.add(new GridPageAdapter.GridItem("打开辅助功能设置", GridPageAdapter.GridAction.OPEN_ACCESSIBILITY));
        items.add(new GridPageAdapter.GridItem("请求全屏截图授权", GridPageAdapter.GridAction.REQUEST_SNAPSHOT));
        items.add(new GridPageAdapter.GridItem("立刻截图", GridPageAdapter.GridAction.TAKE_SNAPSHOT));
        items.add(new GridPageAdapter.GridItem("运行示例 JSON 脚本", GridPageAdapter.GridAction.RUN_SCRIPT));
        items.add(new GridPageAdapter.GridItem("返回主界面", GridPageAdapter.GridAction.GO_BACK));

        GridPageAdapter adapter = new GridPageAdapter(items, this);
        rv.setAdapter(adapter);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    @Override
    public void onItemClick(GridPageAdapter.GridAction action) {
        switch (action) {
            case OPEN_ACCESSIBILITY:
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                break;
            case REQUEST_SNAPSHOT:
                startActivityForResult(ScreenCapture.createProjectionIntent(this), REQ_MEDIA_PROJECTION);
                break;
            case TAKE_SNAPSHOT:
                if (ScreenCapture.hasProjectionPermission()) {
                    ScreenCapture.captureNow(this, ScreenCapture.Method.MEDIA_PROJECTION, "gridshot.png");
                } else {
                    Toast.makeText(this, "请先授权 MediaProjection", Toast.LENGTH_SHORT).show();
                }
                break;
            case RUN_SCRIPT:
                Toast.makeText(this, "运行示例脚本 (待实现集成)", Toast.LENGTH_SHORT).show();
                break;
            case GO_BACK:
                finish();
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_MEDIA_PROJECTION) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                ScreenCapture.saveProjectionPermission(resultCode, data);
            }
        }
    }
}

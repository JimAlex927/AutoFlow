package com.auto.master.ui;

import android.app.Activity;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

/** Simple UI manager for the top-right action button used to add items. */
public class UIManager {
    public enum Level { PROJECT, TASK, OPERATION; }

    private final Activity activity;
    private final Button topButton;
    private OnActionProvider actionProvider;

    public UIManager(Activity activity, int rootLayoutId, int buttonId) {
        this.activity = activity;
        Button b = activity.findViewById(buttonId);
        this.topButton = b != null ? b : createFallbackButton(rootLayoutId);
        ensureButtonVisible();
    }

    private Button createFallbackButton(int rootLayoutId) {
        View root = activity.findViewById(rootLayoutId);
        if (root instanceof android.widget.LinearLayout) {
            Button b = new Button(activity);
            b.setId(android.R.id.button1);
            b.setText("新增");
            ((android.widget.LinearLayout) root).addView(b, 0);
            return b;
        }
        return null;
    }

    public void setLevel(Level level) {
        if (topButton == null) return;
        switch (level) {
            case PROJECT: topButton.setText("新增项目"); break;
            case TASK: topButton.setText("新增 Task"); break;
            case OPERATION: topButton.setText("新增操作"); break;
        }
    }

    public void setActionProvider(OnActionProvider provider) {
        this.actionProvider = provider;
        if (topButton != null) {
            topButton.setOnClickListener(v -> {
                if (provider != null) provider.onAction();
            });
        }
    }

    public void ensureButtonVisible() {
        if (topButton != null) topButton.setVisibility(View.VISIBLE);
    }

    public interface OnActionProvider {
        void onAction();
    }
}

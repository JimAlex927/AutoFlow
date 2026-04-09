package com.auto.master.auto;

import android.content.Context;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import com.auto.master.capture.FileUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;

public final class NodeDumpHelper {

    private static final String TAG = "NodeDump";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private NodeDumpHelper() {}

    public static void dumpToFile(Context ctx, String fileName) {


        if (!fileName.endsWith(".json")) fileName = fileName + ".json";
        final String effectiveFileName = fileName;
        MAIN.post(() -> {
            try {
                AutoAccessibilityService svc = AutoAccessibilityService.get();
                if (svc == null) {
                    Log.e(TAG, "Accessibility not connected");
                    return;
                }
                AccessibilityNodeInfo root = svc.getRootInActiveWindow();
                if (root == null) {
                    Log.e(TAG, "Root is null");
                    return;
                }

                JSONObject json = nodeToJson(root, 0);
                root.recycle();

                File out = FileUtil.makeCaptureFile(ctx, effectiveFileName);

                if (writeText(out, json.toString(2))) {
                    Log.d(TAG, "Saved node dump: " + out.getAbsolutePath());
                }
            } catch (Exception e) {
                Log.e(TAG, "dump error", e);
            }
        });
    }

    private static JSONObject nodeToJson(AccessibilityNodeInfo n, int depth) throws Exception {
        JSONObject o = new JSONObject();
        o.put("class", safe(n.getClassName()));
        o.put("text", safe(n.getText()));
        o.put("desc", safe(n.getContentDescription()));
        o.put("resId", safe(n.getViewIdResourceName()));
        o.put("clickable", n.isClickable());
        o.put("enabled", n.isEnabled());
        o.put("focusable", n.isFocusable());

        Rect r = new Rect();
        n.getBoundsInScreen(r);
        o.put("bounds", new JSONObject()
                .put("l", r.left).put("t", r.top).put("r", r.right).put("b", r.bottom));

        JSONArray children = new JSONArray();
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo c = n.getChild(i);
            if (c != null) {
                children.put(nodeToJson(c, depth + 1));
                c.recycle();
            }
        }
        o.put("children", children);
        return o;
    }

    private static String safe(CharSequence cs) {
        return cs == null ? "" : cs.toString();
    }

    private static boolean writeText(File file, String s) {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
            fos.write(s.getBytes("UTF-8"));
            fos.flush();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "writeText error", e);
            return false;
        } finally {
            try { if (fos != null) fos.close(); } catch (Exception ignored) {}
        }
    }
}

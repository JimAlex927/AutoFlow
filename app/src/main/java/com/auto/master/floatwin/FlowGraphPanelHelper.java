package com.auto.master.floatwin;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.auto.master.R;
import com.auto.master.Task.Operation.MetaOperation;
import com.auto.master.floatwin.adapter.FlowNodeAdapter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class FlowGraphPanelHelper {

    interface Host {
        Context getContext();

        WindowManager getWindowManager();

        Handler getUiHandler();

        int dp(int value);

        void adaptPanelSizeToScreen(WindowManager.LayoutParams lp, int desiredWidthDp, int desiredHeightDp);

        int getSharedPanelX();

        int getSharedPanelY();

        void rememberSharedPanelPosition(@Nullable WindowManager.LayoutParams lp);

        void safeRemoveView(View view);

        @Nullable
        File getCurrentTaskDir();

        JSONArray readOperationsArray() throws Exception;

        boolean writeOperationsArray(JSONArray jsonArray, String successText);

        String getOperationTypeName(int type);

        void showToast(String message);

        void editOperationFromFlow(String name, String id, String type, int order);

        void pickOperationId(String title, @Nullable String excludeId, Consumer<String> listener);
    }

    private final Host host;
    private View panelView;
    private WindowManager.LayoutParams panelLp;
    private Runnable refreshAction;
    private String selectedNodeId;
    private FlowGraphView currentGraphView;

    FlowGraphPanelHelper(Host host) {
        this.host = host;
    }

    void onDestroy() {
        closePanel();
        currentGraphView = null;
    }

    void showFlowGraphDialog() {
        showPanel();
    }

    void refreshOpenPanel(@Nullable String preferredNodeId) {
        if (!TextUtils.isEmpty(preferredNodeId)) {
            selectedNodeId = preferredNodeId;
        }
        if (panelView == null || refreshAction == null) {
            return;
        }
        host.getUiHandler().post(refreshAction);
    }

    void highlightOperation(@Nullable String operationId) {
        if (currentGraphView == null) {
            return;
        }
        host.getUiHandler().post(() -> {
            if (currentGraphView != null) {
                currentGraphView.setHighlightedNodeId(operationId);
            }
        });
    }

    void clearHighlight() {
        highlightOperation(null);
    }

    private void showPanel() {
        if (panelView != null) {
            try {
                host.getWindowManager().removeView(panelView);
            } catch (Exception ignored) {
            }
            panelView = null;
        }

        final List<FlowNodeAdapter.FlowNodeItem> nodes = getCurrentTaskFlowNodes();
        panelView = LayoutInflater.from(host.getContext()).inflate(R.layout.window_flow_graph_panel, null);

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        panelLp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        panelLp.gravity = Gravity.TOP | Gravity.START;
        host.adaptPanelSizeToScreen(panelLp, 340, 520);
        panelLp.x = host.getSharedPanelX();
        panelLp.y = host.getSharedPanelY();
        host.getWindowManager().addView(panelView, panelLp);

        View dragHeader = panelView.findViewById(R.id.drag_header);
        View resizeHandle = panelView.findViewById(R.id.resize_handle);
        dragHeader.setOnTouchListener(new DragTouchListener(panelLp, host.getWindowManager(), panelView, (FloatWindowService) host.getContext(), true));
        resizeHandle.setOnTouchListener(new PanelResizeTouchListener(
                panelLp,
                host.getWindowManager(),
                panelView,
                (FloatWindowService) host.getContext(),
                host.dp(300),
                host.dp(400)
        ));

        FlowGraphView graphView = panelView.findViewById(R.id.flow_graph_view);
        graphView.setInteractionReadOnly(true);
        TextView selectedView = panelView.findViewById(R.id.tv_flow_selected);
        TextView editButton = panelView.findViewById(R.id.btn_flow_edit_node);
        TextView setNextButton = panelView.findViewById(R.id.btn_flow_set_next);
        TextView setFallbackButton = panelView.findViewById(R.id.btn_flow_set_fallback);
        TextView clearButton = panelView.findViewById(R.id.btn_flow_clear_conn);

        final FlowNodeAdapter.FlowNodeItem[] selected = {null};
        final Runnable[] render = new Runnable[1];
        render[0] = () -> {
            List<FlowNodeAdapter.FlowNodeItem> latest = getCurrentTaskFlowNodes();
            nodes.clear();
            nodes.addAll(latest);
            graphView.setNodes(getCurrentTaskGraphNodes());
            if (!TextUtils.isEmpty(selectedNodeId)) {
                FlowNodeAdapter.FlowNodeItem refreshed = findFlowNodeById(nodes, selectedNodeId);
                selected[0] = refreshed;
                graphView.setSelectedNodeId(refreshed == null ? null : refreshed.id);
                selectedNodeId = refreshed == null ? null : refreshed.id;
            }
            updateFlowSelectionUi(selected[0], selectedView, editButton, setNextButton, setFallbackButton);
        };
        refreshAction = render[0];

        graphView.setOnNodeSelectListener(node -> {
            selected[0] = node == null ? null : findFlowNodeById(nodes, node.id);
            selectedNodeId = selected[0] == null ? null : selected[0].id;
            updateFlowSelectionUi(selected[0], selectedView, editButton, setNextButton, setFallbackButton);
        });

        graphView.setOnNodeDoubleTapListener(node -> {
            if (node == null) {
                return;
            }
            host.editOperationFromFlow(node.name, node.id, node.type, node.order - 1);
        });
        graphView.setOnConnectListener(null);

        currentGraphView = graphView;
        panelView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                if (currentGraphView == graphView) {
                    currentGraphView = null;
                }
            }
        });

        panelView.findViewById(R.id.btn_flow_close).setOnClickListener(v -> closePanel());
        panelView.findViewById(R.id.btn_flow_back).setOnClickListener(v -> closePanel());
        panelView.findViewById(R.id.btn_flow_center).setOnClickListener(v -> graphView.resetViewTransform());
        panelView.findViewById(R.id.btn_flow_auto_layout).setOnClickListener(v -> {
            graphView.autoArrange();
            host.showToast("已自动排列");
        });

        editButton.setOnClickListener(v -> {
            if (selected[0] == null) {
                host.showToast("请先点击选中一个节点");
                return;
            }
            selectedNodeId = selected[0].id;
            host.editOperationFromFlow(selected[0].name, selected[0].id, selected[0].type, selected[0].order - 1);
        });

        setNextButton.setOnClickListener(v -> {
            if (selected[0] == null) {
                host.showToast("请先选中节点");
                return;
            }
            host.pickOperationId("选择主线下一节点", null, pickedId -> {
                if (updateFlowConnection(selected[0].id, MetaOperation.NEXT_OPERATION_ID, pickedId)) {
                    render[0].run();
                    host.showToast("主线已设置");
                }
            });
        });

        setFallbackButton.setOnClickListener(v -> {
            if (selected[0] == null) {
                host.showToast("请先选中节点");
                return;
            }
            host.pickOperationId("选择分支节点", null, pickedId -> {
                if (updateFlowConnection(selected[0].id, MetaOperation.FALLBACKOPERATIONID, pickedId)) {
                    render[0].run();
                    host.showToast("分支已设置");
                }
            });
        });

        clearButton.setOnClickListener(v -> {
            if (selected[0] == null) {
                host.showToast("请先选中节点");
                return;
            }
            boolean clearedNext = updateFlowConnection(selected[0].id, MetaOperation.NEXT_OPERATION_ID, "");
            boolean clearedFall = updateFlowConnection(selected[0].id, MetaOperation.FALLBACKOPERATIONID, "");
            if (clearedNext || clearedFall) {
                render[0].run();
                host.showToast("已清除连线");
            }
        });

        render[0].run();
    }

    private void closePanel() {
        if (panelView != null) {
            host.rememberSharedPanelPosition(panelLp);
            host.safeRemoveView(panelView);
            panelView = null;
            panelLp = null;
            refreshAction = null;
            selectedNodeId = null;
        }
    }

    private List<FlowNodeAdapter.FlowNodeItem> getCurrentTaskFlowNodes() {
        List<FlowNodeAdapter.FlowNodeItem> nodes = new ArrayList<>();
        if (host.getCurrentTaskDir() == null) {
            return nodes;
        }
        try {
            JSONArray operations = host.readOperationsArray();
            for (int i = 0; i < operations.length(); i++) {
                JSONObject op = operations.optJSONObject(i);
                if (op == null) {
                    continue;
                }
                String id = op.optString("id", "");
                String name = op.optString("name", "未命名");
                int typeInt = op.optInt("type", -1);
                String type = host.getOperationTypeName(typeInt);
                JSONObject inputMap = op.optJSONObject("inputMap");
                String nextId;
                String fallbackId;
                if (typeInt == 16) {
                    nextId = inputMap == null ? "" : inputMap.optString(MetaOperation.LOOP_BODY_NEXT, "");
                    fallbackId = inputMap == null ? "" : inputMap.optString(MetaOperation.LOOP_EXIT_NEXT, "");
                } else if (typeInt == 10 || typeInt == 15) {
                    nextId = inputMap == null ? "" : inputMap.optString(MetaOperation.BRANCH_DEFAULT_NEXT, "");
                    fallbackId = firstBranchTarget(inputMap);
                } else {
                    nextId = inputMap == null ? "" : inputMap.optString(MetaOperation.NEXT_OPERATION_ID, "");
                    fallbackId = inputMap == null ? "" : inputMap.optString(MetaOperation.FALLBACKOPERATIONID, "");
                }
                nodes.add(new FlowNodeAdapter.FlowNodeItem(i + 1, id, name, type, nextId, fallbackId));
            }
        } catch (Exception ignored) {
        }
        return nodes;
    }

    private List<FlowGraphView.Node> getCurrentTaskGraphNodes() {
        List<FlowGraphView.Node> nodes = new ArrayList<>();
        if (host.getCurrentTaskDir() == null) {
            return nodes;
        }
        try {
            JSONArray operations = host.readOperationsArray();
            for (int i = 0; i < operations.length(); i++) {
                JSONObject op = operations.optJSONObject(i);
                if (op == null) {
                    continue;
                }
                FlowGraphView.Node node = new FlowGraphView.Node();
                node.order = i + 1;
                node.id = op.optString("id", "");
                node.name = op.optString("name", "未命名");
                node.typeCode = op.optInt("type", -1);
                node.type = host.getOperationTypeName(node.typeCode);

                JSONObject inputMap = op.optJSONObject("inputMap");
                int typeInt = node.typeCode;
                if (typeInt == 16) {
                    node.nextId = inputMap == null ? "" : inputMap.optString(MetaOperation.LOOP_BODY_NEXT, "");
                    node.fallbackId = inputMap == null ? "" : inputMap.optString(MetaOperation.LOOP_EXIT_NEXT, "");
                } else if (typeInt == 10 || typeInt == 15) {
                    node.nextId = inputMap == null ? "" : inputMap.optString(MetaOperation.BRANCH_DEFAULT_NEXT, "");
                    appendBranchEdges(node, inputMap);
                } else {
                    node.nextId = inputMap == null ? "" : inputMap.optString(MetaOperation.NEXT_OPERATION_ID, "");
                    node.fallbackId = inputMap == null ? "" : inputMap.optString(MetaOperation.FALLBACKOPERATIONID, "");
                }
                nodes.add(node);
            }
        } catch (Exception ignored) {
        }
        return nodes;
    }

    private String firstBranchTarget(@Nullable JSONObject inputMap) {
        if (inputMap == null) {
            return "";
        }
        JSONArray rules = inputMap.optJSONArray(MetaOperation.BRANCH_RULES);
        if (rules == null) {
            return "";
        }
        for (int i = 0; i < rules.length(); i++) {
            JSONObject rule = rules.optJSONObject(i);
            if (rule == null) {
                continue;
            }
            String target = rule.optString("nextOperationId", "");
            if (TextUtils.isEmpty(target)) {
                target = rule.optString("next", "");
            }
            if (TextUtils.isEmpty(target)) {
                target = rule.optString("target", "");
            }
            if (!TextUtils.isEmpty(target)) {
                return target;
            }
        }
        return "";
    }

    private void appendBranchEdges(FlowGraphView.Node node, @Nullable JSONObject inputMap) {
        if (node == null || inputMap == null) {
            return;
        }
        JSONArray rules = inputMap.optJSONArray(MetaOperation.BRANCH_RULES);
        if (rules == null) {
            return;
        }
        List<String> targets = new ArrayList<>();
        for (int i = 0; i < rules.length(); i++) {
            JSONObject rule = rules.optJSONObject(i);
            if (rule == null) {
                continue;
            }
            String target = rule.optString("nextOperationId", "");
            if (TextUtils.isEmpty(target)) {
                target = rule.optString("next", "");
            }
            if (TextUtils.isEmpty(target)) {
                target = rule.optString("target", "");
            }
            if (!TextUtils.isEmpty(target)) {
                targets.add(target);
            }
        }
        for (int i = 0; i < targets.size(); i++) {
            FlowGraphView.Node.Edge edge = new FlowGraphView.Node.Edge();
            edge.toId = targets.get(i);
            edge.kind = "branch";
            edge.fromFallbackPort = false;
            edge.sourceSlotIndex = i;
            edge.sourceSlotCount = targets.size();
            node.extraEdges.add(edge);
        }
    }

    @Nullable
    private FlowNodeAdapter.FlowNodeItem findFlowNodeById(List<FlowNodeAdapter.FlowNodeItem> nodes, String nodeId) {
        if (nodes == null || TextUtils.isEmpty(nodeId)) {
            return null;
        }
        for (FlowNodeAdapter.FlowNodeItem item : nodes) {
            if (TextUtils.equals(nodeId, item.id)) {
                return item;
            }
        }
        return null;
    }

    private void updateFlowSelectionUi(
            @Nullable FlowNodeAdapter.FlowNodeItem selected,
            TextView selectedView,
            TextView editButton,
            TextView setNextButton,
            TextView setFallbackButton
    ) {
        boolean hasSelection = selected != null;
        if (!hasSelection) {
            selectedView.setText("未选中节点");
        } else {
            selectedView.setText("已选中: " + selected.order + " | " + selected.name + " | " + selected.id);
        }
        editButton.setEnabled(hasSelection);
        setNextButton.setEnabled(hasSelection);
        setFallbackButton.setEnabled(hasSelection);
        editButton.setAlpha(hasSelection ? 1f : 0.45f);
        setNextButton.setAlpha(hasSelection ? 1f : 0.45f);
        setFallbackButton.setAlpha(hasSelection ? 1f : 0.45f);
    }

    private boolean updateFlowConnection(String operationId, String key, String targetId) {
        if (host.getCurrentTaskDir() == null || TextUtils.isEmpty(operationId) || TextUtils.isEmpty(key)) {
            return false;
        }
        try {
            JSONArray array = host.readOperationsArray();
            boolean found = false;
            for (int i = 0; i < array.length(); i++) {
                JSONObject op = array.optJSONObject(i);
                if (op == null || !TextUtils.equals(operationId, op.optString("id", ""))) {
                    continue;
                }
                JSONObject inputMap = op.optJSONObject("inputMap");
                if (inputMap == null) {
                    inputMap = new JSONObject();
                }
                if (TextUtils.isEmpty(targetId)) {
                    inputMap.remove(key);
                } else {
                    inputMap.put(key, targetId);
                }
                op.put("inputMap", inputMap);
                found = true;
                break;
            }
            return found && host.writeOperationsArray(array, "");
        } catch (Exception ignored) {
            return false;
        }
    }
}

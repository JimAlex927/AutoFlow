package com.auto.master.scheduler;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.auto.master.R;

import java.util.List;

public class TriggerAdapter extends RecyclerView.Adapter<TriggerAdapter.ViewHolder> {

    public interface OnTriggerAction {
        void onToggle(AppNotificationTrigger trigger, boolean enabled);
        void onDelete(AppNotificationTrigger trigger);
    }

    private final List<AppNotificationTrigger> triggers;
    private final OnTriggerAction listener;

    public TriggerAdapter(List<AppNotificationTrigger> triggers, OnTriggerAction listener) {
        this.triggers = triggers;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_trigger, parent, false);
        return new ViewHolder(v);
    }

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppNotificationTrigger t = triggers.get(position);

        if (t.triggerType == AppNotificationTrigger.TYPE_APP_LAUNCH) {
            holder.typeBadge.setText("App");
            holder.typeBadge.setBackgroundColor(0xFF1E88E5);
            String label = t.watchAppLabel != null ? t.watchAppLabel : t.watchPackage;
            holder.condition.setText("启动 " + (label != null ? label : "?"));
        } else {
            holder.typeBadge.setText("通知");
            holder.typeBadge.setBackgroundColor(0xFFE040FB);
            String pkg = t.notificationPackage != null ? t.notificationPackage : "任意应用";
            String kw = t.notificationKeyword != null ? " 含 \"" + t.notificationKeyword + "\"" : "";
            holder.condition.setText(pkg + kw);
        }

        holder.target.setText("→ " + t.projectName + " / " + t.taskId);
        holder.enabled.setChecked(t.enabled);

        holder.enabled.setOnCheckedChangeListener((btn, checked) -> {
            t.enabled = checked;
            if (listener != null) listener.onToggle(t, checked);
        });
        holder.delete.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(t);
        });
    }

    @Override
    public int getItemCount() {
        return triggers.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView typeBadge, condition, target, delete;
        Switch enabled;

        ViewHolder(View v) {
            super(v);
            typeBadge = v.findViewById(R.id.tv_trigger_type_badge);
            condition = v.findViewById(R.id.tv_trigger_condition);
            target = v.findViewById(R.id.tv_trigger_target);
            enabled = v.findViewById(R.id.sw_trigger_enabled);
            delete = v.findViewById(R.id.btn_trigger_delete);
        }
    }
}

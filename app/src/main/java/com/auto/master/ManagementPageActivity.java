package com.auto.master;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Arrays;
import java.util.List;

/** Simple management page placeholder for Projects/Tasks/Operations. */
public class ManagementPageActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_management);

        RecyclerView rv = findViewById(R.id.recycler_management);
        rv.setLayoutManager(new LinearLayoutManager(this));
        List<String> items = Arrays.asList("Projects", "Tasks", "Operations");
        rv.setAdapter(new ManagementAdapter(items));
    }
}

class ManagementAdapter extends RecyclerView.Adapter<ManagementAdapter.ViewHolder> {
    private final List<String> items;
    ManagementAdapter(List<String> items) { this.items = items; }
    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView t;
        ViewHolder(@NonNull View itemView) { super(itemView); t = itemView.findViewById(R.id.management_item_text); }
    }
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_management, parent, false);
        return new ViewHolder(v);
    }
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String label = items.get(position);
        holder.t.setText(label);
        holder.itemView.setOnClickListener(v -> {
            // Placeholder action
            android.widget.Toast.makeText(v.getContext(), label + " 管理功能待实现", android.widget.Toast.LENGTH_SHORT).show();
        });
    }
    @Override
    public int getItemCount() { return items.size(); }
}

package com.auto.master;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

/** Multi-panel management page with swipe between panels. */
public class ManagementMultiPageActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Simple full-screen container for ViewPager
        ViewPager viewPager = new ViewPager(this);
        viewPager.setId(ViewPager.generateViewId());
        setContentView(viewPager);

        viewPager.setAdapter(new ManagementPagerAdapter(getSupportFragmentManager()));
    }

    static class ManagementPagerAdapter extends FragmentPagerAdapter {
        private final Fragment[] fragments = new Fragment[]{
                new ProjectsFragment(),
                new TasksFragment(),
                new OperationsFragment()
        };

        ManagementPagerAdapter(@NonNull FragmentManager fm) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            return fragments[position];
        }

        @Override
        public int getCount() {
            return fragments.length;
        }
    }
}

// Projects Fragment
class ProjectsFragment extends Fragment {
    @Override
    public android.view.View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        android.view.View v = inflater.inflate(R.layout.fragment_grid, container, false);
        androidx.recyclerview.widget.RecyclerView rv = v.findViewById(R.id.recycler_grid);
        rv.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(getContext(), 3));
        java.util.List<String> data = new java.util.ArrayList<>();
        for (int i = 1; i <= 9; i++) data.add("Project " + i);
        rv.setAdapter(new SimpleGridAdapter(data));
        return v;
    }
}

// Tasks Fragment
class TasksFragment extends Fragment {
    @Override
    public android.view.View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        android.view.View v = inflater.inflate(R.layout.fragment_grid, container, false);
        androidx.recyclerview.widget.RecyclerView rv = v.findViewById(R.id.recycler_grid);
        rv.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(getContext(), 3));
        java.util.List<String> data = new java.util.ArrayList<>();
        for (int i = 1; i <= 9; i++) data.add("Task " + i);
        rv.setAdapter(new SimpleGridAdapter(data));
        return v;
    }
}

// Operations Fragment
class OperationsFragment extends Fragment {
    @Override
    public android.view.View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        android.view.View v = inflater.inflate(R.layout.fragment_grid, container, false);
        androidx.recyclerview.widget.RecyclerView rv = v.findViewById(R.id.recycler_grid);
        rv.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(getContext(), 3));
        java.util.List<String> data = new java.util.ArrayList<>();
        for (int i = 1; i <= 9; i++) data.add("Operation " + i);
        rv.setAdapter(new SimpleGridAdapter(data));
        return v;
    }
}

// Simple grid adapter for fragments
class SimpleGridAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<SimpleGridAdapter.ViewHolder> {
    private final java.util.List<String> items;

    SimpleGridAdapter(java.util.List<String> data) {
        this.items = data;
    }

    static class ViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
        android.widget.Button btn;
        ViewHolder(android.view.View v) {
            super(v);
            btn = v.findViewById(R.id.grid_item_btn);
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        android.view.View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_grid_button, parent, false);
        // Ensure the button id matches
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.btn.setText(items.get(position));
        holder.btn.setOnClickListener(v -> {
            // Placeholder: could navigate to detail pages later
            android.widget.Toast.makeText(v.getContext(), items.get(position) + " 点击", android.widget.Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public int getItemCount() { return items.size(); }
}

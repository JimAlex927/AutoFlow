package com.auto.master;

import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;

/** Project Explorer: browse Projects -> Tasks -> Operations (3-level navigation). */
public class ProjectExplorerActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_project_explorer);
        if (savedInstanceState == null) {
            // Start at Projects level
            loadProjects();
        }
    }

    // Simple method to replace current fragment
    private void replaceFragment(Fragment f) {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        ft.replace(R.id.container, f);
        ft.addToBackStack(null);
        ft.commit();
    }

    // Step 1: load projects
    void loadProjects() {
        replaceFragment(new ProjectsFragment());
    }

    // Step 2: load tasks for a project
    void loadTasks(String projectPath) {
        Bundle b = new Bundle();
        b.putString("projectPath", projectPath);
        TasksFragment tf = new TasksFragment();
        tf.setArguments(b);
        replaceFragment(tf);
    }

    // Step 3: load operations for a task
    void loadOperations(String taskPath) {
        Bundle b = new Bundle();
        b.putString("taskPath", taskPath);
        OperationsFragment of = new OperationsFragment();
        of.setArguments(b);
        replaceFragment(of);
    }

    // --------------- Fragments ---------------
    public static class ProjectsFragment extends Fragment {
        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            RecyclerView rv = new RecyclerView(getContext());
            rv.setLayoutManager(new GridLayoutManager(getContext(), 3));
            // Load project dirs from internal storage (apps private)
            File root = new File(getContext().getFilesDir(), "projects");
            if (!root.exists()) {
                // Create sample data
                root.mkdirs();
                createSampleData(root);
            }
            List<String> projects = new ArrayList<>();
            File[] dirs = root.listFiles(File::isDirectory);
            if (dirs != null) {
                for (File d : dirs) projects.add(d.getName());
            }
            SimpleTextAdapter adapter = new SimpleTextAdapter(projects, v -> {
                String name = (String) v.getTag();
                String path = new File(root, name).getAbsolutePath();
                // Navigate to Tasks in the hosting activity
                if (getActivity() instanceof ProjectExplorerActivity) {
                    ((ProjectExplorerActivity) getActivity()).loadTasks(path);
                }
            });
            rv.setAdapter(adapter);
            return rv;
        }
    private static void createSampleData(File root) {
            // Create two projects (A and B) with two tasks each and sample operations.json
            String[] projects = {"ProjectA", "ProjectB"};
            String[][] tasks = new String[][] { {"Task01", "Task02"}, {"Task01", "Task02"} };
            for (int i = 0; i < projects.length; i++) {
                File p = new File(root, projects[i]);
                p.mkdirs();
                for (String t : tasks[i]) {
                    File dir = new File(p, t);
                    dir.mkdirs();
                    File json = new File(dir, "operations.json");
                    if (!json.exists()) {
                        String content = "[ {\"name\": \"Operation1\", \"type\": \"click\"}, {\"name\": \"Operation2\", \"type\": \"sleep\", \"duration\": 2000} ]";
                        try (java.io.OutputStream os = new java.io.FileOutputStream(json)) {
                            os.write(content.getBytes("UTF-8"));
                        } catch (Exception ignored) {}
                    }
                    // create sample assets dir (optional)
                    new File(dir, "assets").mkdirs();
                }
            }
        }
    }

    public static class TasksFragment extends Fragment {
        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            RecyclerView rv = new RecyclerView(getContext());
            rv.setLayoutManager(new GridLayoutManager(getContext(), 3));
            String projectPath;
            if (getArguments() != null) projectPath = getArguments().getString("projectPath");
            else {
                projectPath = null;
            }
            List<String> tasks = new ArrayList<>();
            if (projectPath != null) {
                File proj = new File(projectPath);
                File[] ds = proj.listFiles(File::isDirectory);
                if (ds != null) for (File d : ds) tasks.add(d.getName());
            }
            SimpleTextAdapter adapter = new SimpleTextAdapter(tasks, v -> {
                String name = (String) v.getTag();
                String path = projectPath + File.separator + name;
                if (getActivity() instanceof ProjectExplorerActivity) {
                    ((ProjectExplorerActivity) getActivity()).loadOperations(new File(path).getAbsolutePath());
                }
            });
            rv.setAdapter(adapter);
            return rv;
        }
    }

    public static class OperationsFragment extends Fragment {
        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            RecyclerView rv = new RecyclerView(getContext());
            rv.setLayoutManager(new GridLayoutManager(getContext(), 3));
            String taskPath = null;
            if (getArguments() != null) taskPath = getArguments().getString("taskPath");
            List<String> ops = new ArrayList<>();
            if (taskPath != null) {
                File f = new File(taskPath, "operations.json");
                if (f.exists()) {
                    // Very simple parse: look for names in the json array of objects with "name"
                    try {
                        // 读取文件内容 - 使用传统方式
                        StringBuilder content = new StringBuilder();
                        BufferedReader reader = new BufferedReader(new FileReader(f));
                        String line;
                        while ((line = reader.readLine()) != null) {
                            content.append(line);
                        }
                        reader.close();

                        // 创建 JSONArray
                        org.json.JSONArray arr = new org.json.JSONArray(content.toString());
                        for (int i = 0; i < arr.length(); i++) {
                            org.json.JSONObject o = arr.getJSONObject(i);
                            if (o.has("name")) {
                                ops.add(o.optString("name"));
                            }
                        }
                    } catch (Exception ignore) {
                        // 忽略异常，使用默认数据
                    }
                }
            }
//            if (ops.isEmpty()) {
//                ops.add("示例操作 1");
//                ops.add("示例操作 2");
//                ops.add("示例操作 3");
//            }
            SimpleTextAdapter adapter = new SimpleTextAdapter(ops, v -> {
                // 点击操作项的简单反馈
                Object tag = v.getTag();
                android.widget.Toast.makeText(v.getContext(), tag != null ? tag.toString() : "操作", android.widget.Toast.LENGTH_SHORT).show();
            });
            rv.setAdapter(adapter);
            return rv;
        }
    }
    // Simple reusable adapter for text items in a grid
    public static class SimpleTextAdapter extends RecyclerView.Adapter<SimpleTextAdapter.Holder> {
        interface OnClick {
            void onClick(View v);
        }
        private final List<String> items;
        private final OnClick click;
        public SimpleTextAdapter(List<String> items, OnClick click) {
            this.items = items;
            this.click = click;
        }
        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView tv = new TextView(parent.getContext());
            tv.setPadding(12,12,12,12);
            tv.setTextSize(14f);
            return new Holder(tv);
        }
        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            String text = items.get(position);
            holder.text.setText(text);
            holder.text.setTag(text);
            holder.text.setOnClickListener(v -> {
                if (click != null) click.onClick(v);
            });
        }
        @Override
        public int getItemCount() { return items.size(); }
        static class Holder extends RecyclerView.ViewHolder {
            TextView text;
            Holder(@NonNull View itemView) {
                super(itemView);
                text = (TextView) itemView;
            }
        }
    }
}

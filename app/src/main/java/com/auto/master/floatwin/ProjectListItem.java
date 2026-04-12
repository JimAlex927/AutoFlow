package com.auto.master.floatwin;

import java.io.File;

final class ProjectListItem {
    final File dir;
    final int taskCount;
    final long lastModified;

    ProjectListItem(File dir, int taskCount, long lastModified) {
        this.dir = dir;
        this.taskCount = taskCount;
        this.lastModified = lastModified;
    }
}

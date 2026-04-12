package com.auto.master.floatwin;

import java.util.ArrayList;
import java.util.List;

final class PrecheckResult {
    final List<String> blocking = new ArrayList<>();
    final List<String> warnings = new ArrayList<>();
    int fixAction = 0;

    boolean hasBlocking() {
        return !blocking.isEmpty();
    }
}

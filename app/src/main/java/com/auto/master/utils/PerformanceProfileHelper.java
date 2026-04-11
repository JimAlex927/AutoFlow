package com.auto.master.utils;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;

import androidx.annotation.Nullable;

public final class PerformanceProfileHelper {

    private static final String SP_NAME = "runtime_performance_profile";
    private static final String KEY_RUN_MODE = "run_mode";

    public enum Tier {
        HIGH,
        BALANCED,
        COOL
    }

    public enum RunMode {
        EXTREME("extreme", "极速"),
        BALANCED("balanced", "均衡"),
        COOL("cool", "冷静");

        public final String value;
        public final String label;

        RunMode(String value, String label) {
            this.value = value;
            this.label = label;
        }

        public static RunMode fromValue(@Nullable String value) {
            if (value == null || value.trim().isEmpty()) {
                return BALANCED;
            }
            for (RunMode mode : values()) {
                if (mode.value.equalsIgnoreCase(value)) {
                    return mode;
                }
            }
            return BALANCED;
        }
    }

    private PerformanceProfileHelper() {
    }

    public static RunMode getRunMode(@Nullable Context context) {
        if (context == null) {
            return RunMode.BALANCED;
        }
        SharedPreferences sp = context.getApplicationContext()
                .getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        return RunMode.fromValue(sp.getString(KEY_RUN_MODE, RunMode.BALANCED.value));
    }

    public static void setRunMode(@Nullable Context context, @Nullable RunMode mode) {
        if (context == null || mode == null) {
            return;
        }
        context.getApplicationContext()
                .getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_RUN_MODE, mode.value)
                .apply();
    }

    public static Tier getTier(@Nullable Context context) {
        int cpuCores = Math.max(1, Runtime.getRuntime().availableProcessors());
        long maxHeapMb = Runtime.getRuntime().maxMemory() / (1024L * 1024L);
        boolean lowRam = false;
        if (context != null) {
            ActivityManager am = context.getSystemService(ActivityManager.class);
            lowRam = am != null && am.isLowRamDevice();
        }

        if (lowRam || cpuCores <= 4 || maxHeapMb <= 256L) {
            return Tier.COOL;
        }
        if (cpuCores <= 6 || maxHeapMb <= 384L) {
            return Tier.BALANCED;
        }
        return Tier.HIGH;
    }

    public static int recommendedMatchParallelism(@Nullable Context context) {
        int cpuCores = Math.max(1, Runtime.getRuntime().availableProcessors());
        RunMode runMode = getRunMode(context);
        if (runMode == RunMode.EXTREME) {
            return cpuCores <= 1 ? 1 : 2;
        }
        if (runMode == RunMode.COOL) {
            return 1;
        }
        if (cpuCores <= 4) {
            return 1;
        }
        return Math.min(2, Math.max(1, cpuCores / 4));
    }

    public static boolean shouldPreferGray(@Nullable Context context,
                                           int templateWidth,
                                           int templateHeight,
                                           @Nullable Rect searchRoi) {
        RunMode runMode = getRunMode(context);
        if (runMode == RunMode.EXTREME) {
            return false;
        }
        Tier tier = getTier(context);
        int templateArea = Math.max(1, templateWidth) * Math.max(1, templateHeight);
        int searchArea = searchRoi == null ? 0 : Math.max(1, searchRoi.width()) * Math.max(1, searchRoi.height());

        if (runMode == RunMode.COOL) {
            if (tier == Tier.COOL) {
                return templateArea >= 24_000 || searchArea >= 260_000;
            }
            if (tier == Tier.BALANCED) {
                return templateArea >= 32_000 || searchArea >= 360_000;
            }
            return templateArea >= 72_000 || searchArea >= 700_000;
        }

        if (tier == Tier.COOL) {
            return templateArea >= 36_000 || searchArea >= 420_000;
        }
        if (tier == Tier.BALANCED) {
            return templateArea >= 56_000 || searchArea >= 520_000;
        }
        return templateArea >= 140_000 || searchArea >= 1_000_000;
    }

    public static double recommendedScaleFactor(@Nullable Context context,
                                                int templateWidth,
                                                int templateHeight,
                                                @Nullable Rect searchRoi) {
        RunMode runMode = getRunMode(context);
        if (runMode == RunMode.EXTREME) {
            return 1.0d;
        }
        Tier tier = getTier(context);
        int templateArea = Math.max(1, templateWidth) * Math.max(1, templateHeight);
        int searchArea = searchRoi == null ? 0 : Math.max(1, searchRoi.width()) * Math.max(1, searchRoi.height());

        if (runMode == RunMode.COOL) {
            if (tier == Tier.COOL) {
                if (searchArea >= 1_400_000 || templateArea >= 140_000) {
                    return 0.68d;
                }
                if (searchArea >= 700_000 || templateArea >= 70_000) {
                    return 0.80d;
                }
                return 0.90d;
            }
            if (tier == Tier.BALANCED) {
                if (searchArea >= 1_100_000 || templateArea >= 100_000) {
                    return 0.78d;
                }
                if (searchArea >= 520_000 || templateArea >= 50_000) {
                    return 0.88d;
                }
                return 0.94d;
            }
            if (searchArea >= 1_600_000 || templateArea >= 160_000) {
                return 0.90d;
            }
            return 0.96d;
        }

        if (tier == Tier.COOL) {
            if (searchArea >= 1_400_000 || templateArea >= 140_000) {
                return 0.72d;
            }
            if (searchArea >= 700_000 || templateArea >= 70_000) {
                return 0.84d;
            }
            return 0.92d;
        }
        if (tier == Tier.BALANCED) {
            if (searchArea >= 1_000_000 || templateArea >= 100_000) {
                return 0.84d;
            }
            if (searchArea >= 500_000 || templateArea >= 50_000) {
                return 0.92d;
            }
            return 1.0d;
        }
        if (searchArea >= 1_600_000 || templateArea >= 160_000) {
            return 0.94d;
        }
        return 1.0d;
    }

    public static boolean shouldUseStrictVerification(@Nullable Context context,
                                                      double scaleFactor,
                                                      boolean useGray) {
        if (!(scaleFactor < 0.999d || useGray)) {
            return false;
        }
        return getRunMode(context) != RunMode.EXTREME;
    }
}

package com.auto.master.utils;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.auto.master.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BatteryHardeningHelper {

    private BatteryHardeningHelper() {
    }

    public static boolean isIgnoringBatteryOptimizations(@Nullable Context context) {
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        PowerManager powerManager = context.getSystemService(PowerManager.class);
        return powerManager != null && powerManager.isIgnoringBatteryOptimizations(context.getPackageName());
    }

    public static void showHardeningDialog(@NonNull Activity activity) {
        String[] items = new String[]{
                activity.getString(R.string.hardening_item_system_whitelist),
                activity.getString(R.string.hardening_item_vendor_battery),
                activity.getString(R.string.hardening_item_auto_start),
                activity.getString(R.string.hardening_item_app_details)
        };
        new AlertDialog.Builder(activity)
                .setTitle(R.string.hardening_dialog_title)
                .setMessage(activity.getString(
                        R.string.hardening_dialog_message,
                        getVendorDisplayName(),
                        activity.getString(isIgnoringBatteryOptimizations(activity)
                                ? R.string.hardening_state_whitelisted
                                : R.string.hardening_state_restricted)
                ))
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            openSystemBatteryWhitelist(activity);
                            break;
                        case 1:
                            if (!openVendorBatterySettings(activity)) {
                                Toast.makeText(activity,
                                        R.string.toast_hardening_vendor_settings_unavailable,
                                        Toast.LENGTH_LONG).show();
                                openAppDetails(activity);
                            }
                            break;
                        case 2:
                            if (!openAutoStartSettings(activity)) {
                                Toast.makeText(activity,
                                        R.string.toast_hardening_auto_start_unavailable,
                                        Toast.LENGTH_LONG).show();
                                openAppDetails(activity);
                            }
                            break;
                        case 3:
                            openAppDetails(activity);
                            break;
                        default:
                            break;
                    }
                })
                .setPositiveButton(R.string.hardening_dialog_best_effort, (dialog, which) -> openBestEffortFlow(activity))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    public static void openBestEffortFlow(@NonNull Activity activity) {
        if (!isIgnoringBatteryOptimizations(activity) && openSystemBatteryWhitelist(activity)) {
            return;
        }
        if (openVendorBatterySettings(activity)) {
            Toast.makeText(activity, R.string.toast_hardening_vendor_settings_opened, Toast.LENGTH_LONG).show();
            return;
        }
        if (openAutoStartSettings(activity)) {
            Toast.makeText(activity, R.string.toast_hardening_auto_start_opened, Toast.LENGTH_LONG).show();
            return;
        }
        openAppDetails(activity);
        Toast.makeText(activity, R.string.toast_hardening_fallback_app_details, Toast.LENGTH_LONG).show();
    }

    public static boolean openSystemBatteryWhitelist(@NonNull Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false;
        }
        Intent requestIntent = new Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:" + activity.getPackageName())
        );
        if (startActivitySafely(activity, requestIntent)) {
            Toast.makeText(activity, R.string.toast_battery_optimization_prompt, Toast.LENGTH_LONG).show();
            return true;
        }

        Intent settingsIntent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
        if (startActivitySafely(activity, settingsIntent)) {
            Toast.makeText(activity, R.string.toast_battery_optimization_request_failed, Toast.LENGTH_LONG).show();
            return true;
        }
        return false;
    }

    public static boolean openVendorBatterySettings(@NonNull Context context) {
        List<Intent> intents = new ArrayList<>();
        String brand = normalizedBrand();
        String manufacturer = normalizedManufacturer();

        if (containsAny(brand, manufacturer, "xiaomi", "redmi", "poco")) {
            addComponent(intents, "com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity");
            addComponent(intents, "com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity");
            addComponent(intents, "com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsContainerManagementActivity");
        } else if (containsAny(brand, manufacturer, "vivo", "iqoo")) {
            addFlattenedComponent(intents, "com.iqoo.powersaving/com.iqoo.powersaving.PowerSavingManagerActivity");
            addFlattenedComponent(intents, "com.vivo.abe/com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity");
            addFlattenedComponent(intents, "com.vivo.abeui/com.vivo.abeui.highpower.ExcessivePowerManagerActivity");
            addFlattenedComponent(intents, "com.iqoo.secure/.ui.phoneoptimize.BgStartUpManager");
        } else if (containsAny(brand, manufacturer, "oppo", "realme", "oneplus")) {
            addFlattenedComponent(intents, "com.coloros.oppoguardelf/com.coloros.powermanager.fuelgaue.PowerUsageModelActivity");
            addFlattenedComponent(intents, "com.coloros.oppoguardelf/com.coloros.powermanager.fuelgaue.PowerConsumptionActivity");
            addFlattenedComponent(intents, "com.oplus.battery/com.oplus.powermanager.fuelgaue.PowerConsumptionActivity");
            addFlattenedComponent(intents, "com.oppo.safe/.permission.startup.StartupAppListActivity");
        } else if (containsAny(brand, manufacturer, "huawei", "honor")) {
            addFlattenedComponent(intents, "com.huawei.systemmanager/.optimize.process.ProtectActivity");
            addFlattenedComponent(intents, "com.huawei.systemmanager/.startupmgr.ui.StartupNormalAppListActivity");
            addFlattenedComponent(intents, "com.hihonor.systemmanager/.optimize.process.ProtectActivity");
        } else if (containsAny(brand, manufacturer, "samsung")) {
            addFlattenedComponent(intents, "com.samsung.android.lool/com.samsung.android.sm.ui.battery.BatteryActivity");
            addFlattenedComponent(intents, "com.samsung.android.sm_cn/com.samsung.android.sm.ui.appmanagement.AppManagementActivity");
        } else if (containsAny(brand, manufacturer, "meizu")) {
            addFlattenedComponent(intents, "com.meizu.safe/.powerui.PowerAppPermissionActivity");
            addFlattenedComponent(intents, "com.meizu.safe/.permission.SmartBGActivity");
        }

        addAction(intents, Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
        return startFirstAvailable(context, intents);
    }

    public static boolean openAutoStartSettings(@NonNull Context context) {
        List<Intent> intents = new ArrayList<>();
        String brand = normalizedBrand();
        String manufacturer = normalizedManufacturer();

        if (containsAny(brand, manufacturer, "xiaomi", "redmi", "poco")) {
            addFlattenedComponent(intents, "com.miui.securitycenter/com.miui.permcenter.autostart.AutoStartManagementActivity");
        } else if (containsAny(brand, manufacturer, "vivo", "iqoo")) {
            addFlattenedComponent(intents, "com.vivo.permissionmanager/.activity.BgStartUpManagerActivity");
            addFlattenedComponent(intents, "com.iqoo.secure/.ui.phoneoptimize.SoftwareManagerActivity");
        } else if (containsAny(brand, manufacturer, "oppo", "realme", "oneplus")) {
            addFlattenedComponent(intents, "com.coloros.safecenter/.startupapp.StartupAppListActivity");
            addFlattenedComponent(intents, "com.oppo.safe/.permission.startup.StartupAppListActivity");
            addFlattenedComponent(intents, "com.coloros.safecenter/.permission.startup.StartupAppListActivity");
        } else if (containsAny(brand, manufacturer, "huawei", "honor")) {
            addFlattenedComponent(intents, "com.huawei.systemmanager/.startupmgr.ui.StartupNormalAppListActivity");
            addFlattenedComponent(intents, "com.hihonor.systemmanager/.startupmgr.ui.StartupNormalAppListActivity");
        } else if (containsAny(brand, manufacturer, "meizu")) {
            addFlattenedComponent(intents, "com.meizu.safe/.permission.PermissionMainActivity");
        } else if (containsAny(brand, manufacturer, "samsung")) {
            addFlattenedComponent(intents, "com.samsung.android.lool/com.samsung.android.sm.ui.ram.AutoRunActivity");
        }

        return startFirstAvailable(context, intents);
    }

    public static void openAppDetails(@NonNull Context context) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", context.getPackageName(), null));
        startActivitySafely(context, intent);
    }

    private static boolean startFirstAvailable(@NonNull Context context, @NonNull List<Intent> intents) {
        for (Intent intent : intents) {
            if (startActivitySafely(context, intent)) {
                return true;
            }
        }
        return false;
    }

    private static boolean startActivitySafely(@NonNull Context context, @Nullable Intent intent) {
        if (intent == null) {
            return false;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            if (intent.resolveActivity(context.getPackageManager()) == null) {
                return false;
            }
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }

    private static void addAction(@NonNull List<Intent> intents, @NonNull String action) {
        intents.add(new Intent(action));
    }

    private static void addComponent(@NonNull List<Intent> intents,
                                     @NonNull String packageName,
                                     @NonNull String className) {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(packageName, className));
        intents.add(intent);
    }

    private static void addFlattenedComponent(@NonNull List<Intent> intents, @NonNull String flattened) {
        ComponentName componentName = ComponentName.unflattenFromString(flattened);
        if (componentName == null) {
            return;
        }
        Intent intent = new Intent();
        intent.setComponent(componentName);
        intents.add(intent);
    }

    private static boolean containsAny(String brand, String manufacturer, String... keywords) {
        for (String keyword : keywords) {
            if (brand.contains(keyword) || manufacturer.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizedBrand() {
        return normalize(Build.BRAND);
    }

    private static String normalizedManufacturer() {
        return normalize(Build.MANUFACTURER);
    }

    private static String normalize(@Nullable String value) {
        if (TextUtils.isEmpty(value)) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String getVendorDisplayName() {
        String brand = TextUtils.isEmpty(Build.BRAND) ? Build.MANUFACTURER : Build.BRAND;
        if (TextUtils.isEmpty(brand)) {
            return "Android";
        }
        return brand.toUpperCase(Locale.ROOT);
    }
}

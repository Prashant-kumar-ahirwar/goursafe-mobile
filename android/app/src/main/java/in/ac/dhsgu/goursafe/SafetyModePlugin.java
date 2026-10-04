package in.ac.dhsgu.goursafe;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.WindowManager;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;

/**
 * Silent SOS mode.
 *
 * enable()  -> Do Not Disturb "total silence" + silent ringer (no sound, no vibration)
 * disable() -> puts the phone back exactly how it was before
 *
 * Android only lets an app change Do Not Disturb after the user grants
 * "Do Not Disturb access" once (requestPolicyAccess opens that screen).
 */
@CapacitorPlugin(
    name = "SafetyMode",
    permissions = {
        @Permission(
            alias = "location",
            strings = { Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION }
        ),
        @Permission(
            alias = "notifications",
            strings = { Manifest.permission.POST_NOTIFICATIONS }
        )
    }
)
public class SafetyModePlugin extends Plugin {

    public static final String PREFS = "goursafe_safety_mode";
    public static final String K_SOS = "sos_active";
    private static final String K_ACTIVE = "active";
    private static final String K_RINGER = "prev_ringer";
    private static final String K_FILTER = "prev_filter";

    private NotificationManager notifications() {
        return (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
    }

    private AudioManager audio() {
        return (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
    }

    private SharedPreferences prefs() {
        return getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @PluginMethod
    public void getStatus(PluginCall call) {
        JSObject r = new JSObject();
        r.put("policyAccess", notifications().isNotificationPolicyAccessGranted());
        r.put("active", prefs().getBoolean(K_ACTIVE, false));
        r.put("sdk", Build.VERSION.SDK_INT);
        PowerManager pm = (PowerManager) getContext().getSystemService(Context.POWER_SERVICE);
        r.put("batteryUnrestricted",
            pm != null && pm.isIgnoringBatteryOptimizations(getContext().getPackageName()));
        call.resolve(r);
    }

    /** Opens this app's page in Android Settings (needed after "Don't ask again"). */
    @PluginMethod
    public void openAppSettings(PluginCall call) {
        Intent intent = new Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", getContext().getPackageName(), null)
        );
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(intent);
        call.resolve();
    }

    /** Opens the battery-optimisation list so the user can set GourSafe to "Don't optimise". */
    @PluginMethod
    public void openBatterySettings(PluginCall call) {
        Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(intent);
        call.resolve();
    }

    /** JS tells us whether an SOS is running, so the Back button never fully closes the app then. */
    @PluginMethod
    public void setSosActive(PluginCall call) {
        boolean active = Boolean.TRUE.equals(call.getBoolean("active", false));
        prefs().edit().putBoolean(K_SOS, active).apply();
        call.resolve();
    }

    @PluginMethod
    public void requestPolicyAccess(PluginCall call) {
        Intent intent = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(intent);
        call.resolve();
    }

    @PluginMethod
    public void enable(PluginCall call) {
        NotificationManager nm = notifications();
        AudioManager am = audio();
        SharedPreferences p = prefs();

        // Remember the normal state, but never overwrite it if enable() is called twice.
        if (!p.getBoolean(K_ACTIVE, false)) {
            p.edit()
                .putInt(K_RINGER, am.getRingerMode())
                .putInt(K_FILTER, nm.getCurrentInterruptionFilter())
                .putBoolean(K_ACTIVE, true)
                .apply();
        }

        boolean dnd = false;
        boolean silent = false;

        try {
            if (nm.isNotificationPolicyAccessGranted()) {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE);
                dnd = true;
            }
        } catch (SecurityException ignored) { }

        try {
            am.setRingerMode(AudioManager.RINGER_MODE_SILENT);
            silent = am.getRingerMode() == AudioManager.RINGER_MODE_SILENT;
        } catch (SecurityException ignored) { }

        JSObject r = new JSObject();
        r.put("dnd", dnd);
        r.put("silent", silent);
        call.resolve(r);
    }

    @PluginMethod
    public void disable(PluginCall call) {
        NotificationManager nm = notifications();
        AudioManager am = audio();
        SharedPreferences p = prefs();

        if (p.getBoolean(K_ACTIVE, false)) {
            int prevFilter = p.getInt(K_FILTER, 0);
            int prevRinger = p.getInt(K_RINGER, AudioManager.RINGER_MODE_NORMAL);

            // Restore Do Not Disturb first, otherwise it can block the ringer change.
            try {
                if (nm.isNotificationPolicyAccessGranted() && prevFilter >= 1 && prevFilter <= 4) {
                    nm.setInterruptionFilter(prevFilter);
                }
            } catch (SecurityException ignored) { }

            try {
                am.setRingerMode(prevRinger);
            } catch (SecurityException ignored) { }

            p.edit().putBoolean(K_ACTIVE, false).apply();
        }
        call.resolve();
    }

    /** Optional: turn screen brightness right down so the phone is less noticeable. */
    @PluginMethod
    public void setDimmed(PluginCall call) {
        final boolean on = Boolean.TRUE.equals(call.getBoolean("on", false));
        getActivity().runOnUiThread(() -> {
            WindowManager.LayoutParams lp = getActivity().getWindow().getAttributes();
            lp.screenBrightness = on ? 0.01f : WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
            getActivity().getWindow().setAttributes(lp);
        });
        call.resolve();
    }
}

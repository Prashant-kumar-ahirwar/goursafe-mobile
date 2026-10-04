package in.ac.dhsgu.goursafe;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.provider.Settings;
import android.view.WindowManager;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * Silent SOS mode.
 *
 * enable()  -> Do Not Disturb "total silence" + silent ringer (no sound, no vibration)
 * disable() -> puts the phone back exactly how it was before
 *
 * Android only lets an app change Do Not Disturb after the user grants
 * "Do Not Disturb access" once (requestPolicyAccess opens that screen).
 */
@CapacitorPlugin(name = "SafetyMode")
public class SafetyModePlugin extends Plugin {

    private static final String PREFS = "goursafe_safety_mode";
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
        call.resolve(r);
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

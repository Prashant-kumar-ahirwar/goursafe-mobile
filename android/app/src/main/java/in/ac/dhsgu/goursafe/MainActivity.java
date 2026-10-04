package in.ac.dhsgu.goursafe;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    // Two back presses within this time = leave the app.
    private static final long DOUBLE_BACK_MS = 2000;
    private long lastBackPress = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Local plugins must be registered BEFORE super.onCreate()
        registerPlugin(SafetyModePlugin.class);
        super.onCreate(savedInstanceState);

        // No stretch / bounce at the top and bottom of a page. That effect made the
        // sticky top bar and the fixed bottom bar look like they were moving.
        WebView webView = getBridge().getWebView();
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        // Keep the sign-in cookie: accept cookies and write them to storage now,
        // so the student is still signed in after the app is closed.
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        // Back button:
        //   1 press, on any other page -> go to the Home screen
        //   1 press, already on Home   -> "Press back again to exit"
        //   2 presses within 2 seconds -> leave the app
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                onGourSafeBack();
            }
        });
    }

    @Override
    public void onPause() {
        // Write cookies (the sign-in) to disk whenever the app leaves the screen
        CookieManager.getInstance().flush();
        super.onPause();
    }

    @Override
    public void onStop() {
        CookieManager.getInstance().flush();
        super.onStop();
    }

    private void onGourSafeBack() {
        WebView webView = getBridge().getWebView();
        long now = SystemClock.elapsedRealtime();

        // Is an SOS in progress? Then never fully close: tracking must keep running.
        SharedPreferences prefs = getSharedPreferences(SafetyModePlugin.PREFS, Context.MODE_PRIVATE);
        boolean sosActive = prefs.getBoolean(SafetyModePlugin.K_SOS, false);

        // Second press within 2 seconds -> exit
        if (lastBackPress != 0 && now - lastBackPress < DOUBLE_BACK_MS) {
            lastBackPress = 0;
            if (sosActive) {
                moveTaskToBack(true); // hide the app, keep sending location
            } else {
                finish();
            }
            return;
        }
        lastBackPress = now;

        String url = webView.getUrl();
        String home = null;
        boolean onHome = true;
        if (url != null) {
            Uri uri = Uri.parse(url);
            if (uri.getScheme() != null && uri.getAuthority() != null) {
                home = uri.getScheme() + "://" + uri.getAuthority() + "/";
                String path = uri.getPath();
                onHome = path == null || path.isEmpty() || path.equals("/");
            }
        }

        if (!onHome && home != null) {
            webView.loadUrl(home);
            return;
        }

        String message = sosActive
            ? "SOS mode is on. Press back again to hide the app."
            : "Press back again to exit";
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}

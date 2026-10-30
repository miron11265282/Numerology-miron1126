package com.miron1126.numerology;

import android.app.Activity;
import android.app.KeyguardManager;
import android.appwidget.AppWidgetManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private static final int RC_SAVE = 11, RC_LOCK = 12;
    private WebView web;
    private String pendingText = "";
    private boolean unlocked = false, checking = false;

    private SharedPreferences prefs() { return getSharedPreferences("numerology", MODE_PRIVATE); }

    private void toast(final String m) { runOnUiThread(() -> Toast.makeText(this, m, Toast.LENGTH_SHORT).show()); }

    private void refreshWidgets() {
        AppWidgetManager m = AppWidgetManager.getInstance(this);
        int[] ids = m.getAppWidgetIds(new ComponentName(this, DayWidget.class));
        Intent i = new Intent(this, DayWidget.class);
        i.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
        sendBroadcast(i);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        web = new WebView(this);
        web.setBackgroundColor(0xFF000000);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        web.addJavascriptInterface(new Bridge(), "Android");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if ("file".equals(u.getScheme())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) {}
                return true;
            }
        });
        web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (checking) return;
        if (prefs().getBoolean("lock", false) && !unlocked) {
            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (km != null && km.isDeviceSecure()) {
                Intent i = km.createConfirmDeviceCredentialIntent("Нумерология", "Подтвердите, что это вы");
                if (i != null) {
                    checking = true;
                    web.setVisibility(View.INVISIBLE);
                    startActivityForResult(i, RC_LOCK);
                    return;
                }
            }
        }
        unlocked = true;
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!checking) unlocked = false;
    }

    @Override
    protected void onActivityResult(int rq, int rs, Intent d) {
        super.onActivityResult(rq, rs, d);
        if (rq == RC_LOCK) {
            checking = false;
            if (rs == RESULT_OK) { unlocked = true; web.setVisibility(View.VISIBLE); } else { finish(); }
        } else if (rq == RC_SAVE && rs == RESULT_OK && d != null && d.getData() != null) {
            try (OutputStream o = getContentResolver().openOutputStream(d.getData())) {
                o.write(pendingText.getBytes("UTF-8"));
                toast("Файл сохранён");
            } catch (Exception e) { toast("Не удалось сохранить файл"); }
        }
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript(
            "(function(){var s=document.getElementById('sheet');if(s&&s.classList.contains('on')){s.classList.remove('on');return 1}var o=document.querySelector('.ov:not(.out)');if(o){closeOv();return 1}if(typeof cur!=='undefined'&&cur>0){go(0);return 1}return 0})()",
            v -> { if (!"1".equals(v)) finish(); });
    }

    public class Bridge {
        @JavascriptInterface public boolean isApp() { return true; }

        @JavascriptInterface public void copy(final String text) {
            runOnUiThread(() -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("numerology", text));
            });
        }

        @JavascriptInterface public void shareText(final String text) {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_TEXT, text);
                startActivity(Intent.createChooser(i, "Поделиться"));
            });
        }

        @JavascriptInterface public void setDaily(String json) {
            prefs().edit().putString("daily", json).apply();
            refreshWidgets();
        }

        @JavascriptInterface public void setNotify(boolean on, int h, int m) {
            prefs().edit().putBoolean("notify", on).putInt("nh", h).putInt("nm", m).apply();
            if (on && Build.VERSION.SDK_INT >= 33) {
                runOnUiThread(() -> requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 21));
            }
            DailyReceiver.schedule(MainActivity.this);
        }

        @JavascriptInterface public String getNotify() {
            SharedPreferences p = prefs();
            return (p.getBoolean("notify", false) ? "1" : "0") + "," + p.getInt("nh", 9) + "," + p.getInt("nm", 0);
        }

        @JavascriptInterface public void setLock(boolean on) { prefs().edit().putBoolean("lock", on).apply(); }

        @JavascriptInterface public boolean getLock() { return prefs().getBoolean("lock", false); }

        @JavascriptInterface public void shareImage(final String b64, final String text) {
            runOnUiThread(() -> {
                try {
                    byte[] b = Base64.decode(b64, Base64.DEFAULT);
                    Bitmap bmp = BitmapFactory.decodeByteArray(b, 0, b.length);
                    String u = MediaStore.Images.Media.insertImage(getContentResolver(), bmp, "numerology", "Нумерология");
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType("image/png");
                    i.putExtra(Intent.EXTRA_STREAM, Uri.parse(u));
                    i.putExtra(Intent.EXTRA_TEXT, text);
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(i, "Поделиться"));
                } catch (Exception e) { toast("Не удалось поделиться"); }
            });
        }

        @JavascriptInterface public void saveFile(final String name, String text) {
            pendingText = text;
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                i.putExtra(Intent.EXTRA_TITLE, name);
                startActivityForResult(i, RC_SAVE);
            });
        }

        @JavascriptInterface public void addEvent(final String title, final long startMs, final String notes) {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_INSERT)
                    .setData(CalendarContract.Events.CONTENT_URI)
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMs)
                    .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
                    .putExtra(CalendarContract.Events.TITLE, title)
                    .putExtra(CalendarContract.Events.DESCRIPTION, notes);
                try { startActivity(i); } catch (Exception e) { toast("Не найдено приложение календаря"); }
            });
        }
    }
}

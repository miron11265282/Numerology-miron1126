package com.miron1126.numerology;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

public class DailyReceiver extends BroadcastReceiver {
    static final String CH = "daily";
    static final String ACTION = "com.miron1126.numerology.DAILY";

    public static void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = PendingIntent.getBroadcast(c, 1,
            new Intent(c, DailyReceiver.class).setAction(ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
        SharedPreferences p = c.getSharedPreferences("numerology", Context.MODE_PRIVATE);
        if (!p.getBoolean("notify", false)) return;
        Calendar t = Calendar.getInstance();
        t.set(Calendar.HOUR_OF_DAY, p.getInt("nh", 9));
        t.set(Calendar.MINUTE, p.getInt("nm", 0));
        t.set(Calendar.SECOND, 0);
        if (t.getTimeInMillis() <= System.currentTimeMillis()) t.add(Calendar.DAY_OF_YEAR, 1);
        am.setRepeating(AlarmManager.RTC_WAKEUP, t.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pi);
    }

    @Override
    public void onReceive(Context c, Intent in) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(in.getAction())) { schedule(c); return; }
        SharedPreferences p = c.getSharedPreferences("numerology", Context.MODE_PRIVATE);
        String title = "Число дня", text = "Откройте приложение, чтобы увидеть совет";
        try {
            JSONObject j = new JSONObject(p.getString("daily", "{}"));
            String k = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            if (j.has(k)) {
                JSONArray a = j.getJSONArray(k);
                title = "Число дня: " + a.getInt(0) + " — " + a.getString(1);
                text = a.getString(2);
            }
        } catch (Exception ignored) {}
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(CH, "Число дня", NotificationManager.IMPORTANCE_DEFAULT));
        }
        PendingIntent open = PendingIntent.getActivity(c, 2, new Intent(c, MainActivity.class),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CH) : new Notification.Builder(c);
        b.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(text)
            .setContentIntent(open).setAutoCancel(true);
        nm.notify(7, b.build());
    }
}

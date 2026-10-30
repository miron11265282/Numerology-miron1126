package com.miron1126.numerology;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

public class DayWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        SharedPreferences p = c.getSharedPreferences("numerology", Context.MODE_PRIVATE);
        String num = "•", ttl = "Нумерология", tip = "Откройте приложение";
        try {
            JSONObject j = new JSONObject(p.getString("daily", "{}"));
            String k = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            if (j.has(k)) {
                JSONArray a = j.getJSONArray(k);
                num = String.valueOf(a.getInt(0));
                ttl = a.getString(1);
                tip = a.getString(2);
            }
        } catch (Exception ignored) {}
        PendingIntent open = PendingIntent.getActivity(c, 3, new Intent(c, MainActivity.class),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        for (int id : ids) {
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_day);
            v.setTextViewText(R.id.w_num, num);
            v.setTextViewText(R.id.w_ttl, ttl);
            v.setTextViewText(R.id.w_tip, tip);
            v.setOnClickPendingIntent(R.id.w_root, open);
            m.updateAppWidget(id, v);
        }
    }
}

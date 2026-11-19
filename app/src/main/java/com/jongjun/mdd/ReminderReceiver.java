package com.jongjun.mdd;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

/** 다시 보기 알림: JS가 넘긴 목록을 AlarmManager에 등록하고, 시간이 되면 알림을 띄운다. 재부팅 시 다시 등록. */
public class ReminderReceiver extends BroadcastReceiver {
    static final String ACTION = "com.jongjun.mdd.REMIND";
    static final String CH = "revisit";
    static final String PREF = "mdd_reminders";

    @Override
    public void onReceive(Context c, Intent i) {
        if (ACTION.equals(i.getAction())) {
            show(c, i.getStringExtra("id"), i.getStringExtra("title"), i.getStringExtra("body"));
        } else {
            JSONArray arr = load(c);
            scheduleAll(c, arr);
        }
    }

    static JSONArray load(Context c) {
        try {
            return new JSONArray(c.getSharedPreferences(PREF, 0).getString("list", "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            if (nm.getNotificationChannel(CH) == null) {
                NotificationChannel ch = new NotificationChannel(CH, "다시 보기 알림", NotificationManager.IMPORTANCE_DEFAULT);
                ch.setDescription("다시 보기로 정한 날에 일기를 알려드려요");
                nm.createNotificationChannel(ch);
            }
        }
    }

    static void show(Context c, String id, String title, String body) {
        if (id == null) return;
        if (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        ensureChannel(c);
        Intent open = new Intent(c, MainActivity.class);
        open.putExtra("entryId", id);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, id.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(c, CH)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(title == null ? "다시 볼 날이에요" : title)
                .setContentText(body == null ? "" : body)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setColor(0xFF3182F6)
                .build();
        c.getSystemService(NotificationManager.class).notify(id.hashCode(), n);
    }

    private static PendingIntent pending(Context c, String id, String title, String body, int flags) {
        Intent it = new Intent(c, ReminderReceiver.class);
        it.setAction(ACTION);
        it.putExtra("id", id);
        it.putExtra("title", title);
        it.putExtra("body", body);
        return PendingIntent.getBroadcast(c, id.hashCode(), it, flags | PendingIntent.FLAG_IMMUTABLE);
    }

    static void replace(Context c, String json) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        JSONArray old = load(c);
        for (int k = 0; k < old.length(); k++) {
            try {
                String id = old.getJSONObject(k).getString("id");
                PendingIntent pi = pending(c, id, null, null, PendingIntent.FLAG_NO_CREATE);
                if (pi != null) {
                    am.cancel(pi);
                    pi.cancel();
                }
            } catch (Exception ignored) {
            }
        }
        JSONArray arr;
        try {
            arr = new JSONArray(json);
        } catch (Exception e) {
            arr = new JSONArray();
        }
        c.getSharedPreferences(PREF, 0).edit().putString("list", arr.toString()).apply();
        scheduleAll(c, arr);
    }

    static void scheduleAll(Context c, JSONArray arr) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        long now = System.currentTimeMillis();
        for (int k = 0; k < arr.length(); k++) {
            try {
                JSONObject o = arr.getJSONObject(k);
                long at = o.getLong("at");
                if (at <= now) continue;
                PendingIntent pi = pending(c, o.getString("id"), o.optString("title"), o.optString("body"), PendingIntent.FLAG_UPDATE_CURRENT);
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } catch (Exception ignored) {
            }
        }
    }
}

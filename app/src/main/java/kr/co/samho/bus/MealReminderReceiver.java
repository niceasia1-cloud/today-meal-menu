package kr.co.samho.bus;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class MealReminderReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "meal_reminders";

    @Override
    public void onReceive(Context context, Intent intent) {
        ensureChannel(context);
        String meal = intent.getStringExtra("meal");
        if (meal == null) meal = "lunch";
        Intent open = new Intent(context, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(context, 300, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        android.app.Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new android.app.Notification.Builder(context, CHANNEL_ID)
                : new android.app.Notification.Builder(context);
        android.app.Notification notification = builder
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(label(meal) + " 식단 확인")
                .setContentText("오늘 메뉴를 확인하고 식사 계획을 정하세요.")
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build();
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(notificationId(meal), notification);
    }

    private void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "식단 알림", NotificationManager.IMPORTANCE_DEFAULT);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.createNotificationChannel(channel);
    }

    private String label(String meal) {
        if ("breakfast".equals(meal)) return "조식";
        if ("dinner".equals(meal)) return "석식";
        return "중식";
    }

    private int notificationId(String meal) {
        if ("breakfast".equals(meal)) return 201;
        if ("dinner".equals(meal)) return 203;
        return 202;
    }
}

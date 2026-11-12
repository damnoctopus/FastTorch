package com.example.fasttorch.notification;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import androidx.core.app.NotificationCompat;
import com.example.fasttorch.MainActivity;
import com.example.fasttorch.R;
import com.example.fasttorch.service.GestureMonitoringService;

public final class MonitoringNotification {
    public static final int ID = 10;
    private static final String CHANNEL = "gesture_monitoring";

    public static Notification build(Context context, boolean lightOn, boolean practice) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL,
                context.getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(context, 0,
                new Intent(context, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop = PendingIntent.getService(context, 1,
                new Intent(context, GestureMonitoringService.class).setAction(GestureMonitoringService.ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_torch)
                .setContentTitle(context.getString(R.string.notification_title))
                .setContentText(context.getString(practice ? R.string.notification_practice
                        : lightOn ? R.string.notification_light_on : R.string.notification_light_off))
                .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .addAction(0, context.getString(R.string.stop), stop)
                .build();
    }

    private MonitoringNotification() { }
}

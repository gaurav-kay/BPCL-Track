package com.example.bpcltrack;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

public class App extends Application {

    public static final String DEVIATION_CHANNEL_ID = "deviation";

    @Override
    public void onCreate() {
        super.onCreate();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel(
                    DEVIATION_CHANNEL_ID,
                    "RMP Worker Deviations",
                    NotificationManager.IMPORTANCE_HIGH
            );
            notificationChannel.setDescription("Notifications of Workers who deviated from their path");

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(notificationChannel);
            }
        }
    }
}

package com.example.geckobrowser;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

public class BrowserPlaybackService extends Service {
    public static final String ACTION_START = "com.example.geckobrowser.START_PLAYBACK";
    public static final String ACTION_STOP = "com.example.geckobrowser.STOP_PLAYBACK";
    public static final String EXTRA_NOTIFICATION = "media_notification";
    private static final int NOTIFICATION_ID = 1042;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || ACTION_STOP.equals(intent.getAction())) {
            stopForeground(true);
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        Notification notification;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notification = intent.getParcelableExtra(EXTRA_NOTIFICATION, Notification.class);
        } else {
            notification = intent.getParcelableExtra(EXTRA_NOTIFICATION);
        }
        if (notification == null) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        startForeground(NOTIFICATION_ID, notification);
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
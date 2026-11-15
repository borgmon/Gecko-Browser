package com.example.geckobrowser;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import android.support.v4.media.session.MediaSessionCompat;
import androidx.media.session.MediaButtonReceiver;

public class MediaButtonEventReceiver extends BroadcastReceiver {
    static MediaSessionCompat mediaSession;

    @Override
    public void onReceive(Context context, Intent intent) {
        MediaButtonReceiver.handleIntent(mediaSession, intent);
    }
}
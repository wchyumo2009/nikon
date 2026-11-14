package com.nikon.metronome;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

/**
 * Keeps the metronome audible after the screen turns off. The engine lives in
 * this process-wide holder, while a foreground service plus a partial wake
 * lock prevent the system from suspending the clock thread. The notification
 * gives the user a way back into the app and a way to stop the beat.
 */
public final class MetronomeService extends Service {

    private static final String TAG = "MetronomeService";
    private static final String CHANNEL_ID = "metronome_playback";
    private static final String ACTION_STOP = "com.nikon.metronome.action.STOP";
    private static final int NOTIFICATION_ID = 1;

    static final String ACTION_STATE_CHANGED = "com.nikon.metronome.action.STATE_CHANGED";

    private static MetronomeEngine engine;

    private PowerManager.WakeLock wakeLock;

    /** One clock per process: rotation and screen off/on never replace it. */
    static synchronized MetronomeEngine getEngine(Context context) {
        if (engine == null) {
            engine = new MetronomeEngine(context.getApplicationContext());
        }
        return engine;
    }

    static void startPlayback(Context context) {
        Context app = context.getApplicationContext();
        getEngine(app).start();
        Intent intent = new Intent(app, MetronomeService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                app.startForegroundService(intent);
            } else {
                app.startService(intent);
            }
        } catch (RuntimeException e) {
            // The foreground service only guarantees uninterrupted playback;
            // the clock thread still ticks while this process is alive.
            Log.e(TAG, "cannot start foreground service", e);
        }
    }

    static void stopPlayback(Context context) {
        Context app = context.getApplicationContext();
        getEngine(app).stop();
        app.stopService(new Intent(app, MetronomeService.class));
        sendStateChanged(app);
    }

    private static void sendStateChanged(Context context) {
        context.sendBroadcast(new Intent(ACTION_STATE_CHANGED)
                .setPackage(context.getPackageName()));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "nikon:metronome");
            wakeLock.setReferenceCounted(false);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopPlayback(this);
            return START_NOT_STICKY;
        }
        if (!getEngine(this).isRunning()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            startForeground(NOTIFICATION_ID, buildNotification());
        } catch (RuntimeException e) {
            Log.e(TAG, "cannot enter foreground; beats may pause while suspended", e);
        }
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        getEngine(this).stop();
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        sendStateChanged(this);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Notification buildNotification() {
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager != null) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(getString(R.string.notification_channel_desc));
            channel.setSound(null, null);
            manager.createNotificationChannel(channel);
        }

        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                flags);
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, MetronomeService.class).setAction(ACTION_STOP), flags);

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notification_playing))
                .setContentIntent(open)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(this, android.R.drawable.ic_media_pause),
                        getString(R.string.stop), stop).build())
                .build();
    }
}

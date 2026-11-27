package com.r4xn.jejak.rec;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import com.r4xn.jejak.App;
import com.r4xn.jejak.R;
import com.r4xn.jejak.Store;
import com.r4xn.jejak.core.Fix;
import com.r4xn.jejak.core.Fmt;
import com.r4xn.jejak.ui.MainActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Records the phone's position in the background, once a second by default. Android only lets this run with a
 * notification showing, so the person can always see that recording is on (and stop it from there).
 */
public final class RecordService extends Service implements LocationListener {
    public static final String CHANNEL = "recording";
    private static final String STOP = "com.r4xn.jejak.STOP";
    private static final int NOTE = 1;
    /** Wi-Fi / mobile network positions are only used when GPS has been quiet this long. */
    private static final long GPS_QUIET = 15_000;

    private LocationManager lm;
    private HandlerThread thread;
    private Handler h;
    private Store store;
    private boolean listening;
    private final List<Fix> buffer = new ArrayList<>();
    private long lastSaved, lastGps, lastFlush, lastNote, dayEnd;
    private int intervalMs;
    private Object gnss;
    private Object altitudes;

    public static void start(Context c) {
        ContextCompat.startForegroundService(c, new Intent(c, RecordService.class));
    }

    public static void stop(Context c) {
        c.stopService(new Intent(c, RecordService.class));
    }

    /** Stops and starts again, to use changed settings. */
    public static void restart(Context c) {
        if (!Live.running) return;
        stop(c);
        start(c);
    }

    public static boolean allowed(Context c) {
        return ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        store = App.store();
        lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        thread = new HandlerThread("jejak-recorder");
        thread.start();
        h = new Handler(thread.getLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && STOP.equals(intent.getAction())) {
            store.setRecording(false);
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            int type = Build.VERSION.SDK_INT >= 29 ? ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION : 0;
            ServiceCompat.startForeground(this, NOTE, note(), type);
        } catch (RuntimeException e) {
            // Android refused (no location permission, or started from the background without "Allow all the time").
            Live.problem = "Android didn't let Jejak record. Open Jejak and press Start.";
            Live.changed();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!allowed(this)) {
            Live.problem = "Location permission is off.";
            Live.changed();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!listening) begin();
        return START_STICKY;
    }

    private void begin() {
        listening = true;
        Live.running = true;
        Live.problem = null;
        intervalMs = store.interval() * 1000;
        h.post(() -> {
            long now = System.currentTimeMillis();
            long start = store.dayStart(store.today());
            dayEnd = store.dayStart(store.today().plusDays(1));
            store.cleanUp();
            Live.seed(store.db().latest(Live.KEEP, Long.MAX_VALUE), store.db().count(start, now + 1));
            lastSaved = Live.last == null ? 0 : Live.last.t;
            lastFlush = now;
        });
        try {
            if (lm.getAllProviders().contains(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, intervalMs, 0f, this, thread.getLooper());
            }
            if (store.indoor() && lm.getAllProviders().contains(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, Math.max(intervalMs, 10_000), 0f, this, thread.getLooper());
            }
            if (Build.VERSION.SDK_INT >= 24) {
                GnssStatus.Callback cb = new GnssStatus.Callback() {
                    @Override
                    public void onSatelliteStatusChanged(@NonNull GnssStatus status) {
                        int used = 0;
                        for (int i = 0; i < status.getSatelliteCount(); i++) if (status.usedInFix(i)) used++;
                        Live.satsUsed = used;
                        Live.satsSeen = status.getSatelliteCount();
                    }
                };
                lm.registerGnssStatusCallback(cb, h);
                gnss = cb;
            }
        } catch (SecurityException e) {
            Live.problem = "Location permission is off.";
            stopSelf();
            return;
        }
        Live.changed();
        h.postDelayed(tick, 5000);
    }

    /** Every few seconds: notice a lost signal, save what's waiting, keep the notification up to date. */
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            long now = System.currentTimeMillis();
            if (!buffer.isEmpty() && now - lastFlush > 10_000) flush();
            if (now >= dayEnd) {
                // a new day: start counting again and tidy up old readings
                flush();
                long start = store.dayStart(store.today());
                dayEnd = store.dayStart(store.today().plusDays(1));
                Live.today = store.db().count(start, now + 1);
                store.cleanUp();
            }
            if (now - lastNote > 60_000) updateNote();
            Live.changed();
            h.postDelayed(this, 5000);
        }
    };

    @Override
    public void onLocationChanged(@NonNull Location l) {
        long now = System.currentTimeMillis();
        boolean gps = LocationManager.GPS_PROVIDER.equals(l.getProvider());
        if (gps) lastGps = now;
        else if (now - lastGps < GPS_QUIET) return;
        // Some phones report GPS time a long way off; the phone's clock is then the better guess.
        long t = Math.abs(l.getTime() - now) > 86_400_000L ? now : l.getTime();
        // GPS ticks are not perfectly even; allow a little slack so a 0.8 s tick still counts as "a second later".
        if (t - lastSaved < intervalMs - 300) return;
        Fix f = new Fix(t, l.getLatitude(), l.getLongitude(), altitude(l),
                l.hasAccuracy() ? l.getAccuracy() : Double.NaN, l.hasSpeed() ? l.getSpeed() : Double.NaN);
        lastSaved = t;
        buffer.add(f);
        Live.reading(f, gps ? "GPS" : "Wi-Fi / mobile network");
        if (buffer.size() >= 15 || now - lastFlush > 15_000) flush();
    }

    /** Height above sea level (GPS gives height above the Earth's ellipsoid; newer Android converts it). */
    private double altitude(Location l) {
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                if (altitudes == null) altitudes = new android.location.altitude.AltitudeConverter();
                if (l.hasAltitude()) ((android.location.altitude.AltitudeConverter) altitudes).addMslAltitudeToLocation(this, l);
                if (l.hasMslAltitude()) return l.getMslAltitudeMeters();
            } catch (Exception e) {
                // fall back to the plain GPS height
            }
        }
        return l.hasAltitude() ? l.getAltitude() : Double.NaN;
    }

    private void flush() {
        if (buffer.isEmpty()) return;
        List<Fix> copy = new ArrayList<>(buffer);
        buffer.clear();
        lastFlush = System.currentTimeMillis();
        try {
            store.db().add(copy);
        } catch (RuntimeException e) {
            Live.problem = "Couldn't save readings (is the phone full?)";
        }
    }

    @Override
    public void onProviderDisabled(@NonNull String provider) {
        if (LocationManager.GPS_PROVIDER.equals(provider)) {
            Live.problem = "Location is turned off on this phone.";
            Live.changed();
        }
    }

    @Override
    public void onProviderEnabled(@NonNull String provider) {
        if (LocationManager.GPS_PROVIDER.equals(provider)) {
            Live.problem = null;
            Live.changed();
        }
    }

    private Notification note() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Recording", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Shows while Jejak is recording where you go");
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class).putExtra(MainActivity.EXTRA_TAB, MainActivity.LIVE),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, RecordService.class).setAction(STOP), PendingIntent.FLAG_IMMUTABLE);
        int every = store.interval();
        String text = (every == 1 ? "Every second" : "Every " + Fmt.duration(every * 1000L).replace(" s", " seconds"))
                + " · " + Fmt.count(Live.today) + " readings today";
        return new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_note)
                .setContentTitle("Recording your timeline")
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setContentIntent(open)
                .addAction(0, "Stop", stop)
                .build();
    }

    private void updateNote() {
        lastNote = System.currentTimeMillis();
        try {
            getSystemService(NotificationManager.class).notify(NOTE, note());
        } catch (SecurityException e) {
            // notifications are off; recording carries on
        }
    }

    @Override
    public void onDestroy() {
        listening = false;
        Live.running = false;
        try {
            lm.removeUpdates(this);
            if (Build.VERSION.SDK_INT >= 24 && gnss != null) lm.unregisterGnssStatusCallback((GnssStatus.Callback) gnss);
        } catch (RuntimeException ignored) {
            // already stopped
        }
        h.removeCallbacks(tick);
        h.post(this::flush);
        thread.quitSafely();
        Live.changed();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

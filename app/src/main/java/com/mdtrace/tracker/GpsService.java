package com.mdtrace.tracker;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class GpsService extends Service {

    private static final String TAG = "MDTraceGPS";
    private static final String CHANNEL_ID = "mdtrace_gps";
    private static final int NOTIF_ID = 1001;
    private static final long INTERVAL_MS = 30000; // 30 seconds

    private FusedLocationProviderClient fusedClient;
    private LocationCallback locationCallback;
    private Handler handler;
    private Location lastLocation;

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        fusedClient = LocationServices.getFusedLocationProviderClient(this);
        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification("MDTrace चालू है — Location tracking active"));
        startLocationUpdates();
        schedulePeriodicSend();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY; // restart if killed
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    private void startLocationUpdates() {
        LocationRequest req = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_MS)
                .setMinUpdateIntervalMillis(15000)
                .setWaitForAccurateLocation(false)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {
                if (result == null) return;
                lastLocation = result.getLastLocation();
                if (lastLocation != null) {
                    sendLocationToServer(lastLocation);
                    updateNotification("📍 " + String.format("%.4f, %.4f", lastLocation.getLatitude(), lastLocation.getLongitude()));
                }
            }
        };

        try {
            fusedClient.requestLocationUpdates(req, locationCallback, Looper.getMainLooper());
        } catch (SecurityException e) {
            Log.e(TAG, "Location permission missing: " + e.getMessage());
        }
    }

    private void schedulePeriodicSend() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (lastLocation != null) sendLocationToServer(lastLocation);
                handler.postDelayed(this, INTERVAL_MS);
            }
        }, INTERVAL_MS);
    }

    private void sendLocationToServer(final Location loc) {
        SharedPreferences prefs = getSharedPreferences("mdtrace", Context.MODE_PRIVATE);
        final String token    = prefs.getString("token", "");
        final String userId   = prefs.getString("userId", "");
        final String familyId = prefs.getString("familyId", "");
        final String server   = prefs.getString("server", "https://deeppink-heron-276368.hostingersite.com");

        if (token.isEmpty() || userId.isEmpty()) return;

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    int battery = getBatteryLevel();
                    JSONObject body = new JSONObject();
                    body.put("memberId", userId);
                    body.put("familyId", familyId);
                    body.put("lat", loc.getLatitude());
                    body.put("lng", loc.getLongitude());
                    body.put("accuracy", loc.getAccuracy());
                    body.put("battery", battery);
                    body.put("ts", new java.util.Date().toString());

                    URL url = new URL(server + "/api/location");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setRequestProperty("Authorization", "Bearer " + token);
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);

                    byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
                    OutputStream os = conn.getOutputStream();
                    os.write(data);
                    os.flush();
                    os.close();

                    int code = conn.getResponseCode();
                    Log.d(TAG, "Location sent: " + code + " lat=" + loc.getLatitude());
                    conn.disconnect();
                } catch (Exception e) {
                    Log.e(TAG, "Send failed: " + e.getMessage());
                }
            }
        }).start();
    }

    private int getBatteryLevel() {
        BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
        if (bm != null) return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        return -1;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "MDTrace GPS Tracking", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Family safety GPS tracking is active");
            ch.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification(String text) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("MDTrace — Family Safety")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentIntent(pi)
                .setOngoing(true)      // Can't be dismissed
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSilent(true)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF_ID, buildNotification(text));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (locationCallback != null) fusedClient.removeLocationUpdates(locationCallback);
        handler.removeCallbacksAndMessages(null);
        // Restart self immediately
        Intent restart = new Intent(this, GpsService.class);
        startService(restart);
    }
}

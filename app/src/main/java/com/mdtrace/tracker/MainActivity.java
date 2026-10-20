package com.mdtrace.tracker;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_PERMISSIONS = 101;
    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tvStatus = findViewById(R.id.tv_main_status);

        SharedPreferences prefs = getSharedPreferences("mdtrace", Context.MODE_PRIVATE);
        boolean setupDone = prefs.getBoolean("setup_done", false);

        if (!setupDone) {
            // First launch — go to setup
            startActivity(new Intent(this, SetupActivity.class));
            finish();
            return;
        }

        // Setup done — check permissions and start GPS
        requestAllPermissions();
    }

    private void requestAllPermissions() {
        List<String> needed = new ArrayList<>();
        String[] required = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        };
        for (String p : required) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED)
                needed.add(p);
        }
        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), REQ_PERMISSIONS);
        } else {
            onPermissionsGranted();
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        boolean allOk = true;
        for (int r : results) if (r != PackageManager.PERMISSION_GRANTED) { allOk = false; break; }
        if (allOk) onPermissionsGranted();
        else {
            tvStatus.setText("⚠️ Location permission ज़रूरी है।\nPhone Settings → Apps → MDTrace → Permissions → Location → Allow all the time");
        }
    }

    private void onPermissionsGranted() {
        // Background location (Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    new String[]{ Manifest.permission.ACCESS_BACKGROUND_LOCATION }, 102);
            }
        }

        // Battery optimization — ask to ignore
        requestIgnoreBatteryOptimization();

        // Start GPS service
        Intent svc = new Intent(this, GpsService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc);
        else startService(svc);

        SharedPreferences prefs = getSharedPreferences("mdtrace", Context.MODE_PRIVATE);
        String name = prefs.getString("name", "Member");
        tvStatus.setText("✅ " + name + " का GPS चालू है\n\nLocation हर 30 सेकंड में भेजी जा रही है।\n\n🛡️ यह ऐप बंद नहीं किया जा सकता।");
    }

    private void requestIgnoreBatteryOptimization() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                }
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void onBackPressed() {
        // Disable back button — ऐप को minimize करो, close नहीं
        moveTaskToBack(true);
    }
}

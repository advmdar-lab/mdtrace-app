package com.mdtrace.tracker;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class SetupActivity extends AppCompatActivity {

    private static final String SERVER = "https://deeppink-heron-276368.hostingersite.com";

    private EditText etName, etPhone, etFamilyName, etInviteCode;
    private RadioGroup rgMode;
    private Button btnAction;
    private TextView tvStatus;
    private boolean isJoinMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);

        etName       = findViewById(R.id.et_name);
        etPhone      = findViewById(R.id.et_phone);
        etFamilyName = findViewById(R.id.et_family_name);
        etInviteCode = findViewById(R.id.et_invite_code);
        rgMode       = findViewById(R.id.rg_mode);
        btnAction    = findViewById(R.id.btn_action);
        tvStatus     = findViewById(R.id.tv_status);

        rgMode.setOnCheckedChangeListener((group, checkedId) -> {
            isJoinMode = (checkedId == R.id.rb_join);
            etFamilyName.setVisibility(isJoinMode ? View.GONE  : View.VISIBLE);
            etInviteCode.setVisibility(isJoinMode ? View.VISIBLE : View.GONE);
            btnAction.setText(isJoinMode ? "🔗 Join Family" : "✨ Create Family & Start");
        });

        btnAction.setOnClickListener(v -> {
            String name  = etName.getText().toString().trim();
            String phone = etPhone.getText().toString().trim();
            if (name.isEmpty() || phone.length() != 10) {
                Toast.makeText(this, "नाम और 10 अंकों का नंबर भरें", Toast.LENGTH_SHORT).show();
                return;
            }
            btnAction.setEnabled(false);
            tvStatus.setText("⏳ Processing…");
            if (isJoinMode) doJoin(name, phone);
            else doRegister(name, phone);
        });
    }

    private void doRegister(String name, String phone) {
        String fName = etFamilyName.getText().toString().trim();
        if (fName.isEmpty()) { tvStatus.setText("❌ Family name भरें"); btnAction.setEnabled(true); return; }
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("phone", phone); body.put("name", name);
                body.put("familyName", fName); body.put("role", "member");
                JSONObject resp = post(SERVER + "/api/register", body.toString());
                if (resp != null && resp.optBoolean("ok")) {
                    saveAndStart(resp, SERVER);
                } else {
                    String msg = resp != null ? resp.optString("msg","Error") : "No response";
                    runOnUiThread(() -> { tvStatus.setText("❌ " + msg); btnAction.setEnabled(true); });
                }
            } catch (Exception e) {
                runOnUiThread(() -> { tvStatus.setText("❌ " + e.getMessage()); btnAction.setEnabled(true); });
            }
        }).start();
    }

    private void doJoin(String name, String phone) {
        String code = etInviteCode.getText().toString().trim().toUpperCase();
        if (code.isEmpty()) { tvStatus.setText("❌ Invite code भरें"); btnAction.setEnabled(true); return; }
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("phone", phone); body.put("name", name); body.put("inviteCode", code);
                JSONObject resp = post(SERVER + "/api/join", body.toString());
                if (resp != null && resp.optBoolean("ok")) {
                    saveAndStart(resp, SERVER);
                } else {
                    String msg = resp != null ? resp.optString("msg","Error") : "No response";
                    runOnUiThread(() -> { tvStatus.setText("❌ " + msg); btnAction.setEnabled(true); });
                }
            } catch (Exception e) {
                runOnUiThread(() -> { tvStatus.setText("❌ " + e.getMessage()); btnAction.setEnabled(true); });
            }
        }).start();
    }

    private void saveAndStart(JSONObject d, String server) {
        try {
            SharedPreferences.Editor ed = getSharedPreferences("mdtrace", Context.MODE_PRIVATE).edit();
            ed.putString("token",    d.optString("token"));
            ed.putString("userId",   d.optString("userId"));
            ed.putString("familyId", d.optString("familyId"));
            ed.putString("name",     d.optString("name"));
            ed.putString("server",   server);
            ed.putBoolean("setup_done", true);
            ed.apply();

            runOnUiThread(() -> {
                tvStatus.setText("✅ सेटअप हो गया! GPS शुरू हो रहा है…");
                // Start GPS service
                Intent svc = new Intent(this, GpsService.class);
                startForegroundService(svc);
                // Open main activity
                startActivity(new Intent(this, MainActivity.class));
                finish();
            });
        } catch (Exception e) {
            runOnUiThread(() -> tvStatus.setText("❌ Save error: " + e.getMessage()));
        }
    }

    private JSONObject post(String urlStr, String jsonBody) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        OutputStream os = conn.getOutputStream();
        os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        os.flush(); os.close();
        int code = conn.getResponseCode();
        java.io.InputStream is = code < 400 ? conn.getInputStream() : conn.getErrorStream();
        String raw = new java.util.Scanner(is).useDelimiter("\\A").next();
        conn.disconnect();
        return new JSONObject(raw);
    }
}

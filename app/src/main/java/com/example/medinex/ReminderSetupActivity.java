
package com.example.medinex;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReminderSetupActivity extends AppCompatActivity {

    private static final String TAG = "MedinexReminder";
    private static final int NOTIFICATION_PERMISSION_CODE = 101;

    private EditText medicineName;
    private TimePicker timePicker;
    private Button saveReminderBtn;
    private Button historyBtn;

    private FirebaseFirestore firestore;
    private FirebaseAuth auth;

    private final ExecutorService databaseExecutor =
            Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reminder_setup);

        medicineName = findViewById(R.id.medicineName);
        timePicker = findViewById(R.id.timePicker);
        saveReminderBtn = findViewById(R.id.saveReminderBtn);
        historyBtn = findViewById(R.id.historyBtn);

        firestore = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        if (medicineName == null || timePicker == null
                || saveReminderBtn == null || historyBtn == null) {
            Log.e(TAG, "A required view is missing from activity_reminder_setup.xml");
            Toast.makeText(this,
                    "Reminder screen layout error",
                    Toast.LENGTH_LONG).show();
            return;
        }

        // Initialize notification channel
        NotificationReceiver.ensureNotificationChannel(this);

        // Check & request notification permission for Android 13+
        checkNotificationPermission();

        timePicker.setIs24HourView(false);

        historyBtn.setOnClickListener(v -> {
            Intent intent = new Intent(
                    ReminderSetupActivity.this,
                    ReminderHistoryActivity.class
            );
            startActivity(intent);
        });

        saveReminderBtn.setOnClickListener(v -> saveReminder());
    }

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_CODE
                );
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this,
                        "Without notification permission, reminder alerts cannot be shown.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void saveReminder() {
        String medicine = medicineName.getText().toString().trim();

        if (medicine.isEmpty()) {
            medicineName.setError("Enter medicine name");
            Toast.makeText(this,
                    "Please enter medicine name",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser currentUser = auth.getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(this,
                    "Please log in again before saving",
                    Toast.LENGTH_LONG).show();
            Log.e(TAG, "No Firebase authenticated user");
            return;
        }

        int hour;
        int minute;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            hour = timePicker.getHour();
            minute = timePicker.getMinute();
        } else {
            hour = timePicker.getCurrentHour();
            minute = timePicker.getCurrentMinute();
        }

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }

        String reminderTime = String.format(
                Locale.getDefault(), "%02d:%02d", hour, minute
        );

        AlarmManager alarmManager =
                (AlarmManager) getSystemService(ALARM_SERVICE);

        if (alarmManager == null) {
            Toast.makeText(this,
                    "Alarm service is unavailable",
                    Toast.LENGTH_LONG).show();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && !alarmManager.canScheduleExactAlarms()) {
            Toast.makeText(this,
                    "Allow Alarms & reminders permission, then try again",
                    Toast.LENGTH_LONG).show();

            Intent settingsIntent = new Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + getPackageName())
            );
            startActivity(settingsIntent);
            return;
        }

        saveReminderBtn.setEnabled(false);
        saveReminderBtn.setText("Saving...");

        final String userUid = currentUser.getUid();
        final long triggerTimeMillis = calendar.getTimeInMillis();

        // Save locally on a background thread.
        databaseExecutor.execute(() -> {
            long localReminderId;

            try {
                Reminder reminder = new Reminder();
                reminder.medicineName = medicine;
                reminder.reminderTime = reminderTime;
                reminder.status = "Scheduled";
                reminder.triggeredAt = "";
                reminder.userId = userUid;
                reminder.scheduledTimeMillis = triggerTimeMillis;

                localReminderId = AppDatabase.getInstance(
                        getApplicationContext()
                ).reminderDao().insert(reminder);

            } catch (Exception e) {
                Log.e(TAG, "Local reminder save failed", e);
                runOnUiThread(() -> {
                    saveReminderBtn.setEnabled(true);
                    saveReminderBtn.setText("Save Reminder");
                    Toast.makeText(this,
                            "Local save failed: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
                return;
            }

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }

                try {
                    Intent notificationIntent = new Intent(
                            ReminderSetupActivity.this,
                            NotificationReceiver.class
                    );
                    notificationIntent.putExtra("medicine", medicine);
                    notificationIntent.putExtra(
                            "reminderId", (int) localReminderId
                    );

                    PendingIntent pendingIntent = PendingIntent.getBroadcast(
                            ReminderSetupActivity.this,
                            (int) localReminderId,
                            notificationIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT
                                    | PendingIntent.FLAG_IMMUTABLE
                    );

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        try {
                            alarmManager.setExactAndAllowWhileIdle(
                                    AlarmManager.RTC_WAKEUP,
                                    triggerTimeMillis,
                                    pendingIntent
                            );
                        } catch (SecurityException se) {
                            Log.w(TAG, "Exact alarm permission revoked, falling back to setAndAllowWhileIdle", se);
                            alarmManager.setAndAllowWhileIdle(
                                    AlarmManager.RTC_WAKEUP,
                                    triggerTimeMillis,
                                    pendingIntent
                            );
                        }
                    } else {
                        alarmManager.setExact(
                                AlarmManager.RTC_WAKEUP,
                                triggerTimeMillis,
                                pendingIntent
                        );
                    }

                    saveToFirebase(
                            userUid,
                            medicine,
                            reminderTime,
                            triggerTimeMillis,
                            localReminderId
                    );

                } catch (Exception e) {
                    Log.e(TAG, "Alarm scheduling failed", e);
                    saveReminderBtn.setEnabled(true);
                    saveReminderBtn.setText("Save Reminder");
                    Toast.makeText(this,
                            "Saved locally, but alarm setup failed: "
                                    + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void saveToFirebase(
            String uid,
            String medicine,
            String reminderTime,
            long scheduledTimeMillis,
            long localReminderId
    ) {
        String reminderId = UUID.randomUUID().toString();

        Map<String, Object> data = new HashMap<>();
        data.put("reminderId", reminderId);
        data.put("medicineName", medicine);
        data.put("reminderTime", reminderTime);
        data.put("scheduledTimeMillis", scheduledTimeMillis);
        data.put("status", "Scheduled");
        data.put("createdAt", FieldValue.serverTimestamp());

        firestore.collection("users")
                .document(uid)
                .collection("reminders")
                .document(reminderId)
                .set(data)
                .addOnSuccessListener(unused -> {
                    Log.d(TAG, "Firebase reminder saved: " + reminderId);

                    // Update local Room record with Firestore ID
                    databaseExecutor.execute(() -> {
                        try {
                            AppDatabase.getInstance(getApplicationContext())
                                    .reminderDao()
                                    .updateFirestoreId((int) localReminderId, reminderId);
                        } catch (Exception e) {
                            Log.w(TAG, "Failed updating local firestoreId", e);
                        }
                    });

                    saveReminderBtn.setEnabled(true);
                    saveReminderBtn.setText("Save Reminder");
                    medicineName.setText("");

                    Toast.makeText(this,
                            "Reminder saved locally and to Firebase!",
                            Toast.LENGTH_LONG).show();
                })
                .addOnFailureListener(error -> {
                    Log.e(TAG, "Firebase reminder save failed", error);

                    saveReminderBtn.setEnabled(true);
                    saveReminderBtn.setText("Save Reminder");

                    Toast.makeText(this,
                            "Saved locally, but Firebase failed: "
                                    + error.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        databaseExecutor.shutdown();
    }
}

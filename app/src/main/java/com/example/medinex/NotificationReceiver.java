package com.example.medinex;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;

public class NotificationReceiver extends BroadcastReceiver {

    private static final String TAG = "NotificationReceiver";
    public static final String CHANNEL_ID = "medicine_reminder_v2";

    public static void ensureNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && context != null) {
            NotificationManager manager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

            if (manager != null && manager.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        "Medicine Reminders",
                        NotificationManager.IMPORTANCE_HIGH
                );
                channel.setDescription("Medinex scheduled medicine intake notifications");
                channel.enableVibration(true);
                channel.enableLights(true);
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) {
            return;
        }

        String medicineName = intent.getStringExtra("medicine");
        if (medicineName == null || medicineName.trim().isEmpty()) {
            medicineName = "Your Medicine";
        }

        int reminderId = intent.getIntExtra("reminderId", -1);

        String triggeredAt = new SimpleDateFormat(
                "dd MMM yyyy, hh:mm a",
                Locale.getDefault()
        ).format(new Date());

        // Update Room Database on background executor
        if (reminderId != -1) {
            final String finalMedName = medicineName;
            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    AppDatabase.getInstance(context)
                            .reminderDao()
                            .updateStatus(reminderId, "Triggered", triggeredAt);

                    // If user is currently signed in, also sync status to Firestore
                    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                    if (user != null) {
                        FirebaseFirestore.getInstance()
                                .collection("users")
                                .document(user.getUid())
                                .collection("reminders")
                                .whereEqualTo("medicineName", finalMedName)
                                .get()
                                .addOnSuccessListener(querySnapshot -> {
                                    if (querySnapshot != null && !querySnapshot.isEmpty()) {
                                        Map<String, Object> update = new HashMap<>();
                                        update.put("status", "Triggered");
                                        update.put("triggeredAt", triggeredAt);
                                        querySnapshot.getDocuments().get(0).getReference().update(update);
                                    }
                                });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error updating reminder status in database", e);
                }
            });
        }

        // Notification Channel
        ensureNotificationChannel(context);

        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (manager == null) {
            Log.e(TAG, "NotificationManager is null");
            return;
        }

        // Content PendingIntent when user clicks the notification
        Intent openIntent = new Intent(context, ReminderHistoryActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent contentPendingIntent = PendingIntent.getActivity(
                context,
                reminderId != -1 ? reminderId : (int) System.currentTimeMillis(),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle("💊 Medicine Reminder")
                        .setContentText("Time to take " + medicineName)
                        .setStyle(
                                new NotificationCompat.BigTextStyle()
                                        .bigText("Time to take " + medicineName + "\n\nPlease take your medicine now.")
                        )
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setContentIntent(contentPendingIntent)
                        .setAutoCancel(true)
                        .setDefaults(NotificationCompat.DEFAULT_ALL);

        manager.notify(
                reminderId != -1 ? reminderId : (int) System.currentTimeMillis(),
                builder.build()
        );
    }
}
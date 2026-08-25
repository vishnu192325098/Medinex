package com.example.medinex;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class NotificationReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(
            Context context,
            Intent intent) {

        // --------------------------------
        // GET MEDICINE
        // --------------------------------

        String medicineName =
                intent.getStringExtra("medicine");

        if (medicineName == null ||
                medicineName.isEmpty()) {

            medicineName = "Your Medicine";
        }


        // --------------------------------
        // GET REMINDER ID
        // --------------------------------

        int reminderId =
                intent.getIntExtra(
                        "reminderId",
                        -1
                );


        // --------------------------------
        // CURRENT TIME
        // --------------------------------

        String triggeredAt =
                new SimpleDateFormat(
                        "dd MMM yyyy, hh:mm a",
                        Locale.getDefault()
                ).format(
                        new Date()
                );


        // --------------------------------
        // UPDATE DATABASE
        // --------------------------------

        if (reminderId != -1) {

            AppDatabase
                    .getInstance(context)
                    .reminderDao()
                    .updateStatus(
                            reminderId,
                            "Triggered",
                            triggeredAt
                    );
        }


        // --------------------------------
        // NOTIFICATION CHANNEL
        // --------------------------------

        String channelId =
                "medicine_reminder_v2";

        NotificationManager manager =
                (NotificationManager)
                        context.getSystemService(
                                Context.NOTIFICATION_SERVICE
                        );


        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O) {

            NotificationChannel channel =
                    new NotificationChannel(
                            channelId,
                            "Medicine Reminders",
                            NotificationManager.IMPORTANCE_HIGH
                    );

            channel.enableVibration(true);
            channel.enableLights(true);

            manager.createNotificationChannel(
                    channel
            );
        }


        // --------------------------------
        // NOTIFICATION
        // --------------------------------

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(
                        context,
                        channelId
                )
                        .setSmallIcon(
                                android.R.drawable.ic_dialog_info
                        )
                        .setContentTitle(
                                "💊 Medicine Reminder"
                        )
                        .setContentText(
                                "Time to take "
                                        + medicineName
                        )
                        .setStyle(
                                new NotificationCompat
                                        .BigTextStyle()
                                        .bigText(
                                                "Time to take "
                                                        + medicineName
                                                        + "\n\nPlease take your medicine now."
                                        )
                        )
                        .setPriority(
                                NotificationCompat.PRIORITY_MAX
                        )
                        .setAutoCancel(true)
                        .setDefaults(
                                NotificationCompat.DEFAULT_ALL
                        );


        // --------------------------------
        // SHOW NOTIFICATION
        // --------------------------------

        manager.notify(
                reminderId != -1
                        ? reminderId
                        : (int) System.currentTimeMillis(),
                builder.build()
        );
    }
}
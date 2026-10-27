package com.example.medinex;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "MedinexBootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            return;
        }

        Log.d(TAG, "Device rebooted or package replaced. Rescheduling active medicine reminders...");

        // Reschedule reminders on a background thread
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                List<Reminder> scheduled = AppDatabase.getInstance(context)
                        .reminderDao()
                        .getAllScheduledReminders();

                if (scheduled == null || scheduled.isEmpty()) {
                    Log.d(TAG, "No scheduled reminders to restore.");
                    return;
                }

                AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                if (alarmManager == null) {
                    Log.e(TAG, "AlarmManager unavailable during boot reschedule.");
                    return;
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                        && !alarmManager.canScheduleExactAlarms()) {
                    Log.w(TAG, "Exact alarm permission not granted after boot.");
                    return;
                }

                long now = System.currentTimeMillis();

                for (Reminder reminder : scheduled) {
                    long triggerTime = reminder.scheduledTimeMillis;

                    // If scheduled time has already passed or not set, compute next occurrence from reminderTime (HH:mm)
                    if (triggerTime <= now && reminder.reminderTime != null && reminder.reminderTime.contains(":")) {
                        try {
                            String[] parts = reminder.reminderTime.split(":");
                            int hour = Integer.parseInt(parts[0].trim());
                            int minute = Integer.parseInt(parts[1].trim());

                            Calendar cal = Calendar.getInstance();
                            cal.set(Calendar.HOUR_OF_DAY, hour);
                            cal.set(Calendar.MINUTE, minute);
                            cal.set(Calendar.SECOND, 0);
                            cal.set(Calendar.MILLISECOND, 0);

                            if (cal.getTimeInMillis() <= now) {
                                cal.add(Calendar.DAY_OF_YEAR, 1);
                            }
                            triggerTime = cal.getTimeInMillis();
                        } catch (Exception e) {
                            Log.w(TAG, "Error calculating reminder time for id " + reminder.id, e);
                            continue;
                        }
                    }

                    if (triggerTime > now) {
                        Intent notifIntent = new Intent(context, NotificationReceiver.class);
                        notifIntent.putExtra("medicine", reminder.medicineName);
                        notifIntent.putExtra("reminderId", reminder.id);

                        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                                context,
                                reminder.id,
                                notifIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                        );

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            alarmManager.setExactAndAllowWhileIdle(
                                    AlarmManager.RTC_WAKEUP,
                                    triggerTime,
                                    pendingIntent
                            );
                        } else {
                            alarmManager.setExact(
                                    AlarmManager.RTC_WAKEUP,
                                    triggerTime,
                                    pendingIntent
                            );
                        }
                        Log.d(TAG, "Rescheduled reminder " + reminder.id + " (" + reminder.medicineName + ") for " + triggerTime);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to reschedule reminders on boot", e);
            }
        });
    }
}

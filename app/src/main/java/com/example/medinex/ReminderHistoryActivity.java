package com.example.medinex;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReminderHistoryActivity extends AppCompatActivity {

    private static final String TAG = "ReminderHistory";

    private TextView historyText;
    private FirebaseAuth auth;
    private FirebaseFirestore firestore;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reminder_history);

        historyText = findViewById(R.id.historyText);
        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadHistory();
    }

    private void loadHistory() {
        if (historyText == null) return;

        FirebaseUser user = auth.getCurrentUser();
        final String uid = (user != null) ? user.getUid() : null;

        historyText.setText("Loading medication reminders...");

        // Step 1: Load immediately from local Room database (offline resilience)
        executor.execute(() -> {
            try {
                List<Reminder> localReminders = AppDatabase.getInstance(this)
                        .reminderDao()
                        .getRemindersForUser(uid);

                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        renderReminders(localReminders);
                    }
                });

                // Step 2: If authenticated and online, fetch from Firestore to merge new records
                if (uid != null) {
                    syncFromFirestore(uid);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading local reminders", e);
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        historyText.setText("Error loading reminders: " + e.getMessage());
                    }
                });
            }
        });
    }

    private void syncFromFirestore(String uid) {
        firestore.collection("users")
                .document(uid)
                .collection("reminders")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot == null || querySnapshot.isEmpty()) {
                        return;
                    }

                    executor.execute(() -> {
                        boolean hasNewData = false;
                        ReminderDao dao = AppDatabase.getInstance(this).reminderDao();

                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            String firestoreId = doc.getId();
                            String medicineName = doc.getString("medicineName");
                            String reminderTime = doc.getString("reminderTime");
                            String status = doc.getString("status");
                            String triggeredAt = doc.getString("triggeredAt");
                            Long scheduledTimeMillis = doc.getLong("scheduledTimeMillis");

                            if (medicineName == null || medicineName.trim().isEmpty()) {
                                continue;
                            }

                            Reminder existing = dao.findExistingReminder(
                                    uid,
                                    firestoreId,
                                    medicineName,
                                    reminderTime
                            );

                            if (existing == null) {
                                Reminder newReminder = new Reminder();
                                newReminder.medicineName = medicineName;
                                newReminder.reminderTime = reminderTime != null ? reminderTime : "";
                                newReminder.status = status != null ? status : "Scheduled";
                                newReminder.triggeredAt = triggeredAt != null ? triggeredAt : "";
                                newReminder.userId = uid;
                                newReminder.firestoreId = firestoreId;
                                newReminder.scheduledTimeMillis = scheduledTimeMillis != null ? scheduledTimeMillis : 0;

                                dao.insert(newReminder);
                                hasNewData = true;
                            } else {
                                // If status in Firestore updated to Triggered, update Room
                                if ("Triggered".equalsIgnoreCase(status) && !"Triggered".equalsIgnoreCase(existing.status)) {
                                    dao.updateStatus(existing.id, "Triggered", triggeredAt != null ? triggeredAt : "");
                                    hasNewData = true;
                                }
                                if (existing.firestoreId == null || existing.firestoreId.isEmpty()) {
                                    dao.updateFirestoreId(existing.id, firestoreId);
                                }
                            }
                        }

                        if (hasNewData) {
                            List<Reminder> updatedList = dao.getRemindersForUser(uid);
                            runOnUiThread(() -> {
                                if (!isFinishing() && !isDestroyed()) {
                                    renderReminders(updatedList);
                                }
                            });
                        }
                    });
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Firestore sync skipped or offline: " + e.getMessage());
                    // App still displays local Room reminders!
                });
    }

    private void renderReminders(List<Reminder> reminders) {
        if (historyText == null) return;

        if (reminders == null || reminders.isEmpty()) {
            historyText.setText("No reminders saved yet.\n\nTap 'Set Medicine Reminder' on the dashboard to create your medication schedule.");
            return;
        }

        StringBuilder builder = new StringBuilder();
        builder.append("Total Reminders: ").append(reminders.size()).append("\n\n");

        for (int i = 0; i < reminders.size(); i++) {
            Reminder reminder = reminders.get(i);

            builder.append("💊 Medicine: ").append(reminder.medicineName != null ? reminder.medicineName : "Unknown").append("\n");
            builder.append("⏰ Time: ").append(reminder.reminderTime != null ? reminder.reminderTime : "--:--").append("\n");

            String statusDisplay = (reminder.status != null && !reminder.status.isEmpty()) ? reminder.status : "Scheduled";
            builder.append("📌 Status: ").append(statusDisplay).append("\n");

            if (reminder.triggeredAt != null && !reminder.triggeredAt.isEmpty()) {
                builder.append("🔔 Triggered: ").append(reminder.triggeredAt).append("\n");
            }

            if (i < reminders.size() - 1) {
                builder.append("------------------------------------\n\n");
            }
        }

        historyText.setText(builder.toString());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
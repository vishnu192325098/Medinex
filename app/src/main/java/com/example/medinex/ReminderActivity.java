
package com.example.medinex;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ReminderActivity extends AppCompatActivity {

    private static final String TAG = "MedinexReminder";

    private EditText medicineName;
    private Button saveReminderBtn;

    private FirebaseFirestore firestore;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reminder);

        medicineName = findViewById(R.id.medicineName);
        saveReminderBtn = findViewById(R.id.saveReminderBtn);

        firestore = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        if (medicineName == null || saveReminderBtn == null) {
            Log.e(TAG, "Required views missing in activity_reminder.xml");

            Toast.makeText(
                    this,
                    "Reminder screen layout error",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        saveReminderBtn.setOnClickListener(v -> {
            Log.d(TAG, "Save Reminder button clicked");

            Toast.makeText(
                    ReminderActivity.this,
                    "Reminder button clicked!",
                    Toast.LENGTH_SHORT
            ).show();

            saveReminder();
        });

        Log.d(TAG, "ReminderActivity initialized");
    }

    private void saveReminder() {

        String medicine = medicineName.getText()
                .toString()
                .trim();

        if (medicine.isEmpty()) {
            medicineName.setError("Enter medicine name");

            Toast.makeText(
                    this,
                    "Please enter a medicine name",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        FirebaseUser currentUser = auth.getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(
                    this,
                    "Please log in again before saving",
                    Toast.LENGTH_LONG
            ).show();

            Log.e(TAG, "No authenticated Firebase user");
            return;
        }

        String uid = currentUser.getUid();
        String reminderId = UUID.randomUUID().toString();

        Map<String, Object> reminder = new HashMap<>();
        reminder.put("reminderId", reminderId);
        reminder.put("medicineName", medicine);
        reminder.put("status", "active");
        reminder.put("createdAt", FieldValue.serverTimestamp());

        saveReminderBtn.setEnabled(false);
        saveReminderBtn.setText("Saving...");

        Log.d(TAG, "Saving reminder for Firebase UID: " + uid);

        firestore.collection("users")
                .document(uid)
                .collection("reminders")
                .document(reminderId)
                .set(reminder)
                .addOnSuccessListener(unused -> {

                    saveReminderBtn.setEnabled(true);
                    saveReminderBtn.setText("Save Reminder");

                    Log.d(TAG, "Reminder saved successfully: " + reminderId);

                    Toast.makeText(
                            ReminderActivity.this,
                            "Reminder saved to Firebase!",
                            Toast.LENGTH_LONG
                    ).show();

                    medicineName.setText("");
                })
                .addOnFailureListener(error -> {

                    saveReminderBtn.setEnabled(true);
                    saveReminderBtn.setText("Save Reminder");

                    Log.e(TAG, "Failed to save reminder", error);

                    Toast.makeText(
                            ReminderActivity.this,
                            "Save failed: " + error.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }
}

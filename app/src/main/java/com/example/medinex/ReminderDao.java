package com.example.medinex;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ReminderDao {

    @Insert
    long insert(Reminder reminder);

    @Query("SELECT * FROM reminders ORDER BY id DESC")
    List<Reminder> getAllReminders();

    @Query("UPDATE reminders SET status = :status, triggeredAt = :triggeredAt WHERE id = :reminderId")
    void updateStatus(
            int reminderId,
            String status,
            String triggeredAt
    );

    @Query("SELECT * FROM reminders WHERE (:userId IS NULL OR userId = :userId OR userId = '' OR userId IS NULL) ORDER BY id DESC")
    List<Reminder> getRemindersForUser(String userId);

    @Query("SELECT * FROM reminders WHERE status = 'Scheduled'")
    List<Reminder> getAllScheduledReminders();

    @Query("SELECT * FROM reminders WHERE (:userId IS NULL OR userId = :userId) AND ((firestoreId IS NOT NULL AND firestoreId = :firestoreId) OR (medicineName = :medicineName AND reminderTime = :reminderTime)) LIMIT 1")
    Reminder findExistingReminder(String userId, String firestoreId, String medicineName, String reminderTime);

    @Query("UPDATE reminders SET firestoreId = :firestoreId WHERE id = :reminderId")
    void updateFirestoreId(int reminderId, String firestoreId);
}
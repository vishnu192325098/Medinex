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
}
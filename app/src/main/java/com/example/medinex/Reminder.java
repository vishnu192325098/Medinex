package com.example.medinex;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "reminders")
public class Reminder {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public String medicineName;

    public String reminderTime;

    public String status;

    public String triggeredAt;

    public String userId;

    public String firestoreId;

    public long scheduledTimeMillis;
}
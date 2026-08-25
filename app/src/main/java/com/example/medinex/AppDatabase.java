package com.example.medinex;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(
        entities = {
                Reminder.class,
                User.class
        },
        version = 4,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static AppDatabase instance;

    public abstract ReminderDao reminderDao();

    public abstract UserDao userDao();


    public static synchronized AppDatabase getInstance(
            Context context) {

        if (instance == null) {

            instance =
                    Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "medinex_database"
                            )
                            .fallbackToDestructiveMigration()
                            .allowMainThreadQueries()
                            .build();
        }

        return instance;
    }
}
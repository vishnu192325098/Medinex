package com.example.medinex;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(
        entities = {
                Reminder.class,
                User.class
        },
        version = 5,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static AppDatabase instance;

    public abstract ReminderDao reminderDao();

    public abstract UserDao userDao();

    public static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE reminders ADD COLUMN userId TEXT");
            database.execSQL("ALTER TABLE reminders ADD COLUMN firestoreId TEXT");
            database.execSQL("ALTER TABLE reminders ADD COLUMN scheduledTimeMillis INTEGER NOT NULL DEFAULT 0");
        }
    };

    public static synchronized AppDatabase getInstance(
            Context context) {

        if (instance == null) {

            instance =
                    Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "medinex_database"
                            )
                            .addMigrations(MIGRATION_4_5)
                            .fallbackToDestructiveMigration()
                            .allowMainThreadQueries()
                            .build();
        }

        return instance;
    }
}
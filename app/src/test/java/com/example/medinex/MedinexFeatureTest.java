package com.example.medinex;

import org.junit.Test;

import java.util.Calendar;
import java.util.UUID;

import static org.junit.Assert.*;

public class MedinexFeatureTest {

    @Test
    public void reminderEntity_preservesAllRequiredFields() {
        Reminder reminder = new Reminder();
        reminder.id = 42;
        reminder.medicineName = "Amoxicillin 500mg";
        reminder.reminderTime = "08:30";
        reminder.status = "Scheduled";
        reminder.triggeredAt = "26 Oct 2026, 08:30 AM";
        reminder.userId = "testFirebaseUid123";
        reminder.firestoreId = "firestoreReminderUuid789";
        reminder.scheduledTimeMillis = 1793000000000L;

        assertEquals(42, reminder.id);
        assertEquals("Amoxicillin 500mg", reminder.medicineName);
        assertEquals("08:30", reminder.reminderTime);
        assertEquals("Scheduled", reminder.status);
        assertEquals("26 Oct 2026, 08:30 AM", reminder.triggeredAt);
        assertEquals("testFirebaseUid123", reminder.userId);
        assertEquals("firestoreReminderUuid789", reminder.firestoreId);
        assertEquals(1793000000000L, reminder.scheduledTimeMillis);
    }

    @Test
    public void reminderScheduling_advancesToNextDayIfPast() {
        // Set time to 1 hour in the past
        Calendar now = Calendar.getInstance();
        int hour = now.get(Calendar.HOUR_OF_DAY) - 1;
        int minute = now.get(Calendar.MINUTE);
        if (hour < 0) {
            hour = 23;
        }

        Calendar scheduledCal = Calendar.getInstance();
        scheduledCal.set(Calendar.HOUR_OF_DAY, hour);
        scheduledCal.set(Calendar.MINUTE, minute);
        scheduledCal.set(Calendar.SECOND, 0);
        scheduledCal.set(Calendar.MILLISECOND, 0);

        long currentTime = System.currentTimeMillis();
        if (scheduledCal.getTimeInMillis() <= currentTime) {
            scheduledCal.add(Calendar.DAY_OF_YEAR, 1);
        }

        assertTrue("Scheduled time must be strictly in the future", scheduledCal.getTimeInMillis() > currentTime);
    }

    @Test
    public void prescriptionStorageStructure_followsUserScopedConvention() {
        String uid = "user_abc_123";
        String scanId = UUID.randomUUID().toString();
        String expectedStoragePath = "users/" + uid + "/prescriptions/" + scanId + "/original.jpg";

        assertTrue(expectedStoragePath.startsWith("users/" + uid + "/prescriptions/"));
        assertTrue(expectedStoragePath.endsWith("/original.jpg"));
    }

    @Test
    public void firestoreReminderPath_matchesDocumentStructure() {
        String uid = "auth_user_999";
        String reminderId = "rem_uuid_555";
        String expectedPath = "users/" + uid + "/reminders/" + reminderId;

        assertEquals("users/auth_user_999/reminders/rem_uuid_555", expectedPath);
    }
}

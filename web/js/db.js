/**
 * Medinex Database & Storage Layer
 * Emulates Android Room Database (UserDao, ReminderDao) and SharedPreferences
 */

export class MedinexDB {
  static PREFS_KEY = "MedinexUser";
  static USERS_KEY = "medinex_users_table";
  static REMINDERS_KEY = "medinex_reminders_table";
  static WATER_KEY = "medinex_water_tracker";

  // ==========================================
  // SHARED PREFERENCES EMULATION
  // ==========================================

  static getPrefs() {
    try {
      const data = localStorage.getItem(this.PREFS_KEY);
      return data ? JSON.parse(data) : { isLoggedIn: false, userId: "", userName: "", userEmail: "", userPhone: "" };
    } catch (e) {
      return { isLoggedIn: false, userId: "", userName: "", userEmail: "", userPhone: "" };
    }
  }

  static setPrefs(prefs) {
    try {
      const current = this.getPrefs();
      const updated = { ...current, ...prefs };
      localStorage.setItem(this.PREFS_KEY, JSON.stringify(updated));
      return updated;
    } catch (e) {
      console.error("Error setting SharedPreferences", e);
    }
  }

  static clearPrefs() {
    try {
      localStorage.removeItem(this.PREFS_KEY);
    } catch (e) {
      console.error("Error clearing SharedPreferences", e);
    }
  }

  // ==========================================
  // REMINDER DAO EMULATION (Room: reminders table)
  // ==========================================

  static getAllReminders() {
    try {
      const data = localStorage.getItem(this.REMINDERS_KEY);
      if (data) {
        const list = JSON.parse(data);
        return list.sort((a, b) => b.id - a.id);
      }
    } catch (e) {}
    return [];
  }

  static insertReminder(medicineName, reminderTime) {
    const reminders = this.getAllReminders();
    const newReminder = {
      id: Date.now(),
      medicineName: medicineName.trim(),
      reminderTime: reminderTime.trim(),
      status: "Scheduled",
      triggeredAt: ""
    };
    reminders.unshift(newReminder);
    localStorage.setItem(this.REMINDERS_KEY, JSON.stringify(reminders));
    return newReminder;
  }

  static updateReminderStatus(reminderId, status, triggeredAt) {
    const reminders = this.getAllReminders();
    const target = reminders.find(r => r.id === reminderId);
    if (target) {
      target.status = status;
      target.triggeredAt = triggeredAt;
      localStorage.setItem(this.REMINDERS_KEY, JSON.stringify(reminders));
    }
  }

  // ==========================================
  // WATER TRACKER STATE
  // ==========================================

  static getWaterGlasses() {
    try {
      const today = new Date().toISOString().slice(0, 10);
      const data = localStorage.getItem(this.WATER_KEY);
      if (data) {
        const parsed = JSON.parse(data);
        if (parsed.date === today) return parsed.glasses;
      }
    } catch (e) {}
    return 0;
  }

  // ==========================================
  // MEDICINE LOOKUP CACHE (7-Day TTL)
  // ==========================================
  static MEDICINE_CACHE_KEY = "medinex_medicine_lookup_cache";
  static CACHE_TTL_MS = 7 * 24 * 60 * 60 * 1000; // 7 days

  static getMedicineCache(term) {
    if (!term) return null;
    try {
      const key = term.toLowerCase().trim();
      const raw = localStorage.getItem(this.MEDICINE_CACHE_KEY);
      if (!raw) return null;
      const cache = JSON.parse(raw);
      const entry = cache[key];
      if (entry && (Date.now() - entry.timestamp) < this.CACHE_TTL_MS) {
        return entry.data;
      }
    } catch (e) {}
    return null;
  }

  static setMedicineCache(term, data) {
    if (!term || !data) return;
    try {
      const key = term.toLowerCase().trim();
      const raw = localStorage.getItem(this.MEDICINE_CACHE_KEY);
      const cache = raw ? JSON.parse(raw) : {};
      cache[key] = {
        timestamp: Date.now(),
        data: data
      };
      // Keep cache size bounded (max 200 entries)
      const keys = Object.keys(cache);
      if (keys.length > 200) {
        delete cache[keys[0]];
      }
      localStorage.setItem(this.MEDICINE_CACHE_KEY, JSON.stringify(cache));
    } catch (e) {}
  }
}

/**
 * Medinex Firebase Service Integration
 * Integrates Firebase Authentication & Cloud Firestore (medinex-2d09e)
 * Consistent with Medinex Android Java implementation.
 */

import { initializeApp } from "https://www.gstatic.com/firebasejs/10.14.1/firebase-app.js";
import {
  getAuth,
  signInWithEmailAndPassword,
  createUserWithEmailAndPassword,
  signOut,
  onAuthStateChanged
} from "https://www.gstatic.com/firebasejs/10.14.1/firebase-auth.js";
import {
  getFirestore,
  doc,
  getDoc,
  setDoc,
  collection,
  getDocs,
  serverTimestamp
} from "https://www.gstatic.com/firebasejs/10.14.1/firebase-firestore.js";

const firebaseConfig = {
  apiKey: "AIzaSyCOCCs39PZ9UhJo5psyTjYTLMU6YkggXP0",
  authDomain: "medinex-2d09e.firebaseapp.com",
  projectId: "medinex-2d09e",
  storageBucket: "medinex-2d09e.firebasestorage.app",
  messagingSenderId: "882881654200",
  appId: "1:882881654200:web:medinex"
};

// Initialize Firebase
const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const db = getFirestore(app);

/**
 * Format Firebase Auth and Firestore errors into user-friendly messages
 */
export function formatFirebaseError(error) {
  if (!error) return "An unknown error occurred.";
  const code = error.code || "";
  const msg = error.message || "";

  switch (code) {
    case "auth/email-already-in-use":
      return "This email is already registered. Please sign in instead.";
    case "auth/invalid-email":
      return "Please enter a valid email address.";
    case "auth/weak-password":
      return "Password must be at least 6 characters long.";
    case "auth/user-not-found":
    case "auth/wrong-password":
    case "auth/invalid-credential":
      return "Incorrect email or password. Please try again.";
    case "auth/user-disabled":
      return "This account has been disabled. Please contact support.";
    case "auth/too-many-requests":
      return "Too many attempts. Access temporarily blocked. Please try again later.";
    case "auth/network-request-failed":
      return "Network connection failed. Please check your internet connection.";
    case "permission-denied":
      return "Permission denied accessing your profile records.";
    default:
      return msg.replace(/^Firebase:\s*/, "").replace(/\s*\(auth\/[^)]+\)\.?$/, "");
  }
}

/**
 * Sign in user with Firebase Authentication and fetch their Firestore profile
 */
export async function loginWithFirebase(email, password) {
  const credential = await signInWithEmailAndPassword(auth, email.trim().toLowerCase(), password);
  const user = credential.user;

  // Retrieve user document from Firestore: users/{uid}
  let profile = {
    uid: user.uid,
    email: user.email,
    name: user.displayName || user.email.split("@")[0],
    phone: ""
  };

  try {
    const docRef = doc(db, "users", user.uid);
    const docSnap = await getDoc(docRef);

    if (docSnap.exists()) {
      const data = docSnap.data();
      profile.name = data.name || profile.name;
      profile.phone = data.phone || "";
      profile.email = data.email || user.email;
    } else {
      // If profile document missing from Firestore, create it gracefully
      await setDoc(docRef, {
        uid: user.uid,
        name: profile.name,
        email: user.email,
        phone: "",
        createdAt: serverTimestamp()
      }, { merge: true });
    }
  } catch (err) {
    console.warn("Could not retrieve Firestore profile during login:", err);
  }

  return { user, profile };
}

/**
 * Register user in Firebase Authentication and store profile in Firestore users/{uid}
 * Never saves passwords to Firestore or local storage.
 */
export async function registerWithFirebase(name, email, phone, password) {
  const cleanEmail = email.trim().toLowerCase();
  const cleanName = name.trim();
  const cleanPhone = phone.trim();

  // Step 1: Create user in Firebase Authentication
  const credential = await createUserWithEmailAndPassword(auth, cleanEmail, password);
  const user = credential.user;

  // Step 2: Save user profile to Firestore under users/{uid}
  const userDocRef = doc(db, "users", user.uid);
  const profileData = {
    uid: user.uid,
    name: cleanName,
    email: cleanEmail,
    phone: cleanPhone,
    createdAt: serverTimestamp()
  };

  await setDoc(userDocRef, profileData);

  return {
    user,
    profile: {
      uid: user.uid,
      name: cleanName,
      email: cleanEmail,
      phone: cleanPhone
    }
  };
}

/**
 * Save or retry saving profile to Firestore users/{uid}
 */
export async function saveProfileToFirestore(uid, name, email, phone) {
  const userDocRef = doc(db, "users", uid);
  const profileData = {
    uid: uid,
    name: name.trim(),
    email: email.trim().toLowerCase(),
    phone: phone.trim(),
    updatedAt: serverTimestamp()
  };
  await setDoc(userDocRef, profileData, { merge: true });
  return profileData;
}

/**
 * Load user's actual Firestore profile document: users/{uid}
 */
export async function loadUserProfile(uid) {
  if (!uid) return null;
  const userDocRef = doc(db, "users", uid);
  const docSnap = await getDoc(userDocRef);
  if (docSnap.exists()) {
    return docSnap.data();
  }
  return null;
}

/**
 * Save reminder to Firestore under users/{uid}/reminders/{reminderId}
 */
export async function saveReminderToFirestore(uid, reminder) {
  if (!uid || !reminder) return;
  const remId = reminder.id ? String(reminder.id) : ("rem_" + Date.now());
  const reminderDocRef = doc(db, "users", uid, "reminders", remId);
  const data = {
    reminderId: remId,
    medicineName: reminder.medicineName,
    reminderTime: reminder.reminderTime,
    scheduledTimeMillis: reminder.scheduledTimeMillis || Date.now(),
    status: reminder.status || "Scheduled",
    createdAt: serverTimestamp()
  };
  await setDoc(reminderDocRef, data, { merge: true });
}

/**
 * Load reminders from Firestore subcollection users/{uid}/reminders
 */
export async function loadUserReminders(uid) {
  if (!uid) return [];
  try {
    const collRef = collection(db, "users", uid, "reminders");
    const snap = await getDocs(collRef);
    const list = [];
    snap.forEach((d) => {
      list.push(d.data());
    });
    return list;
  } catch (err) {
    console.warn("Could not load reminders from Firestore:", err);
    return [];
  }
}

/**
 * Save scan history record to Firestore users/{uid}/prescriptions/{scanId} and users/{uid}/scanHistory/{scanId}
 */
export async function saveScanHistoryToFirestore(uid, scanData) {
  if (!uid || !scanData) return;
  const scanId = scanData.scanId || ("scan_" + Date.now());
  const record = {
    scanId: scanId,
    medicines: scanData.medicines || [],
    medicineCount: (scanData.medicines || []).length,
    status: (scanData.medicines || []).length > 0 ? "completed" : "no_medicine_identified",
    uncertaintyStatus: scanData.uncertaintyStatus || "confident",
    rawOcrText: scanData.rawOcrText || "",
    strengthDosageText: scanData.strengthDosageText || "",
    createdAt: serverTimestamp()
  };

  try {
    const prescDoc = doc(db, "users", uid, "prescriptions", scanId);
    const histDoc = doc(db, "users", uid, "scanHistory", scanId);
    await Promise.all([
      setDoc(prescDoc, record, { merge: true }),
      setDoc(histDoc, record, { merge: true })
    ]);
  } catch (err) {
    console.warn("Could not save scan history to Firestore:", err);
  }
}

/**
 * Sign out of Firebase Authentication
 */
export async function logoutFromFirebase() {
  await signOut(auth);
}

export {
  app,
  auth,
  db,
  onAuthStateChanged
};

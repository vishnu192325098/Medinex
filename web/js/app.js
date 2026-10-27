/**
 * Medinex Application Router & Screen Orchestrator
 * Faithful, screen-by-screen replication of Medinex Android Application
 */

import { MedinexDB } from "./db.js";
import { MedicineApi } from "./api.js";
import { ImagePreprocessor } from "./imagePreprocessor.js";
import { MedicineDatasetManager, VerificationTier } from "./datasetManager.js";
import {
  auth,
  onAuthStateChanged,
  loginWithFirebase,
  registerWithFirebase,
  saveProfileToFirestore,
  loadUserProfile,
  saveReminderToFirestore,
  loadUserReminders,
  saveScanHistoryToFirestore,
  logoutFromFirebase,
  formatFirebaseError
} from "./firebase.js";

class MedinexApp {
  constructor() {
    this.screenContainer = document.getElementById("screenContainer");
    this.bottomNavBar = document.getElementById("bottomNavBar");
    this.androidToast = document.getElementById("androidToast");
    this.reminderChime = document.getElementById("reminderChime");
    this.deviceFrame = document.getElementById("deviceFrame");
    this.viewToggleBtn = document.getElementById("viewToggleBtn");
    this.viewToggleIcon = document.getElementById("viewToggleIcon");
    this.viewToggleLabel = document.getElementById("viewToggleLabel");
    this.soundToggleBtn = document.getElementById("soundToggleBtn");

    this.currentScreen = null;
    this.historyStack = [];
    this.soundEnabled = true;
    this.activeAlarmTimers = [];
    this.lastSelectedImage = null;
    this.originalSelectedImage = null;
    this.currentImageRotation = 0;
    this.currentCropBox = null;
    this.currentCropModeIndex = 0;
    this.currentEnhanceMode = "contrast";
    this.tesseractWorker = null;

    this.init();
  }

  escapeHtml(str) {
    if (!str) return "";
    return String(str)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#039;");
  }

  async init() {
    this.initDesktopControls();
    this.initBottomNav();
    this.startBackgroundReminderTicker();

    // Preload Clinical Dataset
    this.datasetManager = await MedicineDatasetManager.getInstance();

    // Synchronize authentication state with Firebase Authentication
    onAuthStateChanged(auth, async (firebaseUser) => {
      if (firebaseUser) {
        try {
          const profile = await loadUserProfile(firebaseUser.uid);
          MedinexDB.setPrefs({
            isLoggedIn: true,
            uid: firebaseUser.uid,
            userEmail: firebaseUser.email,
            userName: profile?.name || firebaseUser.displayName || firebaseUser.email.split("@")[0],
            userPhone: profile?.phone || ""
          });
        } catch (e) {
          console.warn("Could not sync Firestore profile on auth change:", e);
        }
      }
    });

    // Check auth session
    const prefs = MedinexDB.getPrefs();

    // Start with Splash Screen (like Android SplashActivity)
    this.renderSplashScreen(prefs.isLoggedIn);
  }

  // ==========================================
  // DESKTOP & VIEW CONTROLS
  // ==========================================

  initDesktopControls() {
    this.viewToggleBtn.addEventListener("click", () => {
      document.body.classList.toggle("expanded-view");
      const isExpanded = document.body.classList.contains("expanded-view");
      this.viewToggleIcon.textContent = isExpanded ? "💻" : "📱";
      this.viewToggleLabel.textContent = isExpanded ? "Wide View" : "Mobile View";
      this.showToast(isExpanded ? "Switched to Wide View (960px)" : "Switched to Mobile View (480px)");
    });

    this.soundToggleBtn.addEventListener("click", () => {
      this.soundEnabled = !this.soundEnabled;
      this.soundToggleBtn.innerHTML = this.soundEnabled ? `<span>🔔</span> Audio Enabled` : `<span>🔕</span> Audio Muted`;
      this.showToast(this.soundEnabled ? "Reminder audio chimes enabled" : "Reminder audio chimes muted");
    });
  }

  // ==========================================
  // TOAST NOTIFICATIONS (Android Toast Emulation)
  // ==========================================

  showToast(message, duration = 2500) {
    if (!this.androidToast) return;
    this.androidToast.textContent = message;
    this.androidToast.classList.add("show");

    if (this.toastTimeout) clearTimeout(this.toastTimeout);
    this.toastTimeout = setTimeout(() => {
      this.androidToast.classList.remove("show");
    }, duration);
  }

  // ==========================================
  // AUDIO & REMINDER ALARM TICKER
  // ==========================================

  playChime() {
    if (!this.soundEnabled || !this.reminderChime) return;
    try {
      this.reminderChime.currentTime = 0;
      this.reminderChime.play().catch(() => {});
    } catch (e) {}
  }

  startBackgroundReminderTicker() {
    // Check reminders every 15 seconds against current local time
    setInterval(() => {
      const now = new Date();
      const currentHH = String(now.getHours()).padStart(2, "0");
      const currentMM = String(now.getMinutes()).padStart(2, "0");
      const currentTimeStr = `${currentHH}:${currentMM}`;

      const reminders = MedinexDB.getAllReminders();
      let triggeredAny = false;

      for (const r of reminders) {
        if (r.status === "Scheduled" && r.reminderTime === currentTimeStr) {
          const timestamp = now.toLocaleDateString("en-US", {
            day: "2-digit",
            month: "short",
            year: "numeric"
          }) + ", " + now.toLocaleTimeString("en-US", {
            hour: "2-digit",
            minute: "2-digit",
            hour12: true
          });

          MedinexDB.updateReminderStatus(r.id, "Triggered", timestamp);
          this.playChime();
          this.showToast(`💊 Medicine Reminder: Time to take ${r.medicineName}!`, 6000);

          // Native Web Notification if permitted
          if ("Notification" in window && Notification.permission === "granted") {
            new Notification("💊 Medicine Reminder", {
              body: `Time to take ${r.medicineName}\n\nPlease take your medicine now.`,
              icon: "./assets/logo.png"
            });
          }

          triggeredAny = true;
        }
      }

      if (triggeredAny && this.currentScreen === "reminder_history") {
        this.renderReminderHistoryScreen();
      }
    }, 15000);
  }

  // ==========================================
  // NAVIGATION & ROUTING
  // ==========================================

  initBottomNav() {
    const navButtons = this.bottomNavBar.querySelectorAll(".nav-item");
    navButtons.forEach(btn => {
      btn.addEventListener("click", () => {
        const target = btn.dataset.screen;
        if (target) this.navigateTo(target);
      });
    });
  }

  updateBottomNav(screenId) {
    const navButtons = this.bottomNavBar.querySelectorAll(".nav-item");
    navButtons.forEach(btn => {
      if (btn.dataset.screen === screenId) {
        btn.classList.add("active");
      } else {
        btn.classList.remove("active");
      }
    });

    // Only show bottom nav for authenticated main pages
    const hideOnScreens = ["splash", "login", "register", "settings"];
    if (hideOnScreens.includes(screenId)) {
      this.bottomNavBar.style.display = "none";
    } else {
      this.bottomNavBar.style.display = "flex";
    }
  }

  navigateTo(screenId, pushHistory = true) {
    if (pushHistory && this.currentScreen && this.currentScreen !== screenId) {
      this.historyStack.push(this.currentScreen);
    }
    this.currentScreen = screenId;
    this.updateBottomNav(screenId);

    window.scrollTo({ top: 0, behavior: "smooth" });

    switch (screenId) {
      case "splash":
        this.renderSplashScreen();
        break;
      case "login":
        this.renderLoginScreen();
        break;
      case "register":
        this.renderRegisterScreen();
        break;
      case "dashboard":
        this.renderDashboardScreen();
        break;
      case "upload":
        this.renderUploadScreen();
        break;
      case "reminder_setup":
        this.renderReminderSetupScreen();
        break;
      case "reminder_history":
        this.renderReminderHistoryScreen();
        break;
      case "emergency":
        this.renderEmergencyScreen();
        break;
      case "stock":
        this.renderStockPredictionScreen();
        break;
      case "duplicate":
        this.renderDuplicateMedicineScreen();
        break;
      case "interaction":
        this.renderMedicineInteractionScreen();
        break;
      case "ai":
      case "ai_assistant":
        this.renderAiHealthAssistantScreen();
        break;
      case "health_hub":
        this.renderHealthHubScreen();
        break;
      case "about":
        this.renderAboutScreen();
        break;
      case "bmi":
        this.renderBmiScreen();
        break;
      case "tips":
        this.renderHealthTipsScreen();
        break;
      case "water":
        this.renderWaterTrackerScreen();
        break;
      case "apple":
        this.renderAppleScreen();
        break;
      case "diet":
        this.renderDietPlannerScreen();
        break;
      case "profile":
        this.renderProfileScreen();
        break;
      case "settings":
        this.renderSettingsScreen();
        break;
      default:
        this.renderDashboardScreen();
    }
  }

  goBack() {
    if (this.historyStack.length > 0) {
      const prev = this.historyStack.pop();
      this.navigateTo(prev, false);
    } else {
      this.navigateTo("dashboard", false);
    }
  }

  // ==========================================
  // SCREEN 1: SPLASH SCREEN (SplashActivity.java)
  // ==========================================

  renderSplashScreen(alreadyLoggedIn = false) {
    this.screenContainer.innerHTML = `
      <div class="screen no-nav" style="justify-content: center; align-items: center; min-height: 100vh; padding: 20px;">
        <div class="glass-card" style="width: 300px; text-align: center; padding: 32px 24px; animation: fadeIn 0.4s ease-out;">
          <div style="position: relative; width: 100px; height: 100px; margin: 0 auto;">
            <div style="position: absolute; inset: 0; border-radius: 50%; background: radial-gradient(circle, rgba(79, 195, 247, 0.4) 0%, rgba(255, 255, 255, 0) 70%);"></div>
            <img src="./assets/logo.png" alt="Medinex Logo" style="width: 90px; height: 90px; object-fit: contain; position: relative; z-index: 1;">
          </div>
          <h1 style="font-size: 34px; font-weight: 800; color: var(--color-text-primary); margin-top: 18px;">Medinex</h1>
          <p style="font-size: 16px; color: var(--color-text-secondary); margin-top: 6px;">Your AI Healthcare Companion</p>
          <div class="spinner"></div>
        </div>
      </div>
    `;

    // 2-second timer matching SplashActivity.java
    setTimeout(() => {
      const prefs = MedinexDB.getPrefs();
      if (alreadyLoggedIn || prefs.isLoggedIn || auth.currentUser) {
        this.navigateTo("dashboard", false);
      } else {
        this.navigateTo("login", false);
      }
    }, 2000);
  }

  // ==========================================
  // SCREEN 2: LOGIN (LoginActivity.java)
  // ==========================================

  renderLoginScreen() {
    this.screenContainer.innerHTML = `
      <div class="screen no-nav" style="justify-content: center; align-items: center; min-height: 100vh; padding: 24px;">
        <div class="glass-card" style="width: 100%; max-width: 420px; padding: 28px; text-align: center; box-shadow: 0 12px 32px rgba(0, 80, 160, 0.12);">

          <div style="position: relative; width: 110px; height: 110px; margin: 0 auto;">
            <div style="position: absolute; inset: 0; border-radius: 50%; background: radial-gradient(circle, rgba(79, 195, 247, 0.4) 0%, rgba(255, 255, 255, 0) 70%);"></div>
            <img src="./assets/logo.png" alt="Medinex Logo" style="width: 86px; height: 86px; position: relative; z-index: 1;">
          </div>

          <h2 style="font-size: 28px; font-weight: 800; color: var(--color-text-primary); margin-top: 18px;">Welcome Back</h2>
          <p style="font-size: 15px; color: var(--color-text-secondary); margin-bottom: 24px;">Sign in to your Medinex account</p>

          <form id="loginForm" autocomplete="on">
            <div id="loginFeedback" style="display: none; padding: 12px 14px; border-radius: 12px; font-size: 14px; margin-bottom: 16px; text-align: left; line-height: 1.4;"></div>

            <div class="glass-input-group">
              <input type="email" id="emailInput" class="glass-input" placeholder="Registered Email" required autocomplete="email">
            </div>

            <div class="glass-input-group">
              <input type="password" id="passwordInput" class="glass-input" placeholder="Password" required autocomplete="current-password">
            </div>

            <button type="submit" id="loginBtn" class="btn-glass" style="margin-top: 20px; display: flex; align-items: center; justify-content: center; gap: 8px;">
              <span id="loginBtnSpinner" class="spinner-small" style="display: none;"></span>
              <span id="loginBtnText">LOGIN</span>
            </button>
          </form>

          <div id="registerText" style="margin-top: 24px; color: var(--color-primary); font-size: 16px; font-weight: 700; cursor: pointer;">
            Create New Account
          </div>
        </div>
      </div>
    `;

    const form = document.getElementById("loginForm");
    const emailInput = document.getElementById("emailInput");
    const passwordInput = document.getElementById("passwordInput");
    const loginBtn = document.getElementById("loginBtn");
    const loginBtnText = document.getElementById("loginBtnText");
    const loginBtnSpinner = document.getElementById("loginBtnSpinner");
    const loginFeedback = document.getElementById("loginFeedback");
    const registerText = document.getElementById("registerText");

    form.addEventListener("submit", async (e) => {
      e.preventDefault();
      const email = emailInput.value.trim();
      const password = passwordInput.value.trim();

      loginFeedback.style.display = "none";
      loginFeedback.textContent = "";

      if (!email || !password) {
        this.showToast("Please enter both email and password");
        return;
      }

      if (!email.includes("@") || !email.includes(".")) {
        loginFeedback.style.display = "block";
        loginFeedback.style.background = "rgba(239, 68, 68, 0.12)";
        loginFeedback.style.border = "1px solid #EF4444";
        loginFeedback.style.color = "#DC2626";
        loginFeedback.textContent = "Please enter a valid email address.";
        return;
      }

      // Show loading state
      loginBtn.disabled = true;
      loginBtn.style.opacity = "0.75";
      loginBtnSpinner.style.display = "inline-block";
      loginBtnText.textContent = "Signing In...";

      try {
        const { user, profile } = await loginWithFirebase(email, password);

        // Store non-sensitive session cache in prefs
        MedinexDB.setPrefs({
          isLoggedIn: true,
          uid: user.uid,
          userName: profile.name || user.email.split("@")[0],
          userEmail: user.email,
          userPhone: profile.phone || ""
        });

        this.showToast(`Welcome back, ${profile.name || user.email}!`);
        this.navigateTo("dashboard", false);
      } catch (err) {
        console.error("Firebase Login Error:", err);
        const errorMsg = formatFirebaseError(err);
        loginFeedback.style.display = "block";
        loginFeedback.style.background = "rgba(239, 68, 68, 0.12)";
        loginFeedback.style.border = "1px solid #EF4444";
        loginFeedback.style.color = "#DC2626";
        loginFeedback.textContent = errorMsg;
        this.showToast(errorMsg);
      } finally {
        loginBtn.disabled = false;
        loginBtn.style.opacity = "1";
        loginBtnSpinner.style.display = "none";
        loginBtnText.textContent = "LOGIN";
      }
    });

    registerText.addEventListener("click", () => {
      this.navigateTo("register");
    });
  }

  // ==========================================
  // SCREEN 3: REGISTER (RegisterActivity.java)
  // ==========================================

  renderRegisterScreen() {
    this.screenContainer.innerHTML = `
      <div class="screen no-nav" style="justify-content: center; align-items: center; min-height: 100vh; padding: 24px;">
        <div class="glass-card" style="width: 100%; max-width: 420px; padding: 24px; text-align: center;">

          <div style="position: relative; width: 100px; height: 100px; margin: 0 auto;">
            <div style="position: absolute; inset: 0; border-radius: 50%; background: radial-gradient(circle, rgba(79, 195, 247, 0.4) 0%, rgba(255, 255, 255, 0) 70%);"></div>
            <img src="./assets/logo.png" alt="Medinex Logo" style="width: 80px; height: 80px; position: relative; z-index: 1;">
          </div>

          <h2 style="font-size: 28px; font-weight: 800; color: var(--color-text-primary); margin-top: 12px;">Create Account</h2>
          <p style="font-size: 14px; color: var(--color-text-secondary); margin-bottom: 20px;">Join your AI healthcare companion</p>

          <form id="registerForm">
            <div id="registerFeedback" style="display: none; padding: 12px 14px; border-radius: 12px; font-size: 14px; margin-bottom: 16px; text-align: left; line-height: 1.4;"></div>

            <div class="glass-input-group">
              <input type="text" id="nameInput" class="glass-input" placeholder="Full Name" required autocomplete="name">
            </div>

            <div class="glass-input-group">
              <input type="email" id="emailInput" class="glass-input" placeholder="Email Address" required autocomplete="email">
            </div>

            <div class="glass-input-group">
              <input type="tel" id="phoneInput" class="glass-input" placeholder="Phone Number (10+ digits)" required autocomplete="tel">
            </div>

            <div class="glass-input-group">
              <input type="password" id="passwordInput" class="glass-input" placeholder="Password (minimum 6 characters)" required autocomplete="new-password">
            </div>

            <button type="submit" id="registerBtn" class="btn-glass" style="margin-top: 18px; display: flex; align-items: center; justify-content: center; gap: 8px;">
              <span id="registerBtnSpinner" class="spinner-small" style="display: none;"></span>
              <span id="registerBtnText">Create Account →</span>
            </button>
          </form>

          <p style="font-size: 12px; color: var(--color-text-secondary); margin-top: 14px;">
            Secured by Firebase Authentication & Cloud Firestore
          </p>

          <div id="loginText" style="margin-top: 20px; color: var(--color-primary); font-size: 15px; font-weight: 700; cursor: pointer;">
            Already have an account? Sign In
          </div>
        </div>
      </div>
    `;

    const form = document.getElementById("registerForm");
    const nameInput = document.getElementById("nameInput");
    const emailInput = document.getElementById("emailInput");
    const phoneInput = document.getElementById("phoneInput");
    const passwordInput = document.getElementById("passwordInput");
    const registerBtn = document.getElementById("registerBtn");
    const registerBtnText = document.getElementById("registerBtnText");
    const registerBtnSpinner = document.getElementById("registerBtnSpinner");
    const registerFeedback = document.getElementById("registerFeedback");
    const loginText = document.getElementById("loginText");

    let pendingUid = null;

    form.addEventListener("submit", async (e) => {
      e.preventDefault();
      const name = nameInput.value.trim();
      const email = emailInput.value.trim().toLowerCase();
      const phone = phoneInput.value.trim();
      const password = passwordInput.value.trim();

      registerFeedback.style.display = "none";
      registerFeedback.textContent = "";

      if (!name || !email || !phone || !password) {
        this.showToast("Please fill all required fields");
        return;
      }

      if (!email.includes("@") || !email.includes(".")) {
        registerFeedback.style.display = "block";
        registerFeedback.style.background = "rgba(239, 68, 68, 0.12)";
        registerFeedback.style.border = "1px solid #EF4444";
        registerFeedback.style.color = "#DC2626";
        registerFeedback.textContent = "Please enter a valid email address.";
        return;
      }

      if (phone.length < 10) {
        registerFeedback.style.display = "block";
        registerFeedback.style.background = "rgba(239, 68, 68, 0.12)";
        registerFeedback.style.border = "1px solid #EF4444";
        registerFeedback.style.color = "#DC2626";
        registerFeedback.textContent = "Phone number must have at least 10 digits.";
        return;
      }

      if (password.length < 6) {
        registerFeedback.style.display = "block";
        registerFeedback.style.background = "rgba(239, 68, 68, 0.12)";
        registerFeedback.style.border = "1px solid #EF4444";
        registerFeedback.style.color = "#DC2626";
        registerFeedback.textContent = "Password must be at least 6 characters long.";
        return;
      }

      // If pendingUid is already set, user is retrying Firestore profile save
      if (pendingUid) {
        registerBtn.disabled = true;
        registerBtn.style.opacity = "0.75";
        registerBtnSpinner.style.display = "inline-block";
        registerBtnText.textContent = "Saving Profile...";

        try {
          await saveProfileToFirestore(pendingUid, name, email, phone);

          registerFeedback.style.display = "block";
          registerFeedback.style.background = "rgba(34, 197, 94, 0.15)";
          registerFeedback.style.border = "1px solid #22C55E";
          registerFeedback.style.color = "#15803D";
          registerFeedback.textContent = "✓ Profile successfully saved to Firestore! Redirecting...";

          MedinexDB.setPrefs({
            isLoggedIn: true,
            uid: pendingUid,
            userName: name,
            userEmail: email,
            userPhone: phone
          });

          this.showToast("Profile saved successfully!");
          setTimeout(() => this.navigateTo("dashboard", false), 1200);
        } catch (saveErr) {
          console.error("Firestore Retry Profile Save Failed:", saveErr);
          const errorMsg = formatFirebaseError(saveErr);
          registerFeedback.style.display = "block";
          registerFeedback.style.background = "rgba(239, 68, 68, 0.12)";
          registerFeedback.style.border = "1px solid #EF4444";
          registerFeedback.style.color = "#DC2626";
          registerFeedback.textContent = `Could not save profile: ${errorMsg}. Please try clicking 'Retry Saving Profile' again.`;
          registerBtn.disabled = false;
          registerBtn.style.opacity = "1";
          registerBtnSpinner.style.display = "none";
          registerBtnText.textContent = "🔄 Retry Saving Profile";
        }
        return;
      }

      // First time registration: create Auth user then Firestore profile
      registerBtn.disabled = true;
      registerBtn.style.opacity = "0.75";
      registerBtnSpinner.style.display = "inline-block";
      registerBtnText.textContent = "Creating Account...";

      try {
        const result = await registerWithFirebase(name, email, phone, password);
        pendingUid = result.user.uid;

        registerFeedback.style.display = "block";
        registerFeedback.style.background = "rgba(34, 197, 94, 0.15)";
        registerFeedback.style.border = "1px solid #22C55E";
        registerFeedback.style.color = "#15803D";
        registerFeedback.textContent = "✓ Account and profile created successfully in Firebase! Redirecting...";

        MedinexDB.setPrefs({
          isLoggedIn: true,
          uid: result.user.uid,
          userName: name,
          userEmail: email,
          userPhone: phone
        });

        this.showToast("Registration Successful!");
        setTimeout(() => this.navigateTo("dashboard", false), 1200);
      } catch (err) {
        console.error("Firebase Registration Error:", err);
        const errorMsg = formatFirebaseError(err);

        // Check if Auth succeeded but Firestore save threw an error
        if (auth.currentUser && auth.currentUser.email === email) {
          pendingUid = auth.currentUser.uid;
          registerFeedback.style.display = "block";
          registerFeedback.style.background = "rgba(245, 158, 11, 0.15)";
          registerFeedback.style.border = "1px solid #F59E0B";
          registerFeedback.style.color = "#B45309";
          registerFeedback.textContent = `Authentication succeeded, but saving your profile to Firestore failed: ${errorMsg}. Click 'Retry Saving Profile' to complete setup.`;

          registerBtn.disabled = false;
          registerBtn.style.opacity = "1";
          registerBtnSpinner.style.display = "none";
          registerBtnText.textContent = "🔄 Retry Saving Profile";
        } else {
          registerFeedback.style.display = "block";
          registerFeedback.style.background = "rgba(239, 68, 68, 0.12)";
          registerFeedback.style.border = "1px solid #EF4444";
          registerFeedback.style.color = "#DC2626";
          registerFeedback.textContent = errorMsg;

          registerBtn.disabled = false;
          registerBtn.style.opacity = "1";
          registerBtnSpinner.style.display = "none";
          registerBtnText.textContent = "Create Account →";
        }

        this.showToast(errorMsg);
      }
    });

    loginText.addEventListener("click", () => {
      this.navigateTo("login");
    });
  }

  // ==========================================
  // SCREEN 4: DASHBOARD (DashboardActivity.java)
  // ==========================================

  renderDashboardScreen() {
    const prefs = MedinexDB.getPrefs();
    const userName = prefs.userName || "User";
    const initial = userName.charAt(0).toUpperCase();

    this.screenContainer.innerHTML = `
      <div class="screen">
        <div class="scroll-content">

          <!-- HEADER -->
          <div class="screen-header-row">
            <div>
              <h1 class="screen-title" style="font-size: 28px;">Medinex</h1>
              <p class="screen-subtitle">AI Healthcare Companion</p>
            </div>
            <button id="profileCircle" class="profile-circle-btn" title="View Profile">
              ${initial}
            </button>
          </div>

          <!-- WELCOME / HERO CARD -->
          <div class="hero-header-card">
            <h2 id="welcomeText" class="hero-title">Welcome ${userName} 👋</h2>
            <p class="hero-subtitle">Take care of your health with intelligent tools.</p>
            <div class="hero-badge">● AI HEALTHCARE ACTIVE</div>
          </div>

          <!-- QUICK ACTIONS SECTION -->
          <div class="section-header-row">
            <h3 class="section-title">Quick Actions</h3>
            <span class="section-badge">10 tools</span>
          </div>

          <!-- ROW 1 & 2 GRID -->
          <div class="dashboard-grid">
            <!-- 1. UPLOAD -->
            <div id="uploadBtn" class="dash-card">
              <div class="dash-card-icon">
                <img src="./assets/icons/ic_upload.svg" alt="Scan Prescription" style="width: 44px; height: 44px;">
              </div>
              <div class="dash-card-title">Scan Prescription</div>
              <div class="dash-card-desc">AI medicine scan</div>
            </div>

            <!-- 2. REMINDER -->
            <div id="reminderBtn" class="dash-card">
              <div class="dash-card-icon">
                <img src="./assets/icons/ic_reminder.svg" alt="Reminders" style="width: 44px; height: 44px;">
              </div>
              <div class="dash-card-title">Reminders</div>
              <div class="dash-card-desc">Never miss a dose</div>
            </div>

            <!-- 3. EMERGENCY -->
            <div id="emergencyBtn" class="dash-card">
              <div class="dash-card-icon">
                <img src="./assets/icons/ic_emergency.svg" alt="Emergency" style="width: 44px; height: 44px;">
              </div>
              <div class="dash-card-title">Emergency</div>
              <div class="dash-card-desc">Quick assistance</div>
            </div>

            <!-- 4. STOCK PREDICTION -->
            <div id="stockBtn" class="dash-card">
              <div class="dash-card-icon">
                <img src="./assets/icons/ic_stock.svg" alt="Stock Prediction" style="width: 44px; height: 44px;">
              </div>
              <div class="dash-card-title">Stock Prediction</div>
              <div class="dash-card-desc">Predict refills</div>
            </div>
          </div>

          <!-- AI HEALTH ASSISTANT FEATURE BANNER -->
          <div id="aiBtn" class="ai-feature-banner">
            <div class="ai-icon-box">
              <img src="./assets/icons/ic_ai.svg" alt="AI Health Assistant" style="width: 52px; height: 52px; filter: drop-shadow(0 2px 8px rgba(0,0,0,0.15));">
            </div>
            <div class="ai-text-box">
              <div class="ai-banner-title">AI Health Assistant</div>
              <div class="ai-banner-desc">Ask about symptoms, medicines & health</div>
            </div>
            <div class="ai-arrow">→</div>
          </div>

          <!-- HEALTH TOOLS SECTION -->
          <div class="section-header-row" style="margin-top: 24px;">
            <h3 class="section-title">Health Tools</h3>
          </div>

          <!-- ROW 3 & 4 GRID -->
          <div class="tools-grid">
            <!-- 5. DUPLICATE CHECK -->
            <div id="duplicateBtn" class="tool-card">
              <div class="tool-card-icon">
                <img src="./assets/icons/ic_duplicate.svg" alt="Duplicate Check" style="width: 40px; height: 40px;">
              </div>
              <div class="tool-card-title">Duplicate Check</div>
            </div>

            <!-- 6. INTERACTION CHECKER -->
            <div id="interactionBtn" class="tool-card">
              <div class="tool-card-icon">
                <img src="./assets/icons/ic_interaction.svg" alt="Interaction Checker" style="width: 40px; height: 40px;">
              </div>
              <div class="tool-card-title">Interaction Checker</div>
            </div>

            <!-- 7. DIET PLANNER -->
            <div id="dietBtn" class="tool-card">
              <div class="tool-card-icon">
                <img src="./assets/icons/ic_ai.svg" alt="Diet Planner" style="width: 40px; height: 40px;">
              </div>
              <div class="tool-card-title">Diet Planner</div>
            </div>

            <!-- 8. HEALTH HUB -->
            <div id="healthHubBtn" class="tool-card">
              <div class="tool-card-icon">
                <img src="./assets/icons/ic_health_hub.svg" alt="Health Hub" style="width: 40px; height: 40px;">
              </div>
              <div class="tool-card-title">Health Hub</div>
            </div>
          </div>

          <!-- FOOTER -->
          <div class="branding-footer">
            MEDINEX • AI POWERED HEALTHCARE
          </div>

        </div>
      </div>
    `;

    document.getElementById("profileCircle").addEventListener("click", () => this.navigateTo("profile"));
    document.getElementById("uploadBtn").addEventListener("click", () => this.navigateTo("upload"));
    document.getElementById("reminderBtn").addEventListener("click", () => this.navigateTo("reminder_setup"));
    document.getElementById("emergencyBtn").addEventListener("click", () => this.navigateTo("emergency"));
    document.getElementById("stockBtn").addEventListener("click", () => this.navigateTo("stock"));
    document.getElementById("aiBtn").addEventListener("click", () => this.navigateTo("ai"));
    document.getElementById("duplicateBtn").addEventListener("click", () => this.navigateTo("duplicate"));
    document.getElementById("interactionBtn").addEventListener("click", () => this.navigateTo("interaction"));
    document.getElementById("dietBtn").addEventListener("click", () => this.navigateTo("diet"));
    document.getElementById("healthHubBtn").addEventListener("click", () => this.navigateTo("health_hub"));
  }

  // ==========================================
  // SCREEN 5: UPLOAD & OCR SCAN (UploadActivity.java)
  // ==========================================

  // ==========================================
  // SCREEN 5: UPLOAD & PRESCRIPTION OCR (UploadActivity.java)
  // ==========================================

  async getTesseractWorker() {
    if (!window.Tesseract) return null;
    try {
      if (!this.tesseractWorker) {
        this.tesseractWorker = await Tesseract.createWorker('eng');
      }
      return this.tesseractWorker;
    } catch (e) {
      console.warn("Could not create persistent Tesseract worker:", e);
      return null;
    }
  }

  renderUploadScreen() {
    this.screenContainer.innerHTML = `
      <div class="screen">
        <div class="scroll-content">

          <div class="top-back-bar">
            <button class="btn-back" id="backBtn" aria-label="Go Back">←</button>
            <div style="flex: 1;">
              <h2 class="screen-title" style="font-size: 24px;">AI Prescription Scanner</h2>
              <p class="screen-subtitle">Turn your prescription into intelligent medicine information</p>
            </div>
            <div style="width: 46px; height: 46px; border-radius: 50%; background: var(--cyan-accent); color: white; display: flex; align-items: center; justify-content: center; font-size: 20px; font-weight: bold;">
              ✦
            </div>
          </div>

          <!-- AI SCANNER HERO -->
          <div class="hero-header-card" style="display: flex; align-items: center; justify-content: space-between; padding: 20px;">
            <div>
              <div class="hero-title" style="font-size: 20px;">Smart Prescription Scan</div>
              <div class="hero-subtitle">Upload a medicine package or prescription for clinical AI analysis.</div>
            </div>
            <div style="font-size: 32px; color: white;">✦</div>
          </div>

          <!-- RESPONSIVE TWO COLUMN DESKTOP WRAPPER -->
          <div class="two-col-desktop" style="margin-top: 20px;">

            <!-- LEFT COLUMN: UPLOAD CONTROLS & PREVIEW -->
            <div>
              <div class="section-header-row" style="margin-top: 4px;">
                <h3 class="section-title">Upload Medicine Image</h3>
              </div>

              <!-- DROPZONE -->
              <div id="uploadCard" class="prescription-dropzone" role="button" tabindex="0" aria-label="Upload medicine image">
                <input type="file" id="fileInput" accept="image/jpeg,image/jpg,image/png,image/webp" style="display: none;">
                <img id="prescriptionImage" src="./assets/icons/ic_upload.svg" alt="Upload Icon" style="width: 70px; height: 70px; margin-bottom: 10px;">
                <img id="previewImage" class="dropzone-preview" alt="Prescription Preview">
                <div id="uploadPromptText" style="text-align: center;">
                  <div style="font-size: 17px; font-weight: 700; color: var(--color-text-primary);">Upload your prescription</div>
                  <div style="font-size: 12px; color: var(--color-text-secondary); margin-top: 4px;">JPG, PNG, or WEBP (Click or Drag & Drop)</div>
                </div>
              </div>

              <!-- IMAGE TOOLBAR (Rotate, Crop, Filter, Clear, Quality Badges) -->
              <div id="imageToolbar" class="image-toolbar" style="display: none; flex-wrap: wrap;">
                <div class="quality-badge-group" id="qualityBadges">
                  <!-- Injected dynamically -->
                </div>
                <div style="display: flex; gap: 6px; flex-wrap: wrap;">
                  <button type="button" class="btn-sm-ctrl" id="rotateBtn" title="Rotate image 90 degrees clockwise">
                    🔄 Rotate 90°
                  </button>
                  <button type="button" class="btn-sm-ctrl" id="cropBtn" title="Cycle crop & focus on medicine label">
                    ✂ Focus Crop: Full
                  </button>
                  <button type="button" class="btn-sm-ctrl" id="filterBtn" title="Toggle anti-glare foil filter">
                    💡 Filter: Balanced
                  </button>
                  <button type="button" class="btn-sm-ctrl" id="clearImageBtn" title="Remove image">
                    ❌ Clear
                  </button>
                </div>
              </div>

              <!-- PRE-LOAD SAMPLES BUTTONS -->
              <div style="margin-top: 14px; display: flex; gap: 8px; flex-wrap: wrap;">
                <button type="button" class="btn-ctrl" id="sample1Btn">💊 Sample: Dolo 650</button>
                <button type="button" class="btn-ctrl" id="sample2Btn">💊 Sample: Atorvastatin 20mg</button>
                <button type="button" class="btn-ctrl" id="sample3Btn">💊 Sample: Pantoprazole 40mg</button>
                <button type="button" class="btn-ctrl" id="sample4Btn">💊 Sample: Augmentin 625</button>
                <button type="button" class="btn-ctrl" id="sample5Btn">⚠ Sample: Aspirin + Warfarin</button>
              </div>

              <!-- PROGRESS BAR (Hidden by default) -->
              <div id="ocrProgressWrap" class="ocr-progress-wrap" style="display: none;">
                <div id="ocrProgressFill" class="ocr-progress-fill"></div>
              </div>

              <!-- SELECT BUTTON -->
              <button id="selectImageBtn" class="btn-primary-gradient" style="margin-top: 16px;">
                ＋ Select Medicine Image
              </button>

              <!-- ANALYZE BUTTON -->
              <button id="scanBtn" class="btn-glass" style="margin-top: 10px;">
                ✦ Analyze with Medinex AI
              </button>
            </div>

            <!-- RIGHT COLUMN: AI ANALYSIS REPORT -->
            <div>
              <div class="section-header-row" style="margin-top: 4px; margin-bottom: 6px;">
                <h3 class="section-title">AI Analysis Report</h3>
                <span style="font-size: 11px; font-weight: 800; color: var(--color-primary-dark);">UNIVERSAL CLINICAL VERIFICATION</span>
              </div>

              <div id="resultText" class="report-output-box" aria-live="polite">📋 Select a medicine packaging or prescription photo and tap Analyze with Medinex AI.</div>
            </div>

          </div>

          <div class="branding-footer" style="margin-top: 32px;">
            MEDINEX • AI PRESCRIPTION INTELLIGENCE
          </div>

        </div>
      </div>
    `;

    document.getElementById("backBtn").addEventListener("click", () => this.goBack());

    const fileInput = document.getElementById("fileInput");
    const uploadCard = document.getElementById("uploadCard");
    const selectImageBtn = document.getElementById("selectImageBtn");
    const scanBtn = document.getElementById("scanBtn");
    const resultText = document.getElementById("resultText");
    const prescriptionImage = document.getElementById("prescriptionImage");
    const previewImage = document.getElementById("previewImage");
    const uploadPromptText = document.getElementById("uploadPromptText");
    const imageToolbar = document.getElementById("imageToolbar");
    const qualityBadges = document.getElementById("qualityBadges");
    const rotateBtn = document.getElementById("rotateBtn");
    const cropBtn = document.getElementById("cropBtn");
    const filterBtn = document.getElementById("filterBtn");
    const clearImageBtn = document.getElementById("clearImageBtn");
    const ocrProgressWrap = document.getElementById("ocrProgressWrap");
    const ocrProgressFill = document.getElementById("ocrProgressFill");

    let activeSampleOcr = null;
    this.currentImageRotation = 0;
    this.currentCropBox = null;
    this.currentCropModeIndex = 0;
    this.currentEnhanceMode = "contrast";

    selectImageBtn.addEventListener("click", () => fileInput.click());
    uploadCard.addEventListener("click", (e) => {
      if (e.target !== rotateBtn && e.target !== cropBtn && e.target !== filterBtn && e.target !== clearImageBtn) {
        fileInput.click();
      }
    });

    // Drag and Drop
    uploadCard.addEventListener("dragover", (e) => {
      e.preventDefault();
      uploadCard.style.borderColor = "var(--color-primary)";
    });

    uploadCard.addEventListener("dragleave", () => {
      uploadCard.style.borderColor = "rgba(79, 142, 247, 0.45)";
    });

    uploadCard.addEventListener("drop", (e) => {
      e.preventDefault();
      uploadCard.style.borderColor = "rgba(79, 142, 247, 0.45)";
      if (e.dataTransfer.files?.length > 0) {
        handleFileSelection(e.dataTransfer.files[0]);
      }
    });

    fileInput.addEventListener("change", (e) => {
      if (e.target.files?.length > 0) {
        handleFileSelection(e.target.files[0]);
      }
    });

    const updateQualityBadges = (img) => {
      const q = ImagePreprocessor.assessImageQuality(img);
      qualityBadges.innerHTML = "";

      const w = img.naturalWidth || img.width;
      const h = img.naturalHeight || img.height;
      const resBadge = document.createElement("span");
      resBadge.className = `quality-badge ${q.isLowResolution ? "warn" : "good"}`;
      resBadge.textContent = q.isLowResolution ? `⚠ Low Res (${w}x${h})` : `✓ ${w}x${h}`;
      qualityBadges.appendChild(resBadge);

      const lightBadge = document.createElement("span");
      lightBadge.className = `quality-badge ${q.isTooDark || q.isOverexposed ? "warn" : "good"}`;
      lightBadge.textContent = q.isTooDark ? "⚠ Dark" : (q.isOverexposed ? "⚠ Glare" : "✓ Lighting OK");
      qualityBadges.appendChild(lightBadge);

      const sharpBadge = document.createElement("span");
      sharpBadge.className = `quality-badge ${q.isBlurry ? "warn" : "good"}`;
      sharpBadge.textContent = q.isBlurry ? "⚠ Blurry" : "✓ Sharp";
      qualityBadges.appendChild(sharpBadge);

      if (!q.isAcceptable && q.qualityNotice) {
        resultText.textContent = `✅ Image selected.\n📸 Quality Notice: ${q.qualityNotice}\n\nTap 'Analyze with Medinex AI' to continue.`;
      } else {
        resultText.textContent = `✅ Medicine image selected (${w}x${h}).\nTap 'Analyze with Medinex AI' to scan.`;
      }
    };

    const applyCropAndFilterPreview = () => {
      if (!this.originalSelectedImage) return;
      const processedUrl = ImagePreprocessor.preprocessImageForOcr(
        this.originalSelectedImage,
        this.currentImageRotation,
        this.currentCropBox,
        this.currentEnhanceMode
      );
      previewImage.src = processedUrl;
    };

    const handleFileSelection = (file) => {
      if (!file) return;

      const validTypes = ["image/jpeg", "image/jpg", "image/png", "image/webp"];
      if (!validTypes.includes(file.type.toLowerCase()) && !file.name.match(/\.(jpe?g|png|webp)$/i)) {
        this.showToast("Please upload a valid image file (JPG, PNG, or WEBP)");
        return;
      }

      if (file.size > 15 * 1024 * 1024) {
        this.showToast("File size too large. Maximum supported size is 15MB.");
        return;
      }

      activeSampleOcr = null;
      this.currentImageRotation = 0;
      this.currentCropBox = null;
      this.currentCropModeIndex = 0;
      this.currentEnhanceMode = "contrast";
      if (cropBtn) cropBtn.textContent = "✂ Focus Crop: Full";
      if (filterBtn) filterBtn.textContent = "💡 Filter: Balanced";

      const reader = new FileReader();
      reader.onload = (evt) => {
        const img = new Image();
        img.onload = () => {
          this.originalSelectedImage = img;
          this.lastSelectedImage = img;
          previewImage.src = evt.target.result;
          previewImage.style.display = "block";
          prescriptionImage.style.display = "none";
          uploadPromptText.style.display = "none";
          imageToolbar.style.display = "flex";

          updateQualityBadges(img);
        };
        img.onerror = () => {
          this.showToast("Could not read image file. Please try another image.");
        };
        img.src = evt.target.result;
      };
      reader.readAsDataURL(file);
    };

    // Rotate 90 degrees
    rotateBtn.addEventListener("click", (e) => {
      e.stopPropagation();
      if (!this.originalSelectedImage) return;

      this.currentImageRotation = (this.currentImageRotation + 90) % 360;
      applyCropAndFilterPreview();
      this.showToast(`Rotated to ${this.currentImageRotation}°`);
    });

    // Cycle Crop & Focus modes
    if (cropBtn) {
      cropBtn.addEventListener("click", (e) => {
        e.stopPropagation();
        if (!this.originalSelectedImage) return;

        const cropModes = [
          { name: "Full", box: null },
          { name: "Center 80%", box: { x: 0.1, y: 0.1, width: 0.8, height: 0.8 } },
          { name: "Close-up 60%", box: { x: 0.2, y: 0.2, width: 0.6, height: 0.6 } }
        ];

        this.currentCropModeIndex = (this.currentCropModeIndex + 1) % cropModes.length;
        const currentMode = cropModes[this.currentCropModeIndex];
        this.currentCropBox = currentMode.box;
        cropBtn.textContent = `✂ Focus Crop: ${currentMode.name}`;
        applyCropAndFilterPreview();
        this.showToast(`Crop set to: ${currentMode.name}`);
      });
    }

    // Cycle Anti-Glare / Computer Vision Filters
    if (filterBtn) {
      filterBtn.addEventListener("click", (e) => {
        e.stopPropagation();
        if (!this.originalSelectedImage) return;

        const modes = [
          { key: "contrast", label: "Balanced Contrast" },
          { key: "binarize", label: "Foil Anti-Glare" },
          { key: "invert", label: "Inverted Polarity" },
          { key: "raw", label: "Natural Canvas" }
        ];

        const curIdx = modes.findIndex(m => m.key === this.currentEnhanceMode);
        const nextMode = modes[(curIdx + 1) % modes.length];
        this.currentEnhanceMode = nextMode.key;
        filterBtn.textContent = `💡 Filter: ${nextMode.label}`;
        applyCropAndFilterPreview();
        this.showToast(`Applied ${nextMode.label}`);
      });
    }

    // Clear image
    clearImageBtn.addEventListener("click", (e) => {
      e.stopPropagation();
      this.originalSelectedImage = null;
      this.lastSelectedImage = null;
      activeSampleOcr = null;
      this.currentImageRotation = 0;
      this.currentCropBox = null;
      this.currentCropModeIndex = 0;
      this.currentEnhanceMode = "contrast";
      fileInput.value = "";
      previewImage.style.display = "none";
      prescriptionImage.style.display = "block";
      uploadPromptText.style.display = "block";
      imageToolbar.style.display = "none";
      qualityBadges.innerHTML = "";
      ocrProgressWrap.style.display = "none";
      resultText.textContent = "📋 Select a medicine packaging or prescription photo and tap Analyze with Medinex AI.";
      this.showToast("Image cleared");
    });

    // Sample buttons
    document.getElementById("sample1Btn").addEventListener("click", () => {
      activeSampleOcr = `Rx: Dr. R. Sharma MD\nPatient: John Doe, Age: 34\nDate: 25-Oct-2026\n\n1. Dolo 650mg Tablets\nParacetamol 650 mg\nDosage: 1 tablet TDS (1-0-1)\nAfter food for 5 days\n\nMfg by Micro Labs Ltd. Batch No: DL9823 Exp Date: 12/28`;
      this.drawSamplePrescription(activeSampleOcr);
      imageToolbar.style.display = "none";
      resultText.textContent = "✅ Loaded sample: Dolo 650mg (Paracetamol)\nTap 'Analyze with Medinex AI' to scan.";
    });

    document.getElementById("sample2Btn").addEventListener("click", () => {
      activeSampleOcr = `Hospital Discharge Prescription\nDr. K. Patel, Cardiologist\n\n1. Atorvastatin Tablets IP 20 mg\nEach film-coated tablet contains: Atorvastatin Calcium IP eq. to Atorvastatin 20mg\n1 tablet once daily at bedtime OD\nBatch: AT9821 Exp: 10/28 Mfg by Torrent Pharmaceuticals`;
      this.drawSamplePrescription(activeSampleOcr);
      imageToolbar.style.display = "none";
      resultText.textContent = "✅ Loaded sample: Atorvastatin 20mg (Universal Statin)\nTap 'Analyze with Medinex AI' to scan.";
    });

    document.getElementById("sample3Btn").addEventListener("click", () => {
      activeSampleOcr = `GASTRO CARE CLINIC\nDr. S. Nair, Gastroenterologist\n\nRx:\n1. Pantoprazole Gastro-resistant Tablets IP 40 mg\nComposition: Each tablet contains Pantoprazole Sodium IP 40mg\n1 tab once daily in morning on empty stomach (OD)\nBefore breakfast x 14 days\nMfg by Alkem Laboratories`;
      this.drawSamplePrescription(activeSampleOcr);
      imageToolbar.style.display = "none";
      resultText.textContent = "✅ Loaded sample: Pantoprazole 40mg (Gastro-resistant)\nTap 'Analyze with Medinex AI' to scan.";
    });

    const sample4 = document.getElementById("sample4Btn");
    if (sample4) {
      sample4.addEventListener("click", () => {
        activeSampleOcr = `City Clinic Hospital\nDr. Anita V.\n\nRx:\n1. Augmentin 625mg Tablets\nComposition: Amoxicillin 500mg and Potassium Clavulanate 125mg\n1 tablet twice daily BD after food for 5 days\nMfg by GlaxoSmithKline Pharmaceuticals`;
        this.drawSamplePrescription(activeSampleOcr);
        imageToolbar.style.display = "none";
        resultText.textContent = "✅ Loaded sample: Augmentin 625 (Amoxicillin + Clavulanic Acid)\nTap 'Analyze with Medinex AI' to scan.";
      });
    }

    const sample5 = document.getElementById("sample5Btn");
    if (sample5) {
      sample5.addEventListener("click", () => {
        activeSampleOcr = `Apex Heart Institute\nDr. S. K. Roy, Cardiologist\n\nRx:\n1. Aspirin 75mg Tablets IP\n1 tab once daily OD after lunch\n\n2. Warfarin 5mg Tablets\n1 tab OD evening\nFor 30 days`;
        this.drawSamplePrescription(activeSampleOcr);
        imageToolbar.style.display = "none";
        resultText.textContent = "✅ Loaded sample: Aspirin + Warfarin (Interaction Alert)\nTap 'Analyze with Medinex AI' to scan.";
      });
    }

    scanBtn.addEventListener("click", async () => {
      if (!this.lastSelectedImage && !activeSampleOcr) {
        this.showToast("Please select or capture a prescription or medicine package image first");
        return;
      }

      // Try Shared FastAPI AI Recognition Backend First
      if (!activeSampleOcr && (this.lastSelectedImage || this.originalSelectedImage)) {
        try {
          ocrProgressWrap.style.display = "block";
          ocrProgressFill.style.width = "30%";
          resultText.textContent = "⏳ Step 1/2: Submitting packaging image to Medinex Shared AI Backend...";

          const rawCroppedCanvas = ImagePreprocessor.cropAndRotateImage(
            this.originalSelectedImage || this.lastSelectedImage,
            this.currentCropBox,
            this.currentImageRotation
          );

          // Standardize max dimension to 1024px preserving natural RGB colors
          let targetW = rawCroppedCanvas.width;
          let targetH = rawCroppedCanvas.height;
          const maxDim = Math.max(targetW, targetH);
          let uploadCanvas = rawCroppedCanvas;
          if (maxDim > 1024) {
            const scale = 1024 / maxDim;
            targetW = Math.round(targetW * scale);
            targetH = Math.round(targetH * scale);
            uploadCanvas = document.createElement("canvas");
            uploadCanvas.width = targetW;
            uploadCanvas.height = targetH;
            const uCtx = uploadCanvas.getContext("2d");
            uCtx.drawImage(rawCroppedCanvas, 0, 0, targetW, targetH);
          }

          ocrProgressFill.style.width = "65%";
          resultText.textContent = "⏳ Step 2/2: Processing with Unified Clinical Recognition Model & Dosage Guard...";

          // Convert natural RGB Canvas to JPEG Blob (matching Android upload format)
          const imageBlob = await new Promise(resolve => uploadCanvas.toBlob(resolve, "image/jpeg", 0.85));

          const aiResponse = await MedicineApi.recognizePackagingImage(imageBlob);
          ocrProgressFill.style.width = "100%";

          setTimeout(() => {
            ocrProgressWrap.style.display = "none";
            ocrProgressFill.style.width = "0%";
          }, 350);

          if (aiResponse && (aiResponse.status === "success" || aiResponse.status === "unrecognized" || aiResponse.status === "unreadable" || aiResponse.status === "uncertain")) {
            await this.renderSharedAiRecognitionResult(aiResponse, resultText);
            return;
          }
        } catch (backendErr) {
          console.warn("Shared AI backend unavailable or offline, engaging local OCR fallback:", backendErr);
          resultText.textContent = "⏳ Shared AI backend offline. Engaging on-device fallback OCR...";
        }
      }

      let ocrText = activeSampleOcr;

      if (!ocrText && this.lastSelectedImage) {
        try {
          ocrProgressWrap.style.display = "block";
          ocrProgressFill.style.width = "15%";
          resultText.textContent = "⏳ Step 1/3: Assessing image quality and applying computer vision preprocessing...";

          const selectedMode = this.currentEnhanceMode || "contrast";
          const preprocessedDataUrl = ImagePreprocessor.preprocessImageForOcr(
            this.originalSelectedImage || this.lastSelectedImage,
            this.currentImageRotation,
            this.currentCropBox,
            selectedMode
          );

          resultText.textContent = "⏳ Step 2/3: Running Tesseract OCR engine...";
          ocrProgressFill.style.width = "40%";

          const worker = await this.getTesseractWorker();

          const runOcr = async (url) => {
            if (worker) {
              const res = await worker.recognize(url);
              return res.data?.text || "";
            } else if (window.Tesseract) {
              const res = await Tesseract.recognize(url, 'eng');
              return res.data?.text || "";
            }
            return "";
          };

          if (!worker && !window.Tesseract) {
            resultText.textContent = "⚠ OCR library is not available. Please ensure network connectivity.";
            ocrProgressWrap.style.display = "none";
            return;
          }

          // Pass 1: Primary preprocessed canvas
          ocrText = await runOcr(preprocessedDataUrl);

          // Evaluate clinical outcome of Pass 1
          const pass1Analysis = await this.datasetManager.parsePrescriptionText(ocrText, false);
          const hasVerifiedMedicine = pass1Analysis.detectedMedicines.some(
            m => m.verificationTier && m.verificationTier.title && m.verificationTier.title.includes("Verified")
          );

          // Pass 2: White-on-dark polarity inversion
          if (!hasVerifiedMedicine && selectedMode !== "invert") {
            resultText.textContent = "⏳ Running secondary label polarity pass (white-on-color label enhancement)...";
            ocrProgressFill.style.width = "65%";
            const invertedUrl = ImagePreprocessor.preprocessImageForOcr(
              this.originalSelectedImage || this.lastSelectedImage,
              this.currentImageRotation,
              this.currentCropBox,
              "invert"
            );
            const invertText = await runOcr(invertedUrl);
            const pass2Analysis = await this.datasetManager.parsePrescriptionText(invertText, false);
            const pass2HasVerified = pass2Analysis.detectedMedicines.some(
              m => m.verificationTier && m.verificationTier.title && m.verificationTier.title.includes("Verified")
            );

            if (pass2HasVerified || pass2Analysis.detectedMedicines.length > pass1Analysis.detectedMedicines.length) {
              ocrText = invertText;
            } else if (!hasVerifiedMedicine && selectedMode !== "binarize") {
              // Pass 3: Adaptive local binarization
              resultText.textContent = "⏳ Running tertiary adaptive binarization pass (cutting through foil reflection)...";
              ocrProgressFill.style.width = "80%";
              const binarizedUrl = ImagePreprocessor.preprocessImageForOcr(
                this.originalSelectedImage || this.lastSelectedImage,
                this.currentImageRotation,
                this.currentCropBox,
                "binarize"
              );
              const binarizeText = await runOcr(binarizedUrl);
              const pass3Analysis = await this.datasetManager.parsePrescriptionText(binarizeText, false);
              if (pass3Analysis.detectedMedicines.length > pass1Analysis.detectedMedicines.length) {
                ocrText = binarizeText;
              }
            }
          }

          ocrProgressFill.style.width = "95%";
          resultText.textContent = "⏳ Step 3/3: Cross-referencing clinical formularies, RxNorm & FDA registries...";

        } catch (err) {
          console.error("OCR execution error:", err);
          ocrProgressWrap.style.display = "none";
          resultText.textContent = `⚠ Could not perform OCR on image: ${err.message || "Recognition error"}\n\nPlease try capturing a clearer, well-lit photo of the medicine label.`;
          return;
        }
      }

      ocrProgressFill.style.width = "100%";
      setTimeout(() => {
        ocrProgressWrap.style.display = "none";
        ocrProgressFill.style.width = "0%";
      }, 500);

      await this.analyzeAndRenderPrescription(ocrText, resultText);
    });
  }

  /**
   * Render Shared AI Recognition Result (Matching Android API & UI schema)
   */
  async renderSharedAiRecognitionResult(aiResponse, outputElem) {
    const meds = aiResponse.detected_medicines || [];
    const uncertaintyStatus = aiResponse.uncertainty_status || "unknown";
    const warning = aiResponse.warning;
    const loosePillNotice = aiResponse.loose_pill_notice || "Loose-pill visual identification requires laboratory pill-imprint confirmation. Please scan the original box or blister strip.";
    const disclaimer = aiResponse.disclaimer || "This identification is for informational packaging recognition only and is separate from professional medical advice.";
    const rawOcr = aiResponse.extracted_text || "";
    const primaryStrength = aiResponse.strength_dosage_text || "";

    // Save scan to Firestore if user logged in
    const prefs = MedinexDB.getPrefs();
    const currentUid = auth.currentUser ? auth.currentUser.uid : prefs.uid;
    if (currentUid) {
      saveScanHistoryToFirestore(currentUid, {
        medicines: meds.map(m => ({
          name: m.name,
          activeIngredient: (m.active_ingredients || []).join(" + "),
          strength: m.strength,
          dosageForm: m.dosage_form,
          matchConfidence: m.confidence,
          dataSource: "Medinex Shared AI Packaging Model"
        })),
        rawOcrText: rawOcr,
        strengthDosageText: primaryStrength,
        uncertaintyStatus: uncertaintyStatus
      });
    }

    let tierBadge = "";
    if (uncertaintyStatus === "confident") {
      tierBadge = `<span class="clinical-tier-badge likely" style="background: #0284C7; color: white; padding: 4px 10px; border-radius: 12px; font-size: 12px; font-weight: 700;">🟢 Probable Match (${Math.round((meds[0]?.confidence || 0.85) * 100)}%)</span>`;
    } else if (uncertaintyStatus === "uncertain") {
      tierBadge = `<span class="clinical-tier-badge likely" style="background: #F59E0B; color: white; padding: 4px 10px; border-radius: 12px; font-size: 12px; font-weight: 700;">🟡 Candidate Match (Clinical Verification Advised)</span>`;
    } else {
      tierBadge = `<span class="clinical-tier-badge unconfirmed" style="background: #EF4444; color: white; padding: 4px 10px; border-radius: 12px; font-size: 12px; font-weight: 700;">🔴 Inconclusive / Unconfirmed</span>`;
    }

    let html = `<div class="clinical-results-container">`;

    // Pipeline & Experimental Model Status Notice
    if (aiResponse.visual_classifier_note) {
      html += `
        <div style="background: rgba(14, 165, 233, 0.08); border: 1px solid #0EA5E9; border-radius: 10px; padding: 10px 14px; margin-bottom: 12px; font-size: 12px; color: #0369A1; line-height: 1.4;">
          <b>ℹ Recognition Pipeline:</b> ${this.escapeHtml(aiResponse.recognition_method || "OCR Label Concordance + Dosage Guard")}<br>
          <span style="font-size: 11px; opacity: 0.9;">${this.escapeHtml(aiResponse.visual_classifier_note)}</span>
        </div>
      `;
    }

    // UNCERTAINTY / STATUS BANNER
    if (uncertaintyStatus === "uncertain" && warning) {
      html += `
        <div style="background: rgba(245, 158, 11, 0.12); border: 1px solid #F59E0B; border-radius: 12px; padding: 14px; margin-bottom: 14px; color: #B45309; font-size: 13px; line-height: 1.4;">
          <b>⚠ Uncertainty Notice:</b> ${this.escapeHtml(warning)}
        </div>
      `;
    } else if (uncertaintyStatus === "unknown") {
      html += `
        <div style="background: rgba(239, 68, 68, 0.12); border: 1px solid #EF4444; border-radius: 12px; padding: 14px; margin-bottom: 14px; color: #DC2626; font-size: 13px; line-height: 1.4;">
          <b>⚠ Unrecognized or Unreadable:</b> No verified medicine match could be confirmed. In accordance with clinical safety, dosages and names are never guessed.
        </div>
      `;
    }

    if (meds.length === 0) {
      html += `
        <div class="clinical-med-card" style="border-left: 4px solid #ef4444; padding: 18px; background: white; border-radius: 12px; box-shadow: 0 4px 16px rgba(0,0,0,0.06);">
          <div class="clinical-med-title" style="color: #b91c1c; font-size: 18px; font-weight: 800;">No Medicine Confidently Identified</div>
          <p style="font-size: 13px; color: #64748B; margin-top: 8px;">
            The uploaded image does not contain readable pharmaceutical packaging markers.
          </p>
        </div>
      `;
    } else {
      meds.forEach((m) => {
        html += `
          <div class="clinical-med-card" style="border-left: 4px solid ${uncertaintyStatus === 'confident' ? '#0284c7' : '#f59e0b'}; margin-bottom: 16px; padding: 18px; background: white; border-radius: 12px; box-shadow: 0 4px 16px rgba(0,0,0,0.06);">
            <div class="clinical-med-header" style="display: flex; justify-content: space-between; align-items: flex-start; gap: 8px;">
              <div>
                <div class="clinical-med-title" style="font-size: 20px; font-weight: 800; color: #0F172A;">${this.escapeHtml(m.name)}</div>
                <div style="font-size: 14px; color: #64748B; margin-top: 3px;">
                  Active Ingredient: <b>${this.escapeHtml((m.active_ingredients || []).join(" + ") || "Active Salt")}</b>
                </div>
              </div>
              ${tierBadge}
            </div>

            <div class="clinical-prop-grid" style="display: grid; grid-template-columns: repeat(auto-fit, minmax(130px, 1fr)); gap: 10px; margin-top: 14px; background: #F8FAFC; padding: 12px; border-radius: 8px;">
              <div class="clinical-prop-item">
                <span style="font-size: 11px; text-transform: uppercase; color: #64748B; font-weight: 700;">Strength</span><br>
                <b style="font-size: 15px; color: #0F172A;">${this.escapeHtml(m.strength || primaryStrength || "Standard Dose")}</b>
              </div>
              <div class="clinical-prop-item">
                <span style="font-size: 11px; text-transform: uppercase; color: #64748B; font-weight: 700;">Dosage Form</span><br>
                <b style="font-size: 15px; color: #0F172A;">${this.escapeHtml(m.dosage_form || "Tablet")}</b>
              </div>
              <div class="clinical-prop-item">
                <span style="font-size: 11px; text-transform: uppercase; color: #64748B; font-weight: 700;">Manufacturer</span><br>
                <b style="font-size: 15px; color: #0F172A;">${this.escapeHtml(m.manufacturer || "Not specified on packaging")}</b>
              </div>
              <div class="clinical-prop-item">
                <span style="font-size: 11px; text-transform: uppercase; color: #64748B; font-weight: 700;">Category</span><br>
                <b style="font-size: 15px; color: #0F172A;">${this.escapeHtml(m.category || "General Care")}</b>
              </div>
              <div class="clinical-prop-item">
                <span style="font-size: 11px; text-transform: uppercase; color: #64748B; font-weight: 700;">Confidence</span><br>
                <b style="font-size: 15px; color: #0F172A;">${Math.round(m.confidence * 100)}%</b>
              </div>
              <div class="clinical-prop-item">
                <span style="font-size: 11px; text-transform: uppercase; color: #64748B; font-weight: 700;">Verification Tier</span><br>
                <b style="font-size: 15px; color: #0F172A;">${this.escapeHtml(m.verification_tier || "Probable Match")}</b>
              </div>
            </div>

            ${m.evidence && m.evidence.length > 0 ? `
              <div style="margin-top: 12px; font-size: 12px; color: #475569;">
                <b>Detected Evidence:</b> ${m.evidence.map(e => this.escapeHtml(e)).join(" • ")}
              </div>
            ` : ""}
          </div>
        `;
      });
    }

    // LOOSE PILL PIPELINE SEPARATION NOTICE
    html += `
      <div style="background: rgba(59, 130, 246, 0.08); border: 1px dashed #3B82F6; border-radius: 12px; padding: 14px; margin-top: 14px; font-size: 12px; color: #1E40AF; line-height: 1.4;">
        <b>ℹ Loose-Pill Pipeline Notice:</b> ${this.escapeHtml(loosePillNotice)}
      </div>
    `;

    // RAW OCR DRAWER
    if (rawOcr) {
      html += `
        <details style="margin-top: 14px; background: rgba(0,0,0,0.02); border-radius: 8px; padding: 10px; font-size: 12px; color: #475569;">
          <summary style="cursor: pointer; font-weight: 700;">📝 View Extracted Label Text (${rawOcr.split("\n").length} lines)</summary>
          <pre style="margin-top: 8px; white-space: pre-wrap; font-family: monospace; font-size: 11px; background: white; padding: 10px; border-radius: 6px; border: 1px solid #E2E8F0;">${this.escapeHtml(rawOcr)}</pre>
        </details>
      `;
    }

    // MEDICAL SAFETY DISCLAIMER
    html += `
      <div style="margin-top: 18px; padding-top: 12px; border-top: 1px solid #E2E8F0; font-size: 11px; color: #94A3B8; text-align: center;">
        ⚖ ${this.escapeHtml(disclaimer)}
      </div>
    </div>`;

    outputElem.innerHTML = html;
  }

  drawSamplePrescription(text) {
    const canvas = document.createElement("canvas");
    canvas.width = 650;
    canvas.height = 380;
    const ctx = canvas.getContext("2d");
    ctx.fillStyle = "#FFFFFF";
    ctx.fillRect(0, 0, canvas.width, canvas.height);

    // Header strip
    ctx.fillStyle = "#0F172A";
    ctx.fillRect(0, 0, canvas.width, 24);

    ctx.fillStyle = "#1B2A41";
    ctx.font = "16px sans-serif";
    const lines = text.split("\n");
    let y = 58;
    for (const l of lines) {
      ctx.fillText(l, 30, y);
      y += 24;
    }

    const img = new Image();
    img.onload = () => {
      this.originalSelectedImage = img;
      this.lastSelectedImage = img;
      const previewImage = document.getElementById("previewImage");
      const prescriptionImage = document.getElementById("prescriptionImage");
      const uploadPromptText = document.getElementById("uploadPromptText");
      if (previewImage) {
        previewImage.src = canvas.toDataURL("image/png");
        previewImage.style.display = "block";
      }
      if (prescriptionImage) prescriptionImage.style.display = "none";
      if (uploadPromptText) uploadPromptText.style.display = "none";
    };
    img.src = canvas.toDataURL("image/png");
  }

  /**
   * Universal Structured Clinical Report Renderer:
   * 1. Awaits universal parsing & RxNorm/FDA online discovery.
   * 2. Renders structured clinical cards with verification tiers & evidence.
   * 3. Renders duplicate active salt and drug interaction warnings.
   * 4. Integrates interactive manual search and verification tool.
   * 5. Displays collapsible raw OCR debug drawer and medical safety notice.
   */
  async analyzeAndRenderPrescription(rawOcrText, outputElem) {
    const parseResult = await this.datasetManager.parsePrescriptionText(rawOcrText, true);
    let detectedMeds = parseResult.detectedMedicines;

    const renderUi = () => {
      // Build plain text report (embedded for tests & screen readers)
      let textSummary = "📋 MEDINEX MEDICINE RECOGNITION REPORT\n\n";

      if (detectedMeds.length === 0) {
        if (parseResult.partiallyRecognized) {
          const pr = parseResult.partiallyRecognized;
          textSummary += "🔬 PARTIALLY RECOGNIZED PHARMACEUTICAL FORMULA\n\n";
          textSummary += `• Active Ingredient : ${pr.activeSalt}\n`;
          textSummary += `• Strength          : ${pr.strength}\n`;
          textSummary += `• Dosage Form       : ${pr.dosageForm}\n`;
          textSummary += `• Therapeutic Category: ${pr.category}\n`;
          textSummary += "• Notice            : Brand name unconfirmed from image. Verification recommended.\n\n";
        } else {
          textSummary += "⚠ READABILITY / IDENTIFICATION NOTICE\n\n";
          textSummary += "The medicine text could not be confidently identified.\n\n";
          textSummary += "💡 Recommendations:\n";
          textSummary += "• Capture a clear, well-lit photo of the brand name and composition label.\n";
          textSummary += "• Keep the package flat and avoid reflection or shadows on the foil.\n";
          textSummary += "• Crop closely around the medicine name and active ingredients.\n";
          textSummary += "• Use the 🔄 Rotate 90° button if the text was captured sideways.\n";
          textSummary += "• Medinex strictly adheres to medical safety: uncertain text is never guessed or fabricated.\n\n";
        }
        if (parseResult.fullNormalizedOcrText) {
          textSummary += `📝 RAW OCR TEXT\n\n${parseResult.fullNormalizedOcrText}\n\n`;
        }
        textSummary += "✅ Analysis Complete";
      } else {
        textSummary += `💊 IDENTIFIED MEDICINES (${detectedMeds.length} Detected)\n\n`;
        detectedMeds.forEach((detail, index) => {
          const med = detail.item;
          const capName = this.datasetManager.capitalize(med.name);
          const capSalt = this.datasetManager.capitalize(med.activeSalt);
          textSummary += "--------------------------------------------------\n";
          textSummary += `Product ${index + 1}: ${capName}\n`;
          textSummary += `Status   : ${detail.verificationTier.title}\n`;
          textSummary += "--------------------------------------------------\n";
          textSummary += `• Product Name       : ${capName}\n`;
          textSummary += `• Generic / Salt     : ${capSalt}\n`;
          textSummary += `• Strength           : ${detail.strength || "Not detected"}\n`;
          textSummary += `• Dosage Form        : ${detail.dosageForm || "Tablet"}\n`;
          textSummary += `• Manufacturer       : ${detail.manufacturer || "Not specified on packaging"}\n`;
          textSummary += `• Category           : ${med.category || "General Care"}\n`;
          textSummary += `• Indication         : ${med.indication || "Prescription medicine"}\n`;
          textSummary += `• Uses               : ${med.uses || med.indication}\n`;
          textSummary += `• Match Confidence   : ${Math.round(detail.matchConfidence * 100)}%\n`;
          textSummary += `• Data Source        : ${detail.dataSource}\n`;
          textSummary += `• Online Verification: ${detail.onlineMatchInfo || detail.dataSource}\n\n`;
        });
      }

      // Build rich HTML output
      let html = `<div style="display: none;" class="sr-only">${textSummary}</div>`;
      html += `<div class="clinical-results-container">`;

      if (detectedMeds.length === 0) {
        if (parseResult.partiallyRecognized) {
          const pr = parseResult.partiallyRecognized;
          html += `
            <div class="clinical-med-card" style="border-left: 4px solid #f59e0b;">
              <div class="clinical-med-header">
                <div class="clinical-med-title-wrap">
                  <div class="clinical-med-title" style="color: #b45309;">🔬 Partially Recognized Active Formula</div>
                  <div class="clinical-med-salt-sub">Generic Formula: <b>${pr.activeSalt}</b> | Strength: <b>${pr.strength}</b></div>
                </div>
                <span class="clinical-tier-badge likely">🟡 Moderate Confidence</span>
              </div>
              <div class="clinical-prop-grid" style="margin-top: 10px;">
                <div class="clinical-prop-item">
                  <span class="clinical-prop-label">Generic / Salt</span>
                  <span class="clinical-prop-val">${pr.activeSalt}</span>
                </div>
                <div class="clinical-prop-item">
                  <span class="clinical-prop-label">Strength</span>
                  <span class="clinical-prop-val">${pr.strength}</span>
                </div>
                <div class="clinical-prop-item">
                  <span class="clinical-prop-label">Dosage Form</span>
                  <span class="clinical-prop-val">${pr.dosageForm}</span>
                </div>
                <div class="clinical-prop-item">
                  <span class="clinical-prop-label">Brand Status</span>
                  <span class="clinical-prop-val" style="color: #d97706; font-weight: 700;">Unconfirmed from image</span>
                </div>
              </div>
              <p style="font-size: 13px; color: #475569; margin: 12px 0 6px 0;">
                Packaging text indicates active generic ingredient <b>${pr.activeSalt} ${pr.strength}</b>. You can confirm or correct this medicine using the search box below:
              </p>
            </div>
          `;
        } else {
          html += `
            <div class="clinical-med-card" style="border-left: 4px solid #ef4444;">
              <div class="clinical-med-header">
                <div class="clinical-med-title-wrap">
                  <div class="clinical-med-title" style="color: #b91c1c;">⚠ Medicine Not Confidently Identified</div>
                  <div class="clinical-med-salt-sub">No verified pharmaceutical match found with high confidence</div>
                </div>
                <span class="clinical-tier-badge unconfirmed">🔴 Unverified</span>
              </div>
              <div style="font-size: 13px; line-height: 1.5; color: #475569;">
                <p><b>Clinical Recommendations:</b></p>
                <ul style="margin: 6px 0 0 18px; padding: 0;">
                  <li>Ensure the medicine packaging name and composition are clearly focused.</li>
                  <li>Avoid silver foil glare or direct flash reflecting off blister strips.</li>
                  <li>Use <b>✂ Focus Crop</b> to isolate just the medicine title and strength.</li>
                  <li>Use <b>🔄 Rotate 90°</b> if the packaging text is oriented sideways.</li>
                  <li><b>Zero Hallucination Guarantee:</b> Medinex never invents or guesses medications when text is uncertain.</li>
                </ul>
              </div>
            </div>
          `;
        }
      } else {
        html += `
          <details class="pipeline-stages-drawer" style="margin-bottom: 14px; background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 10px 14px;">
            <summary style="font-weight: 700; font-size: 13px; color: #334155; cursor: pointer;">
              🔬 Pipeline Diagnostic Stages & Verification Evidence
            </summary>
            <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 10px; margin-top: 12px; font-size: 12px;">
              <div style="background: #ffffff; border: 1px solid #cbd5e1; border-radius: 6px; padding: 8px 12px;">
                <div style="font-weight: 700; color: #475569;">1. OCR Text Extraction</div>
                <div style="color: #16a34a; font-weight: 700; margin-top: 2px;">SUCCESS</div>
                <div style="color: #64748b; font-size: 11px;">${parseResult.pipelineStages?.ocrExtraction?.characterCount || 0} characters across ${parseResult.pipelineStages?.ocrExtraction?.linesParsed || 0} lines</div>
              </div>
              <div style="background: #ffffff; border: 1px solid #cbd5e1; border-radius: 6px; padding: 8px 12px;">
                <div style="font-weight: 700; color: #475569;">2. Clinical Entity Extraction</div>
                <div style="color: #16a34a; font-weight: 700; margin-top: 2px;">SUCCESS</div>
                <div style="color: #64748b; font-size: 11px;">${parseResult.pipelineStages?.candidateGeneration?.candidatesFound || 0} candidate tokens evaluated</div>
              </div>
              <div style="background: #ffffff; border: 1px solid #cbd5e1; border-radius: 6px; padding: 8px 12px;">
                <div style="font-weight: 700; color: #475569;">3. Formulary & Online Registry</div>
                <div style="color: #16a34a; font-weight: 700; margin-top: 2px;">SUCCESS</div>
                <div style="color: #64748b; font-size: 11px;">Sources: ${(parseResult.pipelineStages?.databaseLookup?.sources || ['Medinex Local Formulary']).join(', ')}</div>
              </div>
              <div style="background: #ffffff; border: 1px solid #cbd5e1; border-radius: 6px; padding: 8px 12px;">
                <div style="font-weight: 700; color: #475569;">4. Strength & Safety Audit</div>
                <div style="color: ${parseResult.pipelineStages?.productVerification?.status === 'VERIFIED' ? '#16a34a' : (parseResult.pipelineStages?.productVerification?.status === 'STRENGTH_MISMATCH' ? '#dc2626' : '#d97706')}; font-weight: 700; margin-top: 2px;">
                  ${parseResult.pipelineStages?.productVerification?.status || 'PROCESSED'}
                </div>
                <div style="color: #64748b; font-size: 11px;">Zero-hallucination concordance check</div>
              </div>
            </div>
          </details>

          <div style="font-size: 16px; font-weight: 800; color: var(--color-primary-dark); margin-bottom: 2px;">
            💊 Identified Medicines (${detectedMeds.length} Detected)
          </div>
        `;

        detectedMeds.forEach((detail) => {
          const med = detail.item;
          const capName = this.datasetManager.capitalize(med.name);
          const capSalt = this.datasetManager.capitalize(med.activeSalt);
          const tierTag = detail.verificationTier.tag || (detail.matchConfidence >= 0.85 ? "verified" : "likely");

          html += `
            <div class="clinical-med-card">
              ${detail.strengthWarning ? `
                <div class="clinical-alert-banner high-risk" style="background: #fef2f2; border: 1px solid #f87171; color: #991b1b; padding: 10px 14px; border-radius: 8px; margin-bottom: 12px; font-size: 13px;">
                  <b>⚠ STRENGTH DISCREPANCY DETECTED:</b><br>
                  ${detail.strengthWarning}<br>
                  <span style="font-size: 12px; opacity: 0.9;">Product verification revoked. Confidence penalized to protect patient safety.</span>
                </div>
              ` : ""}

              <div class="clinical-med-header">
                <div class="clinical-med-title-wrap">
                  <div class="clinical-med-title">${capName}</div>
                  <div class="clinical-med-salt-sub">Generic Formula: <b>${capSalt}</b></div>
                </div>
                <div style="display: flex; flex-direction: column; align-items: flex-end; gap: 4px;">
                  <span class="clinical-tier-badge ${tierTag}">${detail.verificationTier.title}</span>
                  <span style="font-size: 11px; font-weight: 700; color: #64748b;">Match: ${Math.round(detail.matchConfidence * 100)}%</span>
                </div>
              </div>

              <div class="clinical-prop-grid">
                <div class="clinical-prop-item">
                  <span class="clinical-prop-label">Generic / Salt</span>
                  <span class="clinical-prop-val">${capSalt}</span>
                </div>
                <div class="clinical-prop-item">
                  <span class="clinical-prop-label">Strength</span>
                  <span class="clinical-prop-val">
                    ${detail.strength || "Standard"}
                    ${detail.strengthStatus === "VALIDATED" ? `<span style="display:inline-block; margin-left:6px; font-size:11px; font-weight:700; color:#15803d; background:#dcfce7; padding:2px 6px; border-radius:4px;">✓ Approved</span>` : ""}
                    ${detail.strengthStatus === "MISMATCH" ? `<span style="display:inline-block; margin-left:6px; font-size:11px; font-weight:700; color:#b91c1c; background:#fee2e2; padding:2px 6px; border-radius:4px;">⚠ Discrepant</span>` : ""}
                    ${detail.strengthStatus === "UNCONFIRMED" ? `<span style="display:inline-block; margin-left:6px; font-size:11px; font-weight:600; color:#64748b; background:#f1f5f9; padding:2px 6px; border-radius:4px;">Unconfirmed</span>` : ""}
                  </span>
                </div>
                <div class="clinical-prop-item">
                  <span class="clinical-prop-label">Dosage Form</span>
                  <span class="clinical-prop-val">${detail.dosageForm || "Tablet"}</span>
                </div>
                <div class="clinical-prop-item">
                  <span class="clinical-prop-label">Manufacturer</span>
                  <span class="clinical-prop-val">${detail.manufacturer || "Not specified on packaging"}</span>
                </div>
                <div class="clinical-prop-item">
                  <span class="clinical-prop-label">Therapeutic Indication</span>
                  <span class="clinical-prop-val">${med.indication || "Prescription medicine"}</span>
                </div>
                <div class="clinical-prop-item">
                  <span class="clinical-prop-label">Clinical Source</span>
                  <span class="clinical-prop-val" style="font-size: 11px; color: var(--color-primary-dark);">${detail.dataSource}</span>
                </div>
              </div>

              ${detail.evidence && detail.evidence.length > 0 ? `
                <div class="clinical-evidence-box">
                  <div class="clinical-evidence-title">🔍 Verification Evidence & Matching Signals</div>
                  <ul class="clinical-evidence-list">
                    ${detail.evidence.map(e => `<li>${e}</li>`).join("")}
                  </ul>
                </div>
              ` : ""}

              <div class="clinical-med-actions">
                <button type="button" class="btn-card-action btn-add-reminder" data-med="${capName}" data-strength="${detail.strength || ''}">
                  ➕ Add to Reminders
                </button>
                <button type="button" class="btn-card-action secondary btn-check-inter" data-med="${capName}">
                  ⚡ Check Interactions
                </button>
              </div>
            </div>
          `;
        });
      }

      // Duplicate Active Salt Alerts
      const duplicates = this.datasetManager.checkDuplicates(detectedMeds);
      if (duplicates.length > 0) {
        html += `<div class="clinical-alert-banner high-risk">
          <b>⚠ DUPLICATE ACTIVE INGREDIENT ALERTS</b>`;
        duplicates.forEach(dup => {
          html += `<div>🔴 <b>${this.datasetManager.capitalize(dup.med1.name)}</b> + <b>${this.datasetManager.capitalize(dup.med2.name)}</b><br>
          Shared Active Ingredient: <b>${dup.sharedSalt}</b> (Risk: Overdose / Toxicity)</div>`;
        });
        html += `</div>`;
      }

      // Drug Interaction Alerts
      const interactions = this.datasetManager.checkInteractions(detectedMeds);
      if (interactions.length > 0) {
        html += `<div class="clinical-alert-banner high-risk">
          <b>⚠ POTENTIAL DRUG INTERACTION DETECTED</b>`;
        interactions.forEach(inter => {
          html += `<div>🔴 <b>${this.datasetManager.capitalize(inter.drug1)}</b> + <b>${this.datasetManager.capitalize(inter.drug2)}</b> [Risk: ${inter.riskLevel}]<br>
          ${inter.description}<br><em>${inter.recommendation}</em></div>`;
        });
        html += `</div>`;
      }

      // Interactive Manual Search & Verification Box
      html += `
        <div class="manual-search-box">
          <div class="manual-search-title">🔍 Manual Search & Medicine Name Correction</div>
          <p style="font-size: 12px; color: #64748b; margin: 0;">If packaging text was partially obscured or you wish to verify an unfamiliar brand, search the universal NLM RxNorm and FDA registries:</p>
          <div class="manual-search-input-group">
            <input type="text" id="manualSearchInput" class="manual-search-input" placeholder="Type medicine or salt name (e.g. Atorvastatin 20mg)...">
            <button type="button" id="manualSearchBtn" class="btn-search-verify">Verify Medicine</button>
          </div>
          <div id="manualSearchResultWrap" style="margin-top: 10px; display: none;"></div>
        </div>
      `;

      // Collapsible Raw OCR Drawer
      html += `
        <details class="raw-ocr-drawer">
          <summary>📝 View Raw Extracted OCR Text & Unmatched Lines</summary>
          <pre>${parseResult.fullNormalizedOcrText || "No text extracted."}</pre>
          ${parseResult.unmatchedOcrLines && parseResult.unmatchedOcrLines.length > 0 ? `
            <div style="margin-top: 8px; font-weight: bold; color: #94a3b8;">Unmatched Background Lines:</div>
            <pre>${parseResult.unmatchedOcrLines.join("\n")}</pre>
          ` : ""}
        </details>
      `;

      // Medical Disclaimer Box
      html += `
        <div class="medical-disclaimer-card">
          <div class="medical-disclaimer-icon">⚕</div>
          <div>
            <b>Medinex Medical Safety Notice:</b> Automated OCR analysis and database cross-referencing are supportive clinical tools and cannot replace professional medical judgement. Always verify dosages, contraindications, and administration timings with a licensed healthcare practitioner or registered pharmacist before taking any medication.
          </div>
        </div>
      `;

      html += `</div>`;

      outputElem.innerHTML = html;

      // Wire interactive buttons
      outputElem.querySelectorAll(".btn-add-reminder").forEach(btn => {
        btn.addEventListener("click", () => {
          const medName = btn.getAttribute("data-med");
          this.navigateTo("reminder_setup");
          setTimeout(() => {
            const input = document.getElementById("medNameInput");
            if (input) input.value = medName;
          }, 100);
        });
      });

      outputElem.querySelectorAll(".btn-check-inter").forEach(btn => {
        btn.addEventListener("click", () => {
          this.navigateTo("interaction");
        });
      });

      const manualInput = document.getElementById("manualSearchInput");
      const manualBtn = document.getElementById("manualSearchBtn");
      const manualResultWrap = document.getElementById("manualSearchResultWrap");

      if (manualBtn && manualInput) {
        manualBtn.addEventListener("click", async () => {
          const val = manualInput.value.trim();
          if (!val) {
            this.showToast("Please enter a medicine or active ingredient name");
            return;
          }
          manualBtn.textContent = "Searching...";
          manualBtn.disabled = true;
          try {
            const resolved = await this.datasetManager.searchManualMedicine(val);
            if (resolved) {
              this.showToast(`Found: ${this.datasetManager.capitalize(resolved.item.name)}`);

              if (manualResultWrap) {
                manualResultWrap.style.display = "block";
                manualResultWrap.innerHTML = `
                  <div style="background: #ffffff; border: 1px solid #cbd5e1; border-radius: 8px; padding: 12px; margin-top: 8px;">
                    <div style="display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 6px;">
                      <div>
                        <div style="font-weight: 800; font-size: 15px; color: var(--color-primary-dark);">
                          ${this.datasetManager.capitalize(resolved.item.name)}
                        </div>
                        <div style="font-size: 12px; color: #64748b;">
                          Active Generic: <b>${this.datasetManager.capitalize(resolved.item.activeSalt)}</b> | Strength: <b>${resolved.item.strength || "Standard"}</b>
                        </div>
                      </div>
                      <span class="clinical-tier-badge ${resolved.verificationTier.tag || 'likely'}" style="font-size: 11px;">
                        ${resolved.matchType}
                      </span>
                    </div>
                    <div style="font-size: 12px; color: #475569; margin-bottom: 8px;">
                      Source: <b>${resolved.dataSource}</b>
                    </div>
                    ${resolved.strengthWarning ? `
                      <div style="background: #fee2e2; color: #991b1b; padding: 6px 10px; border-radius: 4px; font-size: 12px; margin-bottom: 8px;">
                        ${resolved.strengthWarning}
                      </div>
                    ` : ""}
                    <button type="button" id="btnAcceptManualMedicine" class="btn-card-action" style="font-size: 12px; padding: 6px 12px;">
                      ✓ Add to Detected Medicines
                    </button>
                  </div>
                `;

                const btnAccept = document.getElementById("btnAcceptManualMedicine");
                if (btnAccept) {
                  btnAccept.addEventListener("click", () => {
                    detectedMeds.push({
                      item: resolved.item,
                      detectedNameInOcr: val,
                      strength: resolved.item.strength,
                      strengthStatus: resolved.strengthStatus,
                      strengthWarning: resolved.strengthWarning,
                      dosageForm: resolved.item.dosageForm || "Tablet",
                      manufacturer: resolved.item.manufacturer || "Not specified on packaging",
                      matchConfidence: resolved.isExactMatch ? 0.95 : 0.85,
                      verificationTier: resolved.verificationTier,
                      dataSource: resolved.dataSource,
                      evidence: resolved.evidence,
                      isOnlineDiscovered: !!resolved.item.isOnlineDiscovered
                    });
                    renderUi();
                  });
                }
              }

              manualBtn.textContent = "Verify Medicine";
              manualBtn.disabled = false;
            } else {
              this.showToast("No official medical concept found for '" + val + "'");
              if (manualResultWrap) {
                manualResultWrap.style.display = "block";
                manualResultWrap.innerHTML = `
                  <div style="background: #fef2f2; border: 1px solid #fecaca; border-radius: 6px; padding: 10px; font-size: 12px; color: #991b1b;">
                    No recognized pharmaceutical match found for "<b>${val}</b>". Please check spelling or enter the active chemical salt name.
                  </div>
                `;
              }
              manualBtn.textContent = "Verify Medicine";
              manualBtn.disabled = false;
            }
          } catch (e) {
            this.showToast("Lookup failed: " + e.message);
            manualBtn.textContent = "Verify Medicine";
            manualBtn.disabled = false;
          }
        });
      }
    };

    renderUi();
  }


  // ==========================================
  // SCREEN 6: REMINDER SETUP (ReminderSetupActivity.java)
  // ==========================================

  renderReminderSetupScreen() {
    this.screenContainer.innerHTML = `
      <div class="screen theme-light-blue">
        <div class="scroll-content">

          <div class="top-back-bar">
            <button class="btn-back" id="backBtn">←</button>
            <div style="flex: 1;">
              <h2 class="screen-title">💊 Set Medicine Reminder</h2>
              <p class="screen-subtitle">Create a reminder for your medicine</p>
            </div>
          </div>

          <!-- RESPONSIVE TWO COLUMN DESKTOP WRAPPER -->
          <div class="two-col-desktop" style="margin-top: 15px;">
            <div>
              <!-- MEDICINE NAME CARD -->
              <div class="health-hub-card">
                <label class="glass-input-label">💊 Medicine Name</label>
                <input type="text" id="medicineName" class="glass-input" placeholder="Enter medicine name" style="margin-top: 8px;">
              </div>

              <!-- TIME CARD -->
              <div class="health-hub-card" style="margin-top: 18px;">
                <label class="glass-input-label">⏰ Reminder Time</label>

                <div class="time-picker-card">
                  <select id="timeHour" class="time-select">
                    ${Array.from({ length: 12 }, (_, i) => {
                      const h = i + 1;
                      return `<option value="${h}" ${h === 8 ? "selected" : ""}>${String(h).padStart(2, "0")}</option>`;
                    }).join("")}
                  </select>
                  <span style="font-size: 20px; font-weight: 800; color: var(--text-health-hub);">:</span>
                  <select id="timeMinute" class="time-select">
                    ${Array.from({ length: 60 }, (_, i) => {
                      return `<option value="${i}" ${i === 0 ? "selected" : ""}>${String(i).padStart(2, "0")}</option>`;
                    }).join("")}
                  </select>
                  <select id="timeAmPm" class="time-select">
                    <option value="AM" selected>AM</option>
                    <option value="PM">PM</option>
                  </select>
                </div>
              </div>

              <!-- SAVE BUTTON -->
              <button id="saveReminderBtn" class="btn-primary-gradient" style="margin-top: 20px; height: 56px;">
                Save Reminder
              </button>
            </div>

            <div>
              <!-- ACTIVE SCHEDULE SUMMARY -->
              <div class="health-hub-card">
                <div style="font-size: 16px; font-weight: 700; color: var(--text-health-hub); margin-bottom: 12px;">
                  📋 Active Schedule Overview
                </div>
                <div id="quickSchedulePreview" style="font-size: 13px; color: #475569; line-height: 1.6;">
                  <!-- Injected dynamically -->
                </div>
              </div>

              <!-- HISTORY BUTTON -->
              <button id="historyBtn" class="btn-secondary-card" style="margin-top: 16px;">
                📋 View Full Reminder History
              </button>
            </div>
          </div>

        </div>
      </div>
    `;

    document.getElementById("backBtn").addEventListener("click", () => this.goBack());

    const medicineName = document.getElementById("medicineName");
    const timeHour = document.getElementById("timeHour");
    const timeMinute = document.getElementById("timeMinute");
    const timeAmPm = document.getElementById("timeAmPm");
    const saveReminderBtn = document.getElementById("saveReminderBtn");
    const historyBtn = document.getElementById("historyBtn");
    const quickSchedulePreview = document.getElementById("quickSchedulePreview");

    const refreshSchedulePreview = () => {
      if (!quickSchedulePreview) return;
      const reminders = MedinexDB.getAllReminders();
      const scheduled = reminders.filter(r => r.status === "Scheduled");
      if (scheduled.length === 0) {
        quickSchedulePreview.innerHTML = `
          <div style="text-align: center; padding: 24px 12px; color: #94A3B8;">
            <div style="font-size: 32px; margin-bottom: 8px;">⏰</div>
            <div style="font-weight: 600; color: #475569;">No active reminders scheduled yet</div>
            <div style="font-size: 12px; margin-top: 4px;">Set a medicine name and time to receive reminders.</div>
          </div>
        `;
      } else {
        quickSchedulePreview.innerHTML = scheduled.slice(0, 5).map(r => `
          <div style="display: flex; justify-content: space-between; align-items: center; padding: 10px 0; border-bottom: 1px solid rgba(0,0,0,0.06);">
            <div>
              <div style="font-weight: 700; color: #1E293B;">💊 ${r.medicineName}</div>
              <div style="font-size: 12px; color: #64748B;">Scheduled daily</div>
            </div>
            <div style="font-weight: 800; color: var(--color-primary-dark); font-size: 14px;">
              ${r.reminderTime}
            </div>
          </div>
        `).join("");
      }
    };
    refreshSchedulePreview();

    saveReminderBtn.addEventListener("click", () => {
      const name = medicineName.value.trim();
      if (!name) {
        this.showToast("Please enter medicine name");
        return;
      }

      // Convert 12h to 24h format HH:mm
      let h = parseInt(timeHour.value, 10);
      const m = String(timeMinute.value).padStart(2, "0");
      const ampm = timeAmPm.value;

      if (ampm === "PM" && h < 12) h += 12;
      if (ampm === "AM" && h === 12) h = 0;
      const time24 = `${String(h).padStart(2, "0")}:${m}`;

      const newReminder = MedinexDB.insertReminder(name, time24);

      // Sync reminder to Firestore users/{uid}/reminders/{reminderId}
      if (auth.currentUser) {
        saveReminderToFirestore(auth.currentUser.uid, newReminder).catch(e => {
          console.warn("Firestore reminder sync error:", e);
        });
      }

      // Request browser notifications permission
      if ("Notification" in window && Notification.permission !== "granted") {
        Notification.requestPermission();
      }

      this.showToast(`Reminder saved for ${time24}`);
      medicineName.value = "";
      refreshSchedulePreview();
    });

    historyBtn.addEventListener("click", () => {
      this.navigateTo("reminder_history");
    });
  }

  // ==========================================
  // SCREEN 7: REMINDER HISTORY (ReminderHistoryActivity.java)
  // ==========================================

  async renderReminderHistoryScreen() {
    let reminders = MedinexDB.getAllReminders();

    // Fetch and merge cloud reminders from Firestore users/{uid}/reminders
    const currentUid = auth.currentUser ? auth.currentUser.uid : MedinexDB.getPrefs().uid;
    if (currentUid) {
      try {
        const cloudReminders = await loadUserReminders(currentUid);
        if (cloudReminders && cloudReminders.length > 0) {
          const existingIds = new Set(reminders.map(r => String(r.id)));
          cloudReminders.forEach(cr => {
            const idStr = String(cr.reminderId || cr.id);
            if (!existingIds.has(idStr)) {
              reminders.push({
                id: idStr,
                medicineName: cr.medicineName || "Medicine",
                reminderTime: cr.reminderTime || "00:00",
                status: cr.status || "Scheduled",
                triggeredAt: cr.triggeredAt || ""
              });
              existingIds.add(idStr);
            }
          });
        }
      } catch (e) {
        console.warn("Could not sync Firestore reminders:", e);
      }
    }

    let historyText = `Total Reminders Found: ${reminders.length}\n\n`;
    if (reminders.length === 0) {
      historyText += "No reminders saved yet.";
    } else {
      reminders.forEach(r => {
        historyText += `💊 Medicine: ${r.medicineName}\n`;
        historyText += `⏰ Time: ${r.reminderTime}\n`;
        historyText += `📌 Status: ${r.status || "Scheduled"}\n`;
        if (r.triggeredAt) {
          historyText += `🔔 Triggered: ${r.triggeredAt}\n`;
        }
        historyText += "--------------------------\n\n";
      });
    }

    this.screenContainer.innerHTML = `
      <div class="screen">
        <div class="scroll-content">

          <div class="top-back-bar">
            <button class="btn-back" id="backBtn">←</button>
            <div style="flex: 1;">
              <h2 class="screen-title" style="font-size: 26px;">Reminder History</h2>
              <p class="screen-subtitle">Your saved medication schedule</p>
            </div>
          </div>

          <!-- SUMMARY HERO CARD -->
          <div class="hero-header-card" style="display: flex; align-items: center; gap: 16px; margin-top: 15px;">
            <div style="width: 65px; height: 65px; border-radius: var(--radius-lg); background: var(--color-glass-white); display: flex; align-items: center; justify-content: center; font-size: 38px;">
              ⏰
            </div>
            <div>
              <div class="hero-title" style="font-size: 20px;">Medication Schedule</div>
              <div class="hero-subtitle">All your reminders are stored here</div>
            </div>
          </div>

          <div class="section-header-row" style="margin-top: 24px;">
            <h3 class="section-title">Saved Reminders</h3>
          </div>

          <!-- HISTORY TEXT CARD -->
          <div class="glass-card" style="margin-top: 8px;">
            <div id="historyText" class="report-output-box" style="margin-top: 0; min-height: 240px;">${historyText}</div>
          </div>

          <!-- INFO CARD -->
          <div class="glass-card" style="margin-top: 18px; display: flex; align-items: center; gap: 12px; padding: 18px;">
            <div style="font-size: 26px;">💊</div>
            <p style="font-size: 13px; color: var(--color-text-secondary); line-height: 1.4;">
              Stay consistent with your medication schedule for better health management.
            </p>
          </div>

          <div class="branding-footer">
            MEDINEX • AI POWERED HEALTHCARE
          </div>

        </div>
      </div>
    `;

    document.getElementById("backBtn").addEventListener("click", () => this.goBack());
  }

  // ==========================================
  // SCREEN 8: EMERGENCY (EmergencyActivity.java)
  // ==========================================

  renderEmergencyScreen() {
    this.screenContainer.innerHTML = `
      <div class="screen">
        <div class="scroll-content">

          <div class="top-back-bar">
            <button class="btn-back" id="backBtn">←</button>
            <div style="flex: 1;">
              <h2 class="screen-title" style="font-size: 26px;">Emergency Support</h2>
              <p class="screen-subtitle">Quick access when you need immediate assistance</p>
            </div>
          </div>

          <!-- EMERGENCY HERO -->
          <div class="emergency-hero-card">
            <div class="emergency-icon-box" style="width: 70px; height: 70px; font-size: 40px;">
              🚨
            </div>
            <div>
              <h3 style="font-size: 21px; font-weight: 800; color: #FFFFFF;">Emergency Assistance</h3>
              <p style="font-size: 13px; color: #FFE8E8; margin-top: 6px;">Choose a service below for quick access.</p>
            </div>
          </div>

          <div class="section-header-row" style="margin-top: 25px;">
            <h3 class="section-title">Emergency Services</h3>
          </div>

          <!-- AMBULANCE -->
          <a href="tel:108" id="ambulanceBtn" class="emergency-action-card">
            <div class="emergency-icon-box emergency-icon-red">
              🚑
            </div>
            <div style="flex: 1; margin-left: 15px;">
              <div style="font-size: 17px; font-weight: 800; color: var(--color-text-primary);">Call Ambulance</div>
              <div style="font-size: 12px; color: var(--color-text-secondary); margin-top: 3px;">Emergency medical transport (108)</div>
            </div>
            <div class="emergency-arrow emergency-arrow-red">→</div>
          </a>

          <!-- HOSPITALS -->
          <a href="https://www.google.com/maps/search/hospitals+near+me" target="_blank" rel="noopener" id="hospitalBtn" class="emergency-action-card">
            <div class="emergency-icon-box">
              🏥
            </div>
            <div style="flex: 1; margin-left: 15px;">
              <div style="font-size: 17px; font-weight: 800; color: var(--color-text-primary);">Nearby Hospitals</div>
              <div style="font-size: 12px; color: var(--color-text-secondary); margin-top: 3px;">Find hospitals near your location</div>
            </div>
            <div class="emergency-arrow">→</div>
          </a>

          <!-- DOCTOR -->
          <a href="tel:18001023999" id="doctorBtn" class="emergency-action-card">
            <div class="emergency-icon-box">
              👨‍⚕️
            </div>
            <div style="flex: 1; margin-left: 15px;">
              <div style="font-size: 17px; font-weight: 800; color: var(--color-text-primary);">Call Doctor</div>
              <div style="font-size: 12px; color: var(--color-text-secondary); margin-top: 3px;">Connect with medical assistance (18001023999)</div>
            </div>
            <div class="emergency-arrow">→</div>
          </a>

          <!-- EMERGENCY CONTACT -->
          <a href="tel:9515811687" id="contactBtn" class="emergency-action-card">
            <div class="emergency-icon-box">
              📞
            </div>
            <div style="flex: 1; margin-left: 15px;">
              <div style="font-size: 17px; font-weight: 800; color: var(--color-text-primary);">Emergency Contact</div>
              <div style="font-size: 12px; color: var(--color-text-secondary); margin-top: 3px;">Call your saved emergency contact (9515811687)</div>
            </div>
            <div class="emergency-arrow">→</div>
          </a>

          <div class="branding-footer">
            MEDINEX • EMERGENCY ASSISTANCE
          </div>

        </div>
      </div>
    `;

    document.getElementById("backBtn").addEventListener("click", () => this.goBack());
  }

  // ==========================================
  // SCREEN 9: STOCK PREDICTION (StockPredictionActivity.java)
  // ==========================================

  renderStockPredictionScreen() {
    this.screenContainer.innerHTML = `
      <div class="screen">
        <div class="scroll-content">

          <div class="top-back-bar">
            <button class="btn-back" id="backBtn">←</button>
            <div style="flex: 1;">
              <h2 class="screen-title" style="font-size: 26px;">Stock Prediction</h2>
              <p class="screen-subtitle">Predict when your medicine supply may run out</p>
            </div>
          </div>

          <!-- HERO -->
          <div class="hero-header-card" style="display: flex; align-items: center; gap: 16px; margin-top: 15px;">
            <div style="width: 70px; height: 70px; border-radius: var(--radius-lg); background: var(--color-glass-white); display: flex; align-items: center; justify-content: center; font-size: 38px;">
              📊
            </div>
            <div>
              <div class="hero-title" style="font-size: 20px;">Smart Stock Analysis</div>
              <div class="hero-subtitle">Medinex calculates your remaining supply and refill urgency.</div>
            </div>
          </div>

          <!-- RESPONSIVE TWO COLUMN DESKTOP WRAPPER -->
          <div class="two-col-desktop" style="margin-top: 15px;">
            <div>
              <div class="section-header-row" style="margin-top: 4px;">
                <h3 class="section-title">Medicine Information</h3>
              </div>

              <div class="glass-card">
                <label class="glass-input-label">💊 Medicine Name</label>
                <input type="text" id="medicineName" class="glass-input" placeholder="Enter medicine name" value="Metformin">

                <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-top: 12px;">
                  <div>
                    <label class="glass-input-label" style="font-size: 13px;">📦 Total Tablets</label>
                    <input type="number" id="totalTablets" class="glass-input" placeholder="e.g. 15" value="15">
                  </div>
                  <div>
                    <label class="glass-input-label" style="font-size: 13px;">💊 Daily Usage</label>
                    <input type="number" id="dailyUsage" class="glass-input" placeholder="e.g. 2" value="2">
                  </div>
                </div>

                <button id="calculateBtn" class="btn-primary-gradient" style="margin-top: 18px;">
                  Calculate Stock
                </button>
              </div>
            </div>

            <!-- RESULT -->
            <div>
              <div class="section-header-row" style="margin-top: 4px; margin-bottom: 6px;">
                <h3 class="section-title">Stock Prediction Report</h3>
              </div>
              <div id="resultText" class="report-output-box">Fill medicine information and tap Calculate Stock.</div>
            </div>
          </div>

          <div class="branding-footer" style="margin-top: 28px;">
            MEDINEX • INVENTORY INTELLIGENCE
          </div>

        </div>
      </div>
    `;

    document.getElementById("backBtn").addEventListener("click", () => this.goBack());

    const medicineName = document.getElementById("medicineName");
    const totalTablets = document.getElementById("totalTablets");
    const dailyUsage = document.getElementById("dailyUsage");
    const calculateBtn = document.getElementById("calculateBtn");
    const resultText = document.getElementById("resultText");

    calculateBtn.addEventListener("click", () => {
      const medicine = medicineName.value.trim();
      const totalStr = totalTablets.value.trim();
      const usageStr = dailyUsage.value.trim();

      if (!medicine || !totalStr || !usageStr) {
        resultText.textContent = "Please fill all fields";
        return;
      }

      const total = parseInt(totalStr, 10);
      const usage = parseInt(usageStr, 10);

      if (isNaN(total) || isNaN(usage) || usage <= 0) {
        resultText.textContent = "Please enter valid numbers (daily usage > 0)";
        return;
      }

      const daysRemaining = Math.floor(total / usage);
      const futureDate = new Date();
      futureDate.setDate(futureDate.getDate() + daysRemaining);

      const refillDate = futureDate.toLocaleDateString("en-GB", {
        day: "2-digit",
        month: "short",
        year: "numeric"
      });

      let result = "📦 MEDINEX STOCK PREDICTION REPORT\n\n" +
        "💊 Medicine Name:\n" + medicine + "\n\n" +
        "📦 Current Stock:\n" + total + " Tablets\n\n" +
        "💊 Daily Consumption:\n" + usage + " Tablets/Day\n\n" +
        "📅 Predicted Remaining:\n" + daysRemaining + " Days\n\n" +
        "🗓 Expected Exhaustion Date:\n" + refillDate + "\n\n";

      if (daysRemaining <= 3) {
        result += "🚨 STOCK STATUS: CRITICAL\n\n" +
          "Risk:\nMedicine stock will finish very soon.\n\n" +
          "AI Recommendation:\nPurchase medicine immediately.\n\n" +
          "Refill Urgency: HIGH\n";
      } else if (daysRemaining <= 7) {
        result += "⚠ STOCK STATUS: WARNING\n\n" +
          "Risk:\nStock running low.\n\n" +
          "AI Recommendation:\nRefill within the next few days.\n\n" +
          "Refill Urgency: MEDIUM\n";
      } else {
        result += "✅ STOCK STATUS: SAFE\n\n" +
          "Risk:\nNo immediate shortage expected.\n\n" +
          "AI Recommendation:\nCurrent stock is sufficient.\n\n" +
          "Refill Urgency: LOW\n";
      }

      result += "\n📊 STOCK ANALYSIS COMPLETE";
      resultText.textContent = result;
    });
  }

  // ==========================================
  // SCREEN 10: DUPLICATE MEDICINE (DuplicateMedicineActivity.java)
  // ==========================================

  renderDuplicateMedicineScreen() {
    this.screenContainer.innerHTML = `
      <div class="screen">
        <div class="scroll-content">

          <div class="top-back-bar">
            <button class="btn-back" id="backBtn">←</button>
            <div style="flex: 1;">
              <h2 class="screen-title" style="font-size: 26px;">Duplicate Detector</h2>
              <p class="screen-subtitle">Check medicines for duplicate ingredients and potential overdose risks</p>
            </div>
          </div>

          <!-- HERO -->
          <div class="hero-header-card" style="display: flex; align-items: center; gap: 16px; margin-top: 15px;">
            <div style="width: 70px; height: 70px; border-radius: var(--radius-lg); background: var(--color-glass-white); display: flex; align-items: center; justify-content: center; font-size: 38px;">
              🔍
            </div>
            <div>
              <div class="hero-title" style="font-size: 20px;">Medicine Safety Check</div>
              <div class="hero-subtitle">Compare two medicines with Medinex analysis.</div>
            </div>
          </div>

          <!-- RESPONSIVE TWO COLUMN DESKTOP WRAPPER -->
          <div class="two-col-desktop" style="margin-top: 15px;">
            <div>
              <div class="section-header-row" style="margin-top: 4px;">
                <h3 class="section-title">Compare Medicines</h3>
              </div>

              <div class="glass-card">
                <div class="glass-input-group">
                  <label class="glass-input-label">💊 Medicine 1</label>
                  <input type="text" id="medicine1" class="glass-input" placeholder="e.g. Paracetamol or Dolo" value="Paracetamol">
                </div>

                <div class="glass-input-group">
                  <label class="glass-input-label">💊 Medicine 2</label>
                  <input type="text" id="medicine2" class="glass-input" placeholder="e.g. Crocin or Calpol" value="Crocin">
                </div>

                <button id="checkBtn" class="btn-primary-gradient" style="margin-top: 10px;">
                  Check Duplicates
                </button>
              </div>
            </div>

            <!-- RESULT -->
            <div>
              <div class="section-header-row" style="margin-top: 4px; margin-bottom: 6px;">
                <h3 class="section-title">Analysis Result</h3>
              </div>
              <div id="resultText" class="report-output-box">Enter two medicines to check for shared active ingredients.</div>
            </div>
          </div>

          <div class="branding-footer" style="margin-top: 28px;">
            MEDINEX • CLINICAL DUPLICATE SAFEGUARD
          </div>

        </div>
      </div>
    `;

    document.getElementById("backBtn").addEventListener("click", () => this.goBack());

    const medicine1 = document.getElementById("medicine1");
    const medicine2 = document.getElementById("medicine2");
    const checkBtn = document.getElementById("checkBtn");
    const resultText = document.getElementById("resultText");

    checkBtn.addEventListener("click", () => {
      const med1 = medicine1.value.trim().toLowerCase();
      const med2 = medicine2.value.trim().toLowerCase();

      if (!med1 || !med2) {
        resultText.textContent = "⚠ Please enter both medicine names.";
        return;
      }

      // Check hardcoded rules from DuplicateMedicineActivity.java or dataset overlap
      const isDoloCrocinParacetamol =
        (med1.includes("paracetamol") && med2.includes("crocin")) ||
        (med1.includes("crocin") && med2.includes("paracetamol")) ||
        (med1.includes("paracetamol") && med2.includes("dolo")) ||
        (med1.includes("dolo") && med2.includes("paracetamol")) ||
        (med1.includes("crocin") && med2.includes("dolo")) ||
        (med1.includes("dolo") && med2.includes("crocin")) ||
        (med1.includes("calpol") && med2.includes("dolo"));

      if (isDoloCrocinParacetamol) {
        resultText.textContent =
          "🔴 DUPLICATE MEDICINE DETECTED\n\n" +
          "💊 Medicine 1:\n" + med1 + "\n\n" +
          "💊 Medicine 2:\n" + med2 + "\n\n" +
          "🤖 Medinex Analysis:\n" +
          "Both medicines contain Paracetamol.\n\n" +
          "⚠ Risk Level:\nHIGH\n\n" +
          "👨‍⚕️ Recommendation:\n" +
          "Avoid taking both medicines together. Risk of overdose may occur.";
      } else {
        resultText.textContent =
          "🟢 NO DUPLICATE MEDICINE DETECTED\n\n" +
          "💊 Medicine 1:\n" + med1 + "\n\n" +
          "💊 Medicine 2:\n" + med2 + "\n\n" +
          "🤖 Medinex Analysis:\n" +
          "Different active ingredients detected.\n\n" +
          "⚠ Risk Level:\nLOW\n\n" +
          "👨‍⚕️ Recommendation:\n" +
          "Safe to use as prescribed.";
      }
    });
  }

  // ==========================================
  // SCREEN 11: MEDICINE INTERACTION (MedicineInteractionActivity.java)
  // ==========================================

  renderMedicineInteractionScreen() {
    this.screenContainer.innerHTML = `
      <div class="screen">
        <div class="scroll-content">

          <div class="top-back-bar">
            <button class="btn-back" id="backBtn">←</button>
            <div style="flex: 1;">
              <h2 class="screen-title" style="font-size: 26px;">Interaction Checker</h2>
              <p class="screen-subtitle">Check whether two medicines may interact</p>
            </div>
          </div>

          <!-- HERO -->
          <div class="hero-header-card" style="display: flex; align-items: center; gap: 16px; margin-top: 15px;">
            <div style="width: 70px; height: 70px; border-radius: var(--radius-lg); background: var(--color-glass-white); display: flex; align-items: center; justify-content: center; font-size: 40px; font-weight: bold; color: var(--color-white);">
              ⚕
            </div>
            <div>
              <div class="hero-title" style="font-size: 20px;">Medicine Compatibility</div>
              <div class="hero-subtitle">Compare medicines and review potential interaction risks.</div>
            </div>
          </div>

          <!-- RESPONSIVE TWO COLUMN DESKTOP WRAPPER -->
          <div class="two-col-desktop" style="margin-top: 15px;">
            <div>
              <div class="section-header-row" style="margin-top: 4px;">
                <h3 class="section-title">Enter Medicines</h3>
              </div>

              <div class="glass-card">
                <div class="glass-input-group">
                  <label class="glass-input-label">💊 Medicine 1</label>
                  <input type="text" id="medicine1" class="glass-input" placeholder="e.g. Aspirin" value="Aspirin">
                </div>

                <div class="glass-input-group">
                  <label class="glass-input-label">💊 Medicine 2</label>
                  <input type="text" id="medicine2" class="glass-input" placeholder="e.g. Warfarin" value="Warfarin">
                </div>

                <button id="checkBtn" class="btn-primary-gradient" style="margin-top: 10px;">
                  Check Interaction
                </button>
              </div>
            </div>

            <!-- RESULT -->
            <div>
              <div class="section-header-row" style="margin-top: 4px; margin-bottom: 6px;">
                <h3 class="section-title">Interaction Report</h3>
              </div>
              <div id="resultText" class="report-output-box">Enter two medicines to check for adverse drug-drug interactions.</div>
            </div>
          </div>

          <div class="branding-footer" style="margin-top: 28px;">
            MEDINEX • DRUG INTERACTION ENGINE
          </div>

        </div>
      </div>
    `;

    document.getElementById("backBtn").addEventListener("click", () => this.goBack());

    const medicine1 = document.getElementById("medicine1");
    const medicine2 = document.getElementById("medicine2");
    const checkBtn = document.getElementById("checkBtn");
    const resultText = document.getElementById("resultText");

    checkBtn.addEventListener("click", () => {
      const med1 = medicine1.value.trim().toLowerCase();
      const med2 = medicine2.value.trim().toLowerCase();

      if (!med1 || !med2) {
        resultText.textContent = "⚠ Please enter both medicine names.";
        return;
      }

      // Aspirin + Warfarin
      if ((med1.includes("aspirin") && med2.includes("warfarin")) ||
          (med1.includes("warfarin") && med2.includes("aspirin"))) {
        resultText.textContent =
          "🔴 HIGH RISK INTERACTION\n\n" +
          "💊 Medicines:\n" + med1 + " + " + med2 + "\n\n" +
          "⚠ Risk Level:\nHIGH\n\n" +
          "🤖 Medinex Analysis:\n" +
          "These medicines may significantly increase bleeding risk.\n\n" +
          "👨‍⚕️ Recommendation:\n" +
          "Consult a doctor immediately before taking these medicines together.\n\n" +
          "📋 Monitoring Required:\nYES";
      }
      // Ibuprofen + Aspirin
      else if ((med1.includes("ibuprofen") && med2.includes("aspirin")) ||
               (med1.includes("aspirin") && med2.includes("ibuprofen"))) {
        resultText.textContent =
          "🟡 MODERATE INTERACTION\n\n" +
          "💊 Medicines:\n" + med1 + " + " + med2 + "\n\n" +
          "⚠ Risk Level:\nMEDIUM\n\n" +
          "🤖 Medinex Analysis:\n" +
          "Ibuprofen may reduce the effectiveness of Aspirin and increase stomach irritation.\n\n" +
          "👨‍⚕️ Recommendation:\n" +
          "Use with caution and follow medical advice.\n\n" +
          "📋 Monitoring Required:\nYES";
      }
      // Paracetamol + Cetirizine
      else if ((med1.includes("paracetamol") && med2.includes("cetirizine")) ||
               (med1.includes("cetirizine") && med2.includes("paracetamol"))) {
        resultText.textContent =
          "🟢 SAFE COMBINATION\n\n" +
          "💊 Medicines:\n" + med1 + " + " + med2 + "\n\n" +
          "⚠ Risk Level:\nLOW\n\n" +
          "🤖 Medinex Analysis:\n" +
          "No significant interaction has been identified.\n\n" +
          "👨‍⚕️ Recommendation:\n" +
          "Generally safe when used as directed.\n\n" +
          "📋 Monitoring Required:\nNO";
      }
      // Default
      else {
        resultText.textContent =
          "🟢 NO MAJOR INTERACTION DETECTED\n\n" +
          "💊 Medicines:\n" + med1 + " + " + med2 + "\n\n" +
          "⚠ Risk Level:\nLOW\n\n" +
          "🤖 Medinex Analysis:\n" +
          "No major interaction found in the Medinex database.\n\n" +
          "👨‍⚕️ Recommendation:\n" +
          "Safe to consume together under normal circumstances.\n\n" +
          "📋 Monitoring Required:\nNO";
      }
    });
  }

  // ==========================================
  // SCREEN 12: AI HEALTH ASSISTANT (AIHealthAssistantActivity.java)
  // ==========================================

  renderAiHealthAssistantScreen() {
    this.screenContainer.innerHTML = `
      <div class="screen">
        <div class="scroll-content">

          <div class="top-back-bar">
            <button class="btn-back" id="backBtn">←</button>
            <div style="flex: 1;">
              <h2 class="screen-title" style="font-size: 26px;">AI Health Assistant</h2>
              <p class="screen-subtitle">Describe your symptoms and let Medinex analyze them</p>
            </div>
          </div>

          <!-- HERO -->
          <div class="hero-header-card" style="display: flex; align-items: center; gap: 16px; margin-top: 15px;">
            <div style="width: 75px; height: 75px; border-radius: var(--radius-lg); background: var(--color-glass-white); display: flex; align-items: center; justify-content: center; font-size: 42px;">
              🤖
            </div>
            <div>
              <div class="hero-title" style="font-size: 22px;">Medinex AI</div>
              <div class="hero-subtitle">Smart symptom analysis and health guidance.</div>
              <div class="hero-badge" style="color: #D8F7E8; margin-top: 8px;">● AI Assistant Ready</div>
            </div>
          </div>

          <div class="section-header-row" style="margin-top: 25px;">
            <h3 class="section-title">Describe Your Symptoms</h3>
          </div>

          <div class="glass-card">
            <textarea id="symptomInput" class="glass-input" style="height: 100px; padding: 14px 18px; resize: none;" placeholder="e.g. I have fever, headache, and body pain since yesterday...">fever and headache</textarea>

            <div style="display: flex; gap: 8px; flex-wrap: wrap; margin-top: 10px;">
              <button type="button" class="btn-ctrl chip-btn" data-symptom="fever and body pain">Fever & Pain</button>
              <button type="button" class="btn-ctrl chip-btn" data-symptom="cough and sore throat">Cough & Cold</button>
              <button type="button" class="btn-ctrl chip-btn" data-symptom="stomach pain and vomiting">Stomach</button>
              <button type="button" class="btn-ctrl chip-btn" data-symptom="chest pain and breath shortness">Chest Pain (Alert)</button>
              <button type="button" class="btn-ctrl chip-btn" data-symptom="dengue symptoms">Dengue</button>
            </div>

            <button id="analyzeBtn" class="btn-primary-gradient" style="margin-top: 16px;">
              ✦ Analyze Symptoms
            </button>
          </div>

          <!-- RESULT -->
          <div style="margin-top: 24px;">
            <div class="section-header-row" style="margin-bottom: 6px;">
              <h3 class="section-title">AI Clinical Assessment</h3>
            </div>
            <div id="resultText" class="report-output-box">Describe your symptoms above and click Analyze Symptoms.</div>
          </div>

          <div class="branding-footer">
            MEDINEX • AI HEALTH ASSISTANCE
          </div>

        </div>
      </div>
    `;

    document.getElementById("backBtn").addEventListener("click", () => this.goBack());

    const symptomInput = document.getElementById("symptomInput");
    const analyzeBtn = document.getElementById("analyzeBtn");
    const resultText = document.getElementById("resultText");

    document.querySelectorAll(".chip-btn").forEach(btn => {
      btn.addEventListener("click", () => {
        symptomInput.value = btn.dataset.symptom;
        analyzeBtn.click();
      });
    });

    analyzeBtn.addEventListener("click", () => {
      const symptom = symptomInput.value.toLowerCase().trim();

      if (!symptom) {
        resultText.textContent = "⚠ Please enter symptoms.";
        return;
      }

      // Emergency Check
      if (symptom.includes("emergency") || symptom.includes("unconscious") || symptom.includes("accident")) {
        resultText.textContent =
          "🚨 EMERGENCY DETECTED\n\n" +
          "🏥 Please contact emergency services immediately.\n\n" +
          "📞 Use the Emergency Support module in Medinex.";
        return;
      }

      // Chest Pain Critical
      if ((symptom.includes("chest") && symptom.includes("breath")) ||
          (symptom.includes("heart") && symptom.includes("pain"))) {
        resultText.textContent =
          "🚨 MEDINEX EMERGENCY ALERT\n\n" +
          "🔴 CRITICAL RISK DETECTED\n\n" +
          "🩺 Symptoms Detected:\n• Chest Pain\n• Breathing Difficulty\n\n" +
          "⚠ Possible Conditions:\n• Heart Attack\n• Cardiac Emergency\n• Serious Respiratory Condition\n\n" +
          "🏥 Immediate Action Required:\n• Contact Emergency Services\n• Inform Family Members\n• Do Not Ignore Symptoms\n• Seek Immediate Medical Care\n\n" +
          "🚨 EMERGENCY LEVEL: CRITICAL";
        return;
      }

      // Chest / Heart general
      if (symptom.includes("chest") || symptom.includes("heart") || symptom.includes("breath")) {
        resultText.textContent =
          "🤖 MEDINEX AI REPORT\n\n" +
          "🩺 Symptoms Detected:\n• Chest Discomfort\n• Breathing Issues\n\n" +
          "🔍 Possible Conditions:\n• Respiratory Problem\n• Cardiac Condition\n\n" +
          "🔴 Severity Level:\nHIGH RISK\n\n" +
          "💡 Recommendations:\n• Seek immediate medical attention\n• Contact emergency services\n• Do not ignore symptoms\n\n" +
          "🚨 Emergency Warning:\nImmediate medical evaluation recommended.";
        return;
      }

      // Fever
      if (symptom.includes("fever") || symptom.includes("headache") || symptom.includes("body pain")) {
        resultText.textContent =
          "🤖 MEDINEX AI REPORT\n\n" +
          "🩺 Symptoms Detected:\n• Fever\n• Headache / Body Pain\n\n" +
          "🔍 Possible Conditions:\n• Viral Fever\n• Common Flu\n• Mild Infection\n\n" +
          "🟢 Severity Level:\nLOW RISK\n\n" +
          "💡 Recommendations:\n• Drink plenty of fluids\n• Take adequate rest\n• Monitor body temperature\n• Eat nutritious food\n\n" +
          "💊 Commonly Used Medicines:\n• Paracetamol 500mg\n\n" +
          "⏰ General Timing:\n• Usually every 6-8 hours if advised\n• Follow package instructions\n\n" +
          "⚠ Disclaimer:\nConsult a doctor if symptoms persist for more than 3 days.";
      }
      // Cough / Cold
      else if (symptom.includes("cough") || symptom.includes("cold") || symptom.includes("sore throat")) {
        resultText.textContent =
          "🤖 MEDINEX AI REPORT\n\n" +
          "🩺 Symptoms Detected:\n• Cough\n• Cold / Sore Throat\n\n" +
          "🔍 Possible Conditions:\n• Common Cold\n• Throat Infection\n• Seasonal Allergy\n\n" +
          "🟡 Severity Level:\nMEDIUM RISK\n\n" +
          "💡 Recommendations:\n• Drink warm water\n• Avoid cold beverages\n• Take sufficient rest\n• Gargle with salt water\n\n" +
          "💊 Commonly Used Medicines:\n• Cough Syrup\n• Levocetirizine\n\n" +
          "⏰ General Timing:\n• As directed by a healthcare professional\n\n" +
          "⚠ Disclaimer:\nVisit a doctor if breathing difficulty occurs.";
      }
      // Stomach
      else if (symptom.includes("stomach") || symptom.includes("vomit") || symptom.includes("diarrhea")) {
        resultText.textContent =
          "🤖 MEDINEX AI REPORT\n\n" +
          "🩺 Symptoms Detected:\n• Stomach Discomfort\n• Digestive Issues\n\n" +
          "🔍 Possible Conditions:\n• Indigestion\n• Food Poisoning\n• Gastric Irritation\n\n" +
          "🟡 Severity Level:\nMEDIUM RISK\n\n" +
          "💡 Recommendations:\n• Drink plenty of water\n• Eat light food\n• Avoid oily food\n• Take proper rest\n\n" +
          "💊 Commonly Used Medicines:\n• Antacid Tablets\n• Oral Rehydration Solution (ORS)\n\n" +
          "⏰ General Timing:\n• Generally after medical advice\n\n" +
          "⚠ Disclaimer:\nSeek medical advice if symptoms worsen.";
      }
      // Diabetes
      else if (symptom.includes("diabetes") || symptom.includes("sugar")) {
        resultText.textContent =
          "🤖 MEDINEX AI REPORT\n\n" +
          "🩺 Symptoms Detected:\n• Diabetes Related Concern\n\n" +
          "🔍 Possible Conditions:\n• High Blood Sugar\n• Type 2 Diabetes\n\n" +
          "🟡 Severity Level:\nMEDIUM RISK\n\n" +
          "💡 Recommendations:\n• Monitor blood sugar regularly\n• Avoid sugary foods\n• Stay physically active\n\n" +
          "💊 Commonly Used Medicines:\n• Metformin (commonly prescribed)\n\n" +
          "⏰ General Timing:\n• Only as prescribed by a doctor\n\n" +
          "⚠ Doctor Consultation Recommended.";
      }
      // BP
      else if (symptom.includes("bp") || symptom.includes("blood pressure") || symptom.includes("hypertension")) {
        resultText.textContent =
          "🤖 MEDINEX AI REPORT\n\n" +
          "🩺 Symptoms Detected:\n• Blood Pressure Concern\n\n" +
          "🔍 Possible Conditions:\n• Hypertension\n• Elevated Blood Pressure\n\n" +
          "🟡 Severity Level:\nMEDIUM RISK\n\n" +
          "💡 Recommendations:\n• Reduce salt intake\n• Exercise regularly\n• Monitor BP frequently\n\n" +
          "💊 Commonly Used Medicines:\n• Common BP medicines may be prescribed by doctors\n\n" +
          "⏰ General Timing:\n• Follow doctor's instructions exactly\n\n" +
          "⚠ Seek medical advice if BP remains high.";
      }
      // Dengue
      else if (symptom.includes("dengue")) {
        resultText.textContent =
          "🤖 MEDINEX AI REPORT\n\n" +
          "🩺 Symptoms Detected:\n• Dengue Related Symptoms\n\n" +
          "🔍 Possible Conditions:\n• Dengue Fever\n\n" +
          "🔴 Severity Level:\nHIGH RISK\n\n" +
          "💡 Recommendations:\n• Drink plenty of fluids\n• Monitor platelet count\n• Take complete rest\n\n" +
          "🚨 Immediate medical supervision advised.";
      }
      // Typhoid
      else if (symptom.includes("typhoid")) {
        resultText.textContent =
          "🤖 MEDINEX AI REPORT\n\n" +
          "🩺 Symptoms Detected:\n• Typhoid Related Symptoms\n\n" +
          "🔍 Possible Conditions:\n• Typhoid Fever\n\n" +
          "🟠 Severity Level:\nHIGH RISK\n\n" +
          "💡 Recommendations:\n• Eat soft foods\n• Stay hydrated\n• Complete prescribed treatment\n\n" +
          "⚠ Doctor supervision required.";
      }
      // Migraine
      else if (symptom.includes("migraine")) {
        resultText.textContent =
          "🤖 MEDINEX AI REPORT\n\n" +
          "🩺 Symptoms Detected:\n• Migraine Symptoms\n\n" +
          "🔍 Possible Conditions:\n• Migraine Headache\n\n" +
          "🟡 Severity Level:\nMEDIUM RISK\n\n" +
          "💡 Recommendations:\n• Rest in a dark room\n• Stay hydrated\n• Reduce screen exposure\n\n" +
          "⚠ Consult a doctor if frequent.";
      }
      // Asthma
      else if (symptom.includes("asthma")) {
        resultText.textContent =
          "🤖 MEDINEX AI REPORT\n\n" +
          "🩺 Symptoms Detected:\n• Asthma Related Symptoms\n\n" +
          "🔍 Possible Conditions:\n• Asthma Attack\n• Airway Inflammation\n\n" +
          "🔴 Severity Level:\nHIGH RISK\n\n" +
          "💡 Recommendations:\n• Use prescribed inhaler\n• Avoid dust and smoke\n• Seek medical care if breathing worsens\n\n" +
          "⚠ Doctor consultation recommended.";
      }
      // COVID
      else if (symptom.includes("covid") || symptom.includes("corona")) {
        resultText.textContent =
          "🤖 MEDINEX AI REPORT\n\n" +
          "🩺 Symptoms Detected:\n• COVID Related Symptoms\n\n" +
          "🔍 Possible Conditions:\n• COVID-19 Infection\n\n" +
          "🟠 Severity Level:\nMEDIUM TO HIGH RISK\n\n" +
          "💡 Recommendations:\n• Isolate if necessary\n• Drink plenty of fluids\n• Monitor oxygen levels\n• Consult healthcare professionals\n\n" +
          "⚠ Seek medical attention if breathing difficulty occurs.";
      }
      else {
        resultText.textContent =
          "🤖 MEDINEX AI REPORT\n\n" +
          "No matching condition found.\n\n" +
          "💡 Recommendation:\nPlease provide more detailed symptoms.\n\n" +
          "⚠ This AI assistant is for educational purposes only.";
      }
    });
  }

  // ==========================================
  // SCREEN 13: HEALTH HUB (HealthHubActivity.java)
  // ==========================================

  renderHealthHubScreen() {
    this.screenContainer.innerHTML = `
      <div class="screen theme-light-blue">
        <div class="scroll-content">

          <div class="top-back-bar">
            <button class="btn-back" id="backBtn">←</button>
            <div style="flex: 1;">
              <h2 class="screen-title">🏥 Health Hub</h2>
              <p class="screen-subtitle">Your personal collection of health & wellness tools</p>
            </div>
          </div>

          <!-- ABOUT MEDINEX -->
          <div id="aboutBtn" class="health-hub-card" style="display: flex; align-items: center; margin-bottom: 15px; cursor: pointer;">
            <div style="font-size: 28px; margin-right: 15px;">📘</div>
            <div style="font-size: 20px; font-weight: 800; color: var(--text-health-hub);">About Medinex</div>
          </div>

          <!-- BMI CALCULATOR -->
          <div id="bmiBtn" class="health-hub-card" style="display: flex; align-items: center; margin-bottom: 15px; cursor: pointer;">
            <div style="font-size: 28px; margin-right: 15px;">⚖</div>
            <div style="font-size: 20px; font-weight: 800; color: var(--text-health-hub);">BMI Calculator</div>
          </div>

          <!-- HEALTH TIPS -->
          <div id="tipsBtn" class="health-hub-card" style="display: flex; align-items: center; margin-bottom: 15px; cursor: pointer;">
            <div style="font-size: 28px; margin-right: 15px;">💡</div>
            <div style="font-size: 20px; font-weight: 800; color: var(--text-health-hub);">Daily Health Tips</div>
          </div>

          <!-- WATER TRACKER -->
          <div id="waterBtn" class="health-hub-card" style="display: flex; align-items: center; margin-bottom: 15px; cursor: pointer;">
            <div style="font-size: 28px; margin-right: 15px;">💧</div>
            <div style="font-size: 20px; font-weight: 800; color: var(--text-health-hub);">Water Tracker</div>
          </div>

          <!-- APPLE HEALTH ADVISOR -->
          <div id="appleBtn" class="health-hub-card" style="display: flex; align-items: center; margin-bottom: 15px; cursor: pointer;">
            <div style="font-size: 28px; margin-right: 15px;">🍎</div>
            <div style="font-size: 20px; font-weight: 800; color: var(--text-health-hub);">Apple Health Advisor</div>
          </div>

        </div>
      </div>
    `;

    document.getElementById("backBtn").addEventListener("click", () => this.goBack());
    document.getElementById("aboutBtn").addEventListener("click", () => this.navigateTo("about"));
    document.getElementById("bmiBtn").addEventListener("click", () => this.navigateTo("bmi"));
    document.getElementById("tipsBtn").addEventListener("click", () => this.navigateTo("tips"));
    document.getElementById("waterBtn").addEventListener("click", () => this.navigateTo("water"));
    document.getElementById("appleBtn").addEventListener("click", () => this.navigateTo("apple"));
  }

  // ==========================================
  // SCREEN 14: ABOUT MEDINEX (AboutActivity.java)
  // ==========================================

  renderAboutScreen() {
    this.screenContainer.innerHTML = `
      <div class="screen theme-light-blue">
        <div class="scroll-content">

          <div class="top-back-bar">
            <button class="btn-back" id="backBtn">←</button>
            <div style="flex: 1;">
              <h2 class="screen-title">📘 About Medinex</h2>
              <p class="screen-subtitle">AI-powered healthcare assistance</p>
            </div>
          </div>

          <!-- ABOUT CARD -->
          <div class="health-hub-card" style="margin-top: 10px;">
            <h3 style="font-size: 22px; font-weight: 800; color: var(--text-health-hub);">🏥 What is Medinex?</h3>
            <p style="font-size: 16px; color: #334E68; line-height: 1.6; margin-top: 15px;">
              Medinex is an AI-powered healthcare assistant designed to help patients manage medicines, reminders, prescriptions, stock prediction, diet planning, and health monitoring. The system uses intelligent healthcare modules to improve medication safety and patient awareness.
            </p>
          </div>

          <!-- FEATURES CARD -->
          <div class="health-hub-card" style="margin-top: 20px;">
            <h3 style="font-size: 22px; font-weight: 800; color: var(--text-health-hub);">✨ Medinex Features</h3>
            <p style="font-size: 16px; color: #334E68; line-height: 2.2; margin-top: 15px;">
              💊 Medicine Management<br>
              ⏰ Medicine Reminders<br>
              📋 AI Prescription Scanner<br>
              📦 Medicine Stock Prediction<br>
              🥗 Diet Planning<br>
              🩺 Health Monitoring
            </p>
          </div>

          <!-- MISSION CARD -->
          <div class="health-hub-card" style="margin-top: 20px; margin-bottom: 25px;">
            <h3 style="font-size: 22px; font-weight: 800; color: var(--text-health-hub);">💙 Our Goal</h3>
            <p style="font-size: 16px; color: #334E68; line-height: 1.6; margin-top: 15px;">
              To make everyday healthcare management simpler, safer, and more accessible through intelligent digital tools.
            </p>
          </div>

        </div>
      </div>
    `;

    document.getElementById("backBtn").addEventListener("click", () => this.goBack());
  }

  // ==========================================
  // SCREEN 15: BMI CALCULATOR (BMIActivity.java)
  // ==========================================

  renderBmiScreen() {
    this.screenContainer.innerHTML = `
      <div class="screen theme-light-blue">
        <div class="scroll-content">

          <div class="top-back-bar">
            <button class="btn-back" id="backBtn">←</button>
            <div style="flex: 1;">
              <h2 class="screen-title">⚖ BMI Calculator</h2>
              <p class="screen-subtitle">Check your Body Mass Index</p>
            </div>
          </div>

          <!-- HERO CARD -->
          <div class="health-hub-card" style="display: flex; align-items: center; margin-top: 10px;">
            <div style="font-size: 32px; margin-right: 15px;">⚖️</div>
            <div>
              <div style="font-size: 13px; font-weight: 800; color: #0088CC;">MEDINEX HEALTH TOOL</div>
              <div style="font-size: 18px; font-weight: 800; color: var(--text-health-hub); margin-top: 4px;">Calculate your BMI easily</div>
            </div>
          </div>

          <!-- RESPONSIVE TWO COLUMN DESKTOP WRAPPER -->
          <div class="two-col-desktop" style="margin-top: 15px;">
            <div>
              <!-- WEIGHT INPUT -->
              <div class="health-hub-card">
                <label class="glass-input-label">⚖ Weight (kg)</label>
                <input type="number" id="weightInput" class="glass-input" placeholder="Enter weight in kg" value="70">
              </div>

              <!-- HEIGHT INPUT -->
              <div class="health-hub-card" style="margin-top: 16px;">
                <label class="glass-input-label">📏 Height (meters)</label>
                <input type="number" step="0.01" id="heightInput" class="glass-input" placeholder="Enter height in meters e.g. 1.75" value="1.75">
              </div>

              <button id="calculateBtn" class="btn-primary-gradient" style="margin-top: 18px; height: 56px;">
                Calculate BMI
              </button>
            </div>

            <!-- RESULT -->
            <div>
              <div class="health-hub-card" style="margin-bottom: 25px;">
                <label class="glass-input-label">⚖ BMI Result Assessment</label>
                <div id="resultText" class="report-output-box" style="margin-top: 10px; min-height: 160px;">
                  Enter your weight and height to view your BMI assessment.
                </div>
              </div>
            </div>
          </div>

        </div>
      </div>
    `;

    document.getElementById("backBtn").addEventListener("click", () => this.goBack());

    const weightInput = document.getElementById("weightInput");
    const heightInput = document.getElementById("heightInput");
    const calculateBtn = document.getElementById("calculateBtn");
    const resultText = document.getElementById("resultText");

    calculateBtn.addEventListener("click", () => {
      const weightStr = weightInput.value.trim();
      const heightStr = heightInput.value.trim();

      if (!weightStr || !heightStr) {
        resultText.textContent = "Please enter weight and height";
        return;
      }

      const weight = parseFloat(weightStr);
      const height = parseFloat(heightStr);

      if (isNaN(weight) || isNaN(height) || height <= 0) {
        resultText.textContent = "Please enter valid numeric values";
        return;
      }

      const bmi = weight / (height * height);
      let status = "";

      if (bmi < 18.5) {
        status = "Underweight";
      } else if (bmi < 25) {
        status = "Normal";
      } else if (bmi < 30) {
        status = "Overweight";
      } else {
        status = "Obese";
      }

      resultText.textContent =
        "⚖ BMI REPORT\n\n" +
        "Weight: " + weight + " kg\n" +
        "Height: " + height + " m\n\n" +
        "BMI: " + bmi.toFixed(2) + "\n\n" +
        "Status: " + status;
    });
  }

  // ==========================================
  // SCREEN 16: HEALTH TIPS (HealthTipsActivity.java)
  // ==========================================

  renderHealthTipsScreen() {
    this.screenContainer.innerHTML = `
      <div class="screen theme-light-blue">
        <div class="scroll-content">

          <div class="top-back-bar">
            <button class="btn-back" id="backBtn">←</button>
            <div style="flex: 1;">
              <h2 class="screen-title">💡 Daily Health Tips</h2>
              <p class="screen-subtitle">Simple habits for a healthier lifestyle</p>
            </div>
          </div>

          <div class="health-hub-card" style="margin-top: 10px;">
            <!-- Water -->
            <h3 style="font-size: 19px; font-weight: 800; color: var(--text-health-hub);">💧 Stay Hydrated</h3>
            <p style="font-size: 15px; color: #334E68; margin-top: 6px;">Drink enough water throughout the day to support overall health.</p>

            <!-- Food -->
            <h3 style="font-size: 19px; font-weight: 800; color: var(--text-health-hub); margin-top: 22px;">🥗 Eat Healthy</h3>
            <p style="font-size: 15px; color: #334E68; margin-top: 6px;">Choose fresh fruits, vegetables and nutritious foods as part of your daily diet.</p>

            <!-- Exercise -->
            <h3 style="font-size: 19px; font-weight: 800; color: var(--text-health-hub); margin-top: 22px;">🏃 Stay Active</h3>
            <p style="font-size: 15px; color: #334E68; margin-top: 6px;">Exercise regularly and aim for around 30 minutes of physical activity each day.</p>

            <!-- Sleep -->
            <h3 style="font-size: 19px; font-weight: 800; color: var(--text-health-hub); margin-top: 22px;">😴 Get Enough Sleep</h3>
            <p style="font-size: 15px; color: #334E68; margin-top: 6px;">Maintain a regular sleep schedule and aim for 7–8 hours of sleep.</p>

            <!-- Habits -->
            <h3 style="font-size: 19px; font-weight: 800; color: var(--text-health-hub); margin-top: 22px;">🚭 Avoid Harmful Habits</h3>
            <p style="font-size: 15px; color: #334E68; margin-top: 6px;">Avoid smoking and excessive alcohol consumption to support long-term wellbeing.</p>

            <!-- Checkups -->
            <h3 style="font-size: 19px; font-weight: 800; color: var(--text-health-hub); margin-top: 22px;">🩺 Regular Checkups</h3>
            <p style="font-size: 15px; color: #334E68; margin-top: 6px;">Schedule regular health checkups and consult healthcare professionals when needed.</p>
          </div>

          <div style="text-align: center; font-size: 13px; color: #64748B; margin: 20px 0 25px 0;">
            💙 Small healthy habits can make a big difference.
          </div>

        </div>
      </div>
    `;

    document.getElementById("backBtn").addEventListener("click", () => this.goBack());
  }

  // ==========================================
  // SCREEN 17: WATER TRACKER (WaterTrackerActivity.java)
  // ==========================================

  renderWaterTrackerScreen() {
    let glasses = MedinexDB.getWaterGlasses();

    const getStatusText = (g) => {
      if (g >= 8) {
        return "✅ Daily Water Goal Achieved!";
      } else {
        return `💧 Remaining: ${8 - g} Glasses`;
      }
    };

    this.screenContainer.innerHTML = `
      <div class="screen theme-light-blue">
        <div class="scroll-content">

          <div class="top-back-bar">
            <button class="btn-back" id="backBtn">←</button>
            <div style="flex: 1;">
              <h2 class="screen-title">💧 Water Tracker</h2>
              <p class="screen-subtitle">Track your daily water intake</p>
            </div>
          </div>

          <!-- COUNT CARD -->
          <div class="health-hub-card" style="text-align: center; padding: 25px; margin-top: 20px;">
            <div style="font-size: 18px; font-weight: 800; color: var(--text-health-hub);">💧 Today's Water Intake</div>
            <div id="waterCountText" style="font-size: 26px; font-weight: 800; color: var(--text-health-hub); margin-top: 15px;">
              Glasses Drunk: ${glasses}
            </div>
          </div>

          <!-- ADD BUTTON -->
          <button id="addWaterBtn" class="btn-primary-gradient" style="margin-top: 20px; height: 60px;">
            ➕ Add Glass
          </button>

          <!-- GOAL CARD -->
          <div class="health-hub-card" style="margin-top: 20px;">
            <div style="font-size: 18px; font-weight: 800; color: var(--text-health-hub);">🎯 Daily Hydration Goal</div>
            <div id="statusText" style="font-size: 17px; color: #64748B; margin-top: 12px;">
              ${getStatusText(glasses)}
            </div>
          </div>

        </div>
      </div>
    `;

    document.getElementById("backBtn").addEventListener("click", () => this.goBack());

    const waterCountText = document.getElementById("waterCountText");
    const statusText = document.getElementById("statusText");
    const addWaterBtn = document.getElementById("addWaterBtn");

    addWaterBtn.addEventListener("click", () => {
      glasses++;
      MedinexDB.setWaterGlasses(glasses);
      waterCountText.textContent = `Glasses Drunk: ${glasses}`;
      statusText.textContent = getStatusText(glasses);
      this.showToast(`Logged: ${glasses} glasses today!`);
    });
  }

  // ==========================================
  // SCREEN 18: APPLE HEALTH ADVISOR (AppleActivity.java)
  // ==========================================

  renderAppleScreen() {
    this.screenContainer.innerHTML = `
      <div class="screen theme-light-blue">
        <div class="scroll-content">

          <div class="top-back-bar">
            <button class="btn-back" id="backBtn">←</button>
            <div style="flex: 1;">
              <h2 class="screen-title">🍎 Apple Health Advisor</h2>
              <p class="screen-subtitle">Get fruit recommendations and first aid tips</p>
            </div>
          </div>

          <div class="health-hub-card" style="margin-top: 15px;">
            <label class="glass-input-label">🩺 Health Condition</label>
            <input type="text" id="conditionInput" class="glass-input" placeholder="Enter condition (Fever, Cold, Dengue...)" value="Fever">
          </div>

          <button id="generateBtn" class="btn-primary-gradient" style="margin-top: 20px; height: 60px;">
            Get Advice
          </button>

          <div class="health-hub-card" style="margin-top: 20px; margin-bottom: 25px;">
            <label class="glass-input-label">🍎 Health Advice</label>
            <div id="resultText" class="report-output-box" style="margin-top: 15px; color: #334E68;">
              Your health advice will appear here...
            </div>
          </div>

        </div>
      </div>
    `;

    document.getElementById("backBtn").addEventListener("click", () => this.goBack());

    const conditionInput = document.getElementById("conditionInput");
    const generateBtn = document.getElementById("generateBtn");
    const resultText = document.getElementById("resultText");

    generateBtn.addEventListener("click", () => {
      const condition = conditionInput.value.toLowerCase().trim();

      if (condition.includes("fever")) {
        resultText.textContent =
          "🍎 Recommended Fruits\n\n" +
          "• Apple\n• Orange\n• Kiwi\n\n" +
          "🩹 First Aid\n\n" +
          "• Drink plenty of water\n• Take proper rest\n• Monitor temperature";
      } else if (condition.includes("cold")) {
        resultText.textContent =
          "🍎 Recommended Fruits\n\n" +
          "• Orange\n• Lemon\n• Kiwi\n\n" +
          "🩹 First Aid\n\n" +
          "• Drink warm water\n• Steam inhalation\n• Get enough rest";
      } else if (condition.includes("dengue")) {
        resultText.textContent =
          "🍎 Recommended Fruits\n\n" +
          "• Papaya\n• Apple\n• Orange\n\n" +
          "🩹 First Aid\n\n" +
          "• Stay hydrated\n• Drink ORS\n• Consult a doctor";
      } else {
        resultText.textContent =
          "No advice available for this condition.\n\n" +
          "Try: Fever, Cold, or Dengue.";
      }
    });
  }

  // ==========================================
  // SCREEN 19: DIET PLANNER (DietPlannerActivity.java)
  // ==========================================

  renderDietPlannerScreen() {
    this.screenContainer.innerHTML = `
      <div class="screen theme-light-blue">
        <div class="scroll-content">

          <div class="top-back-bar">
            <button class="btn-back" id="backBtn">←</button>
            <div style="flex: 1;">
              <h2 class="screen-title">🥗 Diet Planner</h2>
              <p class="screen-subtitle">Personalized diet recommendations</p>
            </div>
          </div>

          <!-- NUTRITION AI HERO -->
          <div class="health-hub-card" style="display: flex; align-items: center; margin-top: 10px;">
            <div style="font-size: 32px; margin-right: 15px;">🥗</div>
            <div>
              <div style="font-size: 14px; font-weight: 800; color: #0088CC;">MEDINEX NUTRITION AI</div>
              <div style="font-size: 18px; font-weight: 800; color: var(--text-health-hub); margin-top: 4px;">Create a personalized food plan</div>
            </div>
          </div>

          <!-- INPUT -->
          <div class="health-hub-card" style="margin-top: 20px;">
            <label class="glass-input-label">Health Condition</label>
            <p style="font-size: 14px; color: #64748B; margin-bottom: 12px;">Tell us about your current condition</p>
            <input type="text" id="conditionInput" class="glass-input" placeholder="e.g. Fever, Diabetes, Dengue" value="Fever">
          </div>

          <button id="generateBtn" class="btn-primary-gradient" style="margin-top: 20px; height: 60px;">
            Generate Diet Plan
          </button>

          <!-- RESULT -->
          <div class="health-hub-card" style="margin-top: 20px; margin-bottom: 15px;">
            <label class="glass-input-label">🍎 Diet Recommendation</label>
            <p style="font-size: 14px; color: #64748B; margin-bottom: 10px;">Your personalized nutrition plan</p>
            <div id="resultText" class="report-output-box" style="margin-top: 8px; color: #102A43;">
              Your diet plan will appear here after analysis.
            </div>
          </div>

          <p style="font-size: 12px; color: #64748B; text-align: center; margin-bottom: 25px;">
            Medinex recommendations are for general guidance and should not replace professional medical advice.
          </p>

        </div>
      </div>
    `;

    document.getElementById("backBtn").addEventListener("click", () => this.goBack());

    const conditionInput = document.getElementById("conditionInput");
    const generateBtn = document.getElementById("generateBtn");
    const resultText = document.getElementById("resultText");

    generateBtn.addEventListener("click", () => {
      const condition = conditionInput.value.toLowerCase().trim();

      if (condition.includes("fever")) {
        resultText.textContent =
          "🥗 FEVER DIET PLAN\n\n" +
          "Morning:\n• Warm Water\n• Idli\n• Banana\n\n" +
          "Lunch:\n• Rice\n• Dal\n• Boiled Vegetables\n\n" +
          "Evening:\n• Fruit Juice\n• Apple\n\n" +
          "Dinner:\n• Khichdi\n• Soup\n\n" +
          "Avoid:\n• Ice Cream\n• Cold Drinks\n• Fried Foods\n\n" +
          "Water Intake:\n• 3-4 Liters";
      } else if (condition.includes("diabetes")) {
        resultText.textContent =
          "🥗 DIABETES DIET PLAN\n\n" +
          "Breakfast:\n• Oats\n• Boiled Eggs\n\n" +
          "Lunch:\n• Brown Rice\n• Vegetables\n\n" +
          "Dinner:\n• Chapati\n• Dal\n\n" +
          "Avoid:\n• Sugar\n• Soft Drinks\n• Sweets";
      } else if (condition.includes("blood pressure") || condition.includes("bp")) {
        resultText.textContent =
          "🥗 BLOOD PRESSURE DIET PLAN\n\n" +
          "Eat:\n• Fruits\n• Vegetables\n• Low Salt Foods\n\n" +
          "Avoid:\n• Pickles\n• Chips\n• Fast Food\n\n" +
          "Water:\n• 2.5 Liters Daily";
      } else if (condition.includes("cold")) {
        resultText.textContent =
          "🥗 COLD DIET PLAN\n\n" +
          "Foods To Eat:\n• Warm Soup\n• Ginger Tea\n• Citrus Fruits\n\n" +
          "Foods To Avoid:\n• Ice Cream\n• Cold Drinks\n\n" +
          "Water Intake:\n• 3 Liters Daily\n\n" +
          "Sleep:\n• 8 Hours\n\n" +
          "Recovery Tips:\n• Keep Body Warm";
      } else if (condition.includes("cough")) {
        resultText.textContent =
          "🥗 COUGH DIET PLAN\n\n" +
          "Foods To Eat:\n• Honey\n• Warm Water\n• Soup\n\n" +
          "Foods To Avoid:\n• Cold Foods\n• Fried Snacks\n\n" +
          "Water Intake:\n• 3 Liters Daily\n\n" +
          "Recovery Tips:\n• Avoid Dust";
      } else if (condition.includes("stomach")) {
        resultText.textContent =
          "🥗 STOMACH PAIN DIET PLAN\n\n" +
          "Foods To Eat:\n• Banana\n• Rice\n• Toast\n\n" +
          "Foods To Avoid:\n• Spicy Foods\n• Oily Foods\n\n" +
          "Water Intake:\n• 2.5 Liters\n\n" +
          "Recovery Tips:\n• Eat Light Meals";
      } else if (condition.includes("dengue")) {
        resultText.textContent =
          "🥗 DENGUE DIET PLAN\n\n" +
          "Foods To Eat:\n• Papaya Leaf Juice\n• Coconut Water\n• Pomegranate\n\n" +
          "Foods To Avoid:\n• Junk Food\n• Soft Drinks\n\n" +
          "Water Intake:\n• 4 Liters Daily\n\n" +
          "Recovery Tips:\n• Complete Rest";
      } else if (condition.includes("typhoid")) {
        resultText.textContent =
          "🥗 TYPHOID DIET PLAN\n\n" +
          "Foods To Eat:\n• Khichdi\n• Boiled Vegetables\n• Curd\n\n" +
          "Foods To Avoid:\n• Spicy Foods\n• Fried Foods\n\n" +
          "Water Intake:\n• 4 Liters Daily\n\n" +
          "Recovery Tips:\n• Soft Foods Only";
      } else if (condition.includes("migraine")) {
        resultText.textContent =
          "🥗 MIGRAINE DIET PLAN\n\n" +
          "Foods To Eat:\n• Almonds\n• Leafy Vegetables\n\n" +
          "Foods To Avoid:\n• Excess Coffee\n• Alcohol\n\n" +
          "Sleep:\n• 8 Hours Daily\n\n" +
          "Recovery Tips:\n• Reduce Screen Time";
      } else {
        resultText.textContent =
          "No diet plan available for this condition.\n\n" +
          "Try:\n• Fever\n• Diabetes\n• Blood Pressure";
      }
    });
  }

  // ==========================================
  // SCREEN 20: PROFILE (ProfileActivity.java)
  // ==========================================

  async renderProfileScreen() {
    const firebaseUser = auth.currentUser;
    const prefs = MedinexDB.getPrefs();

    // If neither Firebase Auth user nor session prefs exist, redirect to login
    if (!firebaseUser && !prefs.isLoggedIn) {
      this.showToast("Please log in to view your profile");
      this.navigateTo("login", false);
      return;
    }

    // Determine authenticated UID and Email from Firebase Auth or session
    const currentUid = firebaseUser ? firebaseUser.uid : (prefs.uid || "");
    const currentEmail = (firebaseUser && firebaseUser.email)
      ? firebaseUser.email
      : (prefs.userEmail && prefs.userEmail !== "vishnu@medinex.ai" ? prefs.userEmail : "Not provided");
    let currentName = (prefs.userName && prefs.userName !== "User")
      ? prefs.userName
      : (firebaseUser?.displayName || "");
    let currentPhone = prefs.userPhone || "";

    const displayInitial = currentName
      ? currentName.charAt(0).toUpperCase()
      : (currentEmail && currentEmail !== "Not provided" ? currentEmail.charAt(0).toUpperCase() : "U");

    this.screenContainer.innerHTML = `
      <div class="screen theme-light-blue">
        <div class="scroll-content" style="text-align: center;">

          <div class="top-back-bar">
            <button class="btn-back" id="backBtn">←</button>
            <div style="flex: 1;"></div>
          </div>

          <div class="profile-circle-btn profile-circle-lg" id="profileAvatar">
            ${displayInitial}
          </div>

          <h2 class="screen-title" style="margin-top: 12px;">My Profile</h2>
          <p style="font-size: 13px; color: var(--color-text-secondary); margin-top: 2px;">Authenticated Firebase Account</p>

          <div class="health-hub-card" style="margin-top: 20px; text-align: left; padding: 22px;">
            <div style="margin-bottom: 14px;">
              <span style="font-size: 12px; font-weight: 700; color: #94A3B8; text-transform: uppercase; letter-spacing: 0.5px;">Full Name</span>
              <div id="nameText" style="font-size: 18px; font-weight: 700; color: var(--text-health-hub); margin-top: 2px;">
                ${this.escapeHtml(currentName || "Loading profile...")}
              </div>
            </div>

            <div style="margin-bottom: 14px;">
              <span style="font-size: 12px; font-weight: 700; color: #94A3B8; text-transform: uppercase; letter-spacing: 0.5px;">Email Address</span>
              <div id="emailText" style="font-size: 16px; font-weight: 600; color: #334155; margin-top: 2px; word-break: break-all;">
                ${this.escapeHtml(currentEmail)}
              </div>
            </div>

            <div style="margin-bottom: 14px;">
              <span style="font-size: 12px; font-weight: 700; color: #94A3B8; text-transform: uppercase; letter-spacing: 0.5px;">Phone Number</span>
              <div id="phoneText" style="font-size: 16px; color: #334155; margin-top: 2px;">
                ${this.escapeHtml(currentPhone || "Loading...")}
              </div>
            </div>

            <div style="border-top: 1px dashed #CBD5E1; padding-top: 12px; margin-top: 6px;">
              <span style="font-size: 11px; font-weight: 700; color: #94A3B8; text-transform: uppercase; letter-spacing: 0.5px;">Firebase User UID</span>
              <div id="uidText" style="font-size: 12px; font-family: monospace; color: #64748B; margin-top: 2px; word-break: break-all;">
                ${currentUid ? this.escapeHtml(currentUid) : "Unavailable"}
              </div>
            </div>

            <div id="profileSyncStatus" style="font-size: 12px; color: #0284C7; margin-top: 12px; display: flex; align-items: center; gap: 6px;">
              <span class="spinner-small" style="width: 14px; height: 14px; border-width: 2px; border-color: #0284C7; border-top-color: transparent;"></span>
              Syncing with Firestore users/${currentUid ? currentUid.substring(0, 8) + '...' : ''}...
            </div>
          </div>

          <!-- SETTINGS BUTTON -->
          <button id="settingsBtn" class="btn-primary-gradient" style="margin-top: 20px; height: 56px;">
            ⚙ SETTINGS
          </button>

          <!-- LOGOUT BUTTON -->
          <button id="logoutBtn" class="btn-primary-gradient" style="background: linear-gradient(135deg, #EF4444 0%, #DC2626 100%); margin-top: 14px; margin-bottom: 25px; height: 56px;">
            🚪 LOGOUT
          </button>

        </div>
      </div>
    `;

    document.getElementById("backBtn").addEventListener("click", () => this.goBack());

    document.getElementById("settingsBtn").addEventListener("click", () => {
      this.navigateTo("settings");
    });

    document.getElementById("logoutBtn").addEventListener("click", async () => {
      const logoutBtn = document.getElementById("logoutBtn");
      logoutBtn.disabled = true;
      logoutBtn.textContent = "Logging out...";
      try {
        await logoutFromFirebase();
      } catch (err) {
        console.warn("Logout error:", err);
      }
      MedinexDB.clearPrefs();
      this.showToast("Logged out successfully");
      this.navigateTo("login", false);
    });

    // Query Firestore document users/{uid}
    if (currentUid) {
      try {
        const firestoreData = await loadUserProfile(currentUid);
        const syncStatus = document.getElementById("profileSyncStatus");
        const nameEl = document.getElementById("nameText");
        const phoneEl = document.getElementById("phoneText");
        const emailEl = document.getElementById("emailText");
        const avatarEl = document.getElementById("profileAvatar");

        if (firestoreData) {
          const freshName = firestoreData.name || currentName || "Not provided";
          const freshPhone = firestoreData.phone || "Not provided";
          const freshEmail = firestoreData.email || currentEmail;

          if (nameEl) nameEl.textContent = freshName;
          if (phoneEl) phoneEl.textContent = freshPhone;
          if (emailEl) emailEl.textContent = freshEmail;
          if (avatarEl && freshName !== "Not provided") {
            avatarEl.textContent = freshName.charAt(0).toUpperCase();
          }

          // Cache verified profile locally
          MedinexDB.setPrefs({
            isLoggedIn: true,
            uid: currentUid,
            userName: freshName !== "Not provided" ? freshName : currentName,
            userEmail: freshEmail,
            userPhone: freshPhone !== "Not provided" ? freshPhone : ""
          });

          if (syncStatus) {
            syncStatus.style.color = "#16A34A";
            syncStatus.innerHTML = "✓ Verified with Firestore database (users/" + currentUid.substring(0, 8) + "...)";
          }
        } else {
          // Document users/{uid} does not exist yet
          if (nameEl && !currentName) nameEl.textContent = "Not provided";
          if (phoneEl && !currentPhone) phoneEl.textContent = "Not provided";
          if (syncStatus) {
            syncStatus.style.color = "#D97706";
            syncStatus.innerHTML = "⚠ Profile document users/" + currentUid.substring(0, 8) + "... not found in Firestore";
          }
        }
      } catch (err) {
        console.warn("Error fetching Firestore profile:", err);
        const syncStatus = document.getElementById("profileSyncStatus");
        if (syncStatus) {
          syncStatus.style.color = "#64748B";
          syncStatus.textContent = "Offline Mode / Using local cached profile";
        }
      }
    }
  }

  // ==========================================
  // SCREEN 21: SETTINGS (SettingsActivity.java)
  // ==========================================

  renderSettingsScreen() {
    this.screenContainer.innerHTML = `
      <div class="screen theme-dark-settings no-nav">
        <div class="scroll-content">

          <div class="top-back-bar">
            <button class="btn-back" id="backBtn" style="background: rgba(255,255,255,0.1); color: white; border: none;">←</button>
            <div style="flex: 1;">
              <h2 class="screen-title">⚙ Settings</h2>
            </div>
          </div>

          <div id="notificationBtn" class="settings-item">
            <span>🔔 Notifications</span>
          </div>

          <div id="darkModeBtn" class="settings-item">
            <span>🌙 Dark Mode</span>
          </div>

          <div id="versionBtn" class="settings-item">
            <span>📱 App Version 1.0</span>
          </div>

          <button id="settingsLogoutBtn" class="btn-primary-gradient" style="background: #F44336; margin-top: 30px; height: 60px;">
            Logout
          </button>

        </div>
      </div>
    `;

    document.getElementById("backBtn").addEventListener("click", () => this.goBack());

    document.getElementById("notificationBtn").addEventListener("click", () => {
      this.showToast("Medicine reminders are enabled");
    });

    document.getElementById("darkModeBtn").addEventListener("click", () => {
      this.showToast("Dark Mode is active");
    });

    document.getElementById("versionBtn").addEventListener("click", () => {
      this.showToast("Medinex Version 1.0");
    });

    document.getElementById("settingsLogoutBtn").addEventListener("click", async () => {
      const logoutBtn = document.getElementById("settingsLogoutBtn");
      logoutBtn.disabled = true;
      logoutBtn.textContent = "Logging out...";
      try {
        await logoutFromFirebase();
      } catch (err) {
        console.warn("Settings logout error:", err);
      }
      MedinexDB.clearPrefs();
      this.showToast("Logged out successfully");
      this.navigateTo("login", false);
    });
  }
}

// Instantiate on DOM load and attach to window
window.addEventListener("DOMContentLoaded", () => {
  window.MedinexRouter = new MedinexApp();
  window.MedinexDataset = MedicineDatasetManager;
  window.MedicineDatasetManager = MedicineDatasetManager;
  window.MedinexDB = MedinexDB;
  window.MedicineApi = MedicineApi;
  window.ImagePreprocessor = ImagePreprocessor;
});

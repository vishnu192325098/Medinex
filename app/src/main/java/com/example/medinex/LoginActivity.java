
package com.example.medinex;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    private EditText emailInput, passwordInput;
    private Button loginBtn;
    private TextView registerText;

    private FirebaseAuth auth;
    private FirebaseFirestore firestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        loginBtn = findViewById(R.id.loginBtn);
        registerText = findViewById(R.id.registerText);

        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        // If user already authenticated with a valid session, navigate directly to Dashboard
        SharedPreferences existingPrefs = getSharedPreferences("MedinexUser", MODE_PRIVATE);
        if (existingPrefs.getBoolean("isLoggedIn", false) && auth.getCurrentUser() != null) {
            startActivity(new Intent(LoginActivity.this, DashboardActivity.class));
            finish();
            return;
        }

        loginBtn.setOnClickListener(v -> loginUser());

        registerText.setOnClickListener(v ->
                startActivity(new Intent(
                        LoginActivity.this,
                        RegisterActivity.class
                ))
        );
    }

    private void loginUser() {
        String email = emailInput.getText().toString().trim().toLowerCase();
        String password = passwordInput.getText().toString();

        if (email.isEmpty() || password.isEmpty()) {
            showMessage("Please fill all fields");
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showMessage("Please enter a valid email address");
            return;
        }

        loginBtn.setEnabled(false);
        loginBtn.setText("Logging in...");

        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser firebaseUser = auth.getCurrentUser();

                        if (firebaseUser == null) {
                            loginBtn.setEnabled(true);
                            loginBtn.setText("Login");
                            showMessage("Unable to load your account");
                            return;
                        }

                        String uid = firebaseUser.getUid();
                        String fallbackName = firebaseUser.getDisplayName();
                        if (fallbackName == null || fallbackName.trim().isEmpty()) {
                            fallbackName = email.contains("@") ? email.split("@")[0] : email;
                        }

                        // Save authenticated session immediately
                        SharedPreferences prefs = getSharedPreferences("MedinexUser", MODE_PRIVATE);
                        prefs.edit()
                                .putBoolean("isLoggedIn", true)
                                .putString("userId", uid)
                                .putString("userName", fallbackName)
                                .putString("userEmail", email)
                                .apply();

                        Toast.makeText(LoginActivity.this, "Welcome " + fallbackName + " 👋", Toast.LENGTH_SHORT).show();

                        // Asynchronously synchronize Firestore profile without blocking navigation
                        try {
                            final String initialName = fallbackName;
                            firestore.collection("users")
                                    .document(uid)
                                    .get()
                                    .addOnSuccessListener(snapshot -> {
                                        if (snapshot != null && snapshot.exists()) {
                                            String remoteName = snapshot.getString("name");
                                            String remotePhone = snapshot.getString("phone");
                                            if (remoteName != null && !remoteName.trim().isEmpty()) {
                                                prefs.edit()
                                                        .putString("userName", remoteName)
                                                        .putString("userPhone", remotePhone != null ? remotePhone : "")
                                                        .apply();
                                            }
                                        } else {
                                            Map<String, Object> autoProfile = new HashMap<>();
                                            autoProfile.put("uid", uid);
                                            autoProfile.put("name", initialName);
                                            autoProfile.put("email", email);
                                            autoProfile.put("phone", "");
                                            autoProfile.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
                                            firestore.collection("users").document(uid).set(autoProfile);
                                        }
                                    });
                        } catch (Exception ignored) {}

                        // Navigate immediately to Dashboard
                        Intent intent = new Intent(LoginActivity.this, DashboardActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        finish();

                    } else {
                        loginBtn.setEnabled(true);
                        loginBtn.setText("Login");
                        String message;
                        if (task.getException() instanceof com.google.firebase.auth.FirebaseAuthInvalidUserException) {
                            message = "No account found with this email. Please register first.";
                        } else if (task.getException() instanceof com.google.firebase.auth.FirebaseAuthInvalidCredentialsException) {
                            message = "Invalid password. Please check your credentials.";
                        } else {
                            message = task.getException() != null
                                    ? task.getException().getMessage()
                                    : "Login failed";
                        }
                        showMessage(message);
                    }
                });
    }

    private void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}

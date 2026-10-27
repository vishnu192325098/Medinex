
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

        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser firebaseUser = auth.getCurrentUser();

                        if (firebaseUser == null) {
                            loginBtn.setEnabled(true);
                            showMessage("Unable to load your account");
                            return;
                        }

                        String uid = firebaseUser.getUid();

                        firestore.collection("users")
                                .document(uid)
                                .get()
                                .addOnCompleteListener(profileTask -> {
                                    loginBtn.setEnabled(true);

                                    String name = null;
                                    String phone = null;

                                    if (profileTask.isSuccessful() && profileTask.getResult() != null) {
                                        if (profileTask.getResult().exists()) {
                                            name = profileTask.getResult().getString("name");
                                            phone = profileTask.getResult().getString("phone");
                                        } else {
                                            // Document does not exist in Firestore yet: create it gracefully
                                            String fallbackName = firebaseUser.getDisplayName();
                                            if (fallbackName == null || fallbackName.trim().isEmpty()) {
                                                fallbackName = email.contains("@") ? email.split("@")[0] : email;
                                            }
                                            Map<String, Object> autoProfile = new HashMap<>();
                                            autoProfile.put("uid", uid);
                                            autoProfile.put("name", fallbackName);
                                            autoProfile.put("email", email);
                                            autoProfile.put("phone", "");
                                            autoProfile.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
                                            firestore.collection("users").document(uid).set(autoProfile);
                                            name = fallbackName;
                                        }
                                    }

                                    if (name == null || name.trim().isEmpty()) {
                                        name = firebaseUser.getDisplayName();
                                    }

                                    if (name == null || name.trim().isEmpty()) {
                                        name = email.contains("@") ? email.split("@")[0] : email;
                                    }

                                    SharedPreferences prefs =
                                            getSharedPreferences(
                                                    "MedinexUser",
                                                    MODE_PRIVATE
                                            );

                                    prefs.edit()
                                            .putBoolean("isLoggedIn", true)
                                            .putString("userId", uid)
                                            .putString("userName", name)
                                            .putString("userEmail", email)
                                            .putString("userPhone",
                                                    phone == null ? "" : phone)
                                            .apply();

                                    Toast.makeText(
                                            LoginActivity.this,
                                            "Welcome " + name,
                                            Toast.LENGTH_SHORT
                                    ).show();

                                    Intent intent = new Intent(
                                            LoginActivity.this,
                                            DashboardActivity.class
                                    );

                                    startActivity(intent);
                                    finish();
                                });

                    } else {
                        loginBtn.setEnabled(true);
                        String message = task.getException() != null
                                ? task.getException().getMessage()
                                : "Login failed";

                        showMessage(message);
                    }
                });
    }

    private void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}

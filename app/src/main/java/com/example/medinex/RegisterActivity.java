
package com.example.medinex;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private EditText nameInput, emailInput, phoneInput, passwordInput;
    private Button registerBtn;

    private FirebaseAuth auth;
    private FirebaseFirestore firestore;

    private String pendingUid = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        nameInput = findViewById(R.id.nameInput);
        emailInput = findViewById(R.id.emailInput);
        phoneInput = findViewById(R.id.phoneInput);
        passwordInput = findViewById(R.id.passwordInput);
        registerBtn = findViewById(R.id.registerBtn);

        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        registerBtn.setOnClickListener(v -> registerUser());
    }

    private void registerUser() {
        String name = nameInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim().toLowerCase();
        String phone = phoneInput.getText().toString().trim();
        String password = passwordInput.getText().toString();

        if (name.isEmpty() || email.isEmpty()
                || phone.isEmpty()) {
            showMessage("Please fill all fields");
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showMessage("Please enter a valid email address");
            return;
        }

        if (phone.length() < 10) {
            showMessage("Please enter a valid phone number");
            return;
        }

        // If user already authenticated in this session but profile save was pending
        if (pendingUid != null) {
            saveProfileToFirestore(pendingUid, name, email, phone);
            return;
        }

        if (password.isEmpty() || password.length() < 6) {
            showMessage("Password must contain at least 6 characters");
            return;
        }

        registerBtn.setEnabled(false);
        registerBtn.setText("Creating Account...");

        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (!task.isSuccessful()) {
                        registerBtn.setEnabled(true);
                        registerBtn.setText("Register");

                        if (task.getException() instanceof com.google.firebase.auth.FirebaseAuthUserCollisionException) {
                            showMessage("This email is already registered. Please log in instead.");
                        } else {
                            String message = task.getException() != null
                                    ? task.getException().getMessage()
                                    : "Registration failed";
                            showMessage(message);
                        }
                        return;
                    }

                    FirebaseUser firebaseUser = auth.getCurrentUser();

                    if (firebaseUser == null) {
                        registerBtn.setEnabled(true);
                        registerBtn.setText("Register");
                        showMessage("Account created, but user details could not be loaded");
                        return;
                    }

                    pendingUid = firebaseUser.getUid();
                    saveProfileToFirestore(pendingUid, name, email, phone);
                });
    }

    private void saveProfileToFirestore(String uid, String name, String email, String phone) {
        registerBtn.setEnabled(false);
        registerBtn.setText("Saving Profile...");

        Map<String, Object> profile = new HashMap<>();
        profile.put("uid", uid);
        profile.put("name", name);
        profile.put("email", email);
        profile.put("phone", phone);
        profile.put("createdAt",
                com.google.firebase.firestore.FieldValue.serverTimestamp());

        firestore.collection("users")
                .document(uid)
                .set(profile)
                .addOnCompleteListener(saveTask -> {
                    registerBtn.setEnabled(true);

                    if (!saveTask.isSuccessful()) {
                        registerBtn.setText("Retry Saving Profile");
                        String errorMsg = saveTask.getException() != null ? saveTask.getException().getMessage() : "Unknown error";
                        showMessage(
                                "Your account was created, but saving profile failed (" + errorMsg + "). Tap 'Retry Saving Profile'."
                        );
                        return;
                    }

                    pendingUid = null;
                    registerBtn.setText("Register");

                    Toast.makeText(
                            RegisterActivity.this,
                            "Registration successful! Account and profile saved.",
                            Toast.LENGTH_LONG
                    ).show();

                    // Sign out and transition to LoginActivity
                    auth.signOut();

                    Intent intent = new Intent(
                            RegisterActivity.this,
                            LoginActivity.class
                    );
                    intent.addFlags(
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                                    | Intent.FLAG_ACTIVITY_SINGLE_TOP
                    );

                    startActivity(intent);
                    finish();
                });
    }

    private void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}

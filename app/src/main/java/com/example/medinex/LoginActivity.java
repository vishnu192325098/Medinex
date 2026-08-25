package com.example.medinex;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {

    EditText emailInput, passwordInput;
    Button loginBtn;
    TextView registerText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_login);

        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        loginBtn = findViewById(R.id.loginBtn);
        registerText = findViewById(R.id.registerText);

        loginBtn.setOnClickListener(v -> {

            String username =
                    emailInput.getText()
                            .toString()
                            .trim();

            String password =
                    passwordInput.getText()
                            .toString()
                            .trim();

            if (username.isEmpty() || password.isEmpty()) {

                Toast.makeText(
                        LoginActivity.this,
                        "Please fill all fields",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            AppDatabase db =
                    AppDatabase.getInstance(
                            LoginActivity.this
                    );

            UserDao userDao = db.userDao();

            User user;

            if (username.contains("@")) {

                user = userDao.loginWithEmail(
                        username,
                        password
                );

            } else {

                user = userDao.loginWithPhone(
                        username,
                        password
                );
            }

            if (user != null) {

                SharedPreferences prefs =
                        getSharedPreferences(
                                "MedinexUser",
                                MODE_PRIVATE
                        );

                prefs.edit()
                        .putBoolean("isLoggedIn", true)
                        .putString("userName", user.getName())
                        .putString("userEmail", user.getEmail())
                        .putString("userPhone", user.getPhone())
                        .apply();

                Toast.makeText(
                        LoginActivity.this,
                        "Welcome " + user.getName(),
                        Toast.LENGTH_SHORT
                ).show();

                Intent intent =
                        new Intent(
                                LoginActivity.this,
                                DashboardActivity.class
                        );

                startActivity(intent);

                finish();

            } else {

                Toast.makeText(
                        LoginActivity.this,
                        "Invalid Email/Phone or Password",
                        Toast.LENGTH_LONG
                ).show();
            }
        });

        registerText.setOnClickListener(v -> {

            Intent intent =
                    new Intent(
                            LoginActivity.this,
                            RegisterActivity.class
                    );

            startActivity(intent);
        });
    }
}
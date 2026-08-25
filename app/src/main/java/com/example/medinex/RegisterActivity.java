package com.example.medinex;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class RegisterActivity extends AppCompatActivity {

    EditText nameInput, emailInput, phoneInput, passwordInput;
    Button registerBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_register);

        nameInput = findViewById(R.id.nameInput);
        emailInput = findViewById(R.id.emailInput);
        phoneInput = findViewById(R.id.phoneInput);
        passwordInput = findViewById(R.id.passwordInput);
        registerBtn = findViewById(R.id.registerBtn);

        registerBtn.setOnClickListener(v -> {

            String name =
                    nameInput.getText()
                            .toString()
                            .trim();

            String email =
                    emailInput.getText()
                            .toString()
                            .trim()
                            .toLowerCase();

            String phone =
                    phoneInput.getText()
                            .toString()
                            .trim();

            String password =
                    passwordInput.getText()
                            .toString()
                            .trim();

            if (name.isEmpty()
                    || email.isEmpty()
                    || phone.isEmpty()
                    || password.isEmpty()) {

                Toast.makeText(
                        RegisterActivity.this,
                        "Please fill all fields",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            if (!email.contains("@")
                    || !email.contains(".")) {

                Toast.makeText(
                        RegisterActivity.this,
                        "Please enter a valid email",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            if (phone.length() < 10) {

                Toast.makeText(
                        RegisterActivity.this,
                        "Please enter a valid phone number",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            AppDatabase db =
                    AppDatabase.getInstance(
                            RegisterActivity.this
                    );

            UserDao userDao = db.userDao();

            if (userDao.checkEmailExists(email) > 0) {

                Toast.makeText(
                        RegisterActivity.this,
                        "Email already registered",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            if (userDao.checkPhoneExists(phone) > 0) {

                Toast.makeText(
                        RegisterActivity.this,
                        "Phone number already registered",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            User user =
                    new User(
                            name,
                            email,
                            phone,
                            password
                    );

            userDao.insert(user);

            Toast.makeText(
                    RegisterActivity.this,
                    "Registration Successful. Please login.",
                    Toast.LENGTH_LONG
            ).show();

            finish();
        });
    }
}
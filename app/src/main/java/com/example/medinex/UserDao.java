package com.example.medinex;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

@Dao
public interface UserDao {

    @Insert
    void insert(User user);

    @Query("SELECT * FROM users WHERE LOWER(email) = LOWER(:email) LIMIT 1")
    User getUserByEmail(String email);

    @Query("SELECT * FROM users WHERE phone = :phone LIMIT 1")
    User getUserByPhone(String phone);

    @Query("SELECT COUNT(*) FROM users WHERE LOWER(email) = LOWER(:email)")
    int checkEmailExists(String email);

    @Query("SELECT COUNT(*) FROM users WHERE phone = :phone")
    int checkPhoneExists(String phone);
}
package com.example.mywebsite.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserService userService;

    public boolean registerUserWithHash(String password, String email) {
        try {
            String hashedPassword = passwordEncoder.encode(password);
            // Здесь будем сохранять пользователя с хэшированным паролем
            // Пока просто логируем
            System.out.println("Регистрация пользователя: " + email + " с хэшированным паролем");
            return userService.registerUser(hashedPassword, email);
        } catch (Exception e) {
            System.err.println("Ошибка при регистрации: " + e.getMessage());
            return false;
        }
    }

    public boolean validatePassword(String rawPassword, String hashedPassword) {
        return passwordEncoder.matches(rawPassword, hashedPassword);
    }
}
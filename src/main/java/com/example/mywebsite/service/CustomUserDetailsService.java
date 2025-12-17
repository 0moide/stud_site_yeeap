package com.example.mywebsite.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserService userService;

    @Override
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        System.out.println("Попытка аутентификации по: " + identifier);
        
        com.example.mywebsite.entity.User user = null;
        
        // Проверяем, это email или ID
        if (identifier.contains("@")) {
            // Это email
            user = userService.findByEmail(identifier);
        } else if (identifier.matches("\\d+")) {
            // Это ID (только цифры) - проверяем Google и GitHub
            user = userService.findByGoogleId(identifier);
            if (user == null) {
                user = userService.findByGithubId(identifier);
            }
        }
        
        if (user != null) {
            System.out.println("Найден пользователь: " + user.getEmail() + 
                             ", Пароль: " + (user.getPassword() != null ? "есть" : "нет"));
            
            return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())  // Всегда используем email как username
                .password(user.getPassword() != null ? user.getPassword() : "")
                .roles("USER")
                .build();
        }
        
        System.out.println("Пользователь не найден: " + identifier);
        throw new UsernameNotFoundException("Пользователь не найден: " + identifier);
    }
}
package com.example.mywebsite.entity;

public class User {
    private String password;
    private String email;
    private String googleId;
    private String githubId;
    
    public User() {}
    
    public User(String password, String email) {
        this.password = password;
        this.email = email;
    }
    
    // Геттеры и сеттеры
    public String getGoogleId() { return googleId; }
    public void setGoogleId(String googleId) { this.googleId = googleId; }

    public String getGithubId() { return githubId; }
    public void setGithubId(String githubId) { this.githubId = githubId; }
    
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
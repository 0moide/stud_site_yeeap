package com.example.mywebsite.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.example.mywebsite.entity.User;

import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/oauth")
public class OAuth2Controller {

    @Autowired
    private com.example.mywebsite.service.UserService userService;

    // Начало процесса привязки Google
    @GetMapping("/link/google")
    public String startLinkGoogle(HttpSession session, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }
        
        String email = authentication.getName();
        System.out.println("Начинаем привязку Google для пользователя: " + email);
        
        // Сохраняем email в сессии
        session.setAttribute("linking_email", email);
        session.setAttribute("linking_provider", "google");
        
        // Редирект на OAuth авторизацию
        return "redirect:/oauth2/authorization/google";
    }

    // Начало процесса привязки GitHub
    @GetMapping("/link/github")
    public String startLinkGithub(HttpSession session, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }
        
        String email = authentication.getName();
        System.out.println("Начинаем привязку GitHub для пользователя: " + email);
        
        session.setAttribute("linking_email", email);
        session.setAttribute("linking_provider", "github");
        
        return "redirect:/oauth2/authorization/github";
    }

    @GetMapping("/unlink/google")
    public String unlinkGoogleAccount(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }
        
        String email = authentication.getName();
        com.example.mywebsite.entity.User user = userService.findByEmail(email);
        if (user != null) {
            userService.unlinkGoogleId(email);
            System.out.println("Отвязан Google от пользователя: " + email);
        }
        return "redirect:/oauth-management?success=google_unlinked";
    }

    @GetMapping("/unlink/github")
    public String unlinkGithubAccount(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }
        
        String email = authentication.getName();
        com.example.mywebsite.entity.User user = userService.findByEmail(email);
        if (user != null) {
            userService.unlinkGithubId(email);
            System.out.println("Отвязан GitHub от пользователя: " + email);
        }
        return "redirect:/oauth-management?success=github_unlinked";
    }

    @GetMapping("/check-linked-account")
    @ResponseBody
    public String checkLinkedAccount(@RequestParam String provider, 
                                    @RequestParam String providerId,
                                    Authentication authentication) {
        
        String currentEmail = authentication.getName();
        // Если currentEmail это ID, ищем реальный email
        if (currentEmail.matches("\\d+")) {
            User user = userService.findByGoogleId(currentEmail);
            if (user == null) {
                user = userService.findByGithubId(currentEmail);
            }
            if (user != null) {
                currentEmail = user.getEmail();
            }
        }
        
        StringBuilder result = new StringBuilder();
        
        if ("google".equals(provider)) {
            if (userService.isGoogleIdAlreadyUsed(providerId, currentEmail)) {
                // Находим, к какому пользователю привязан
                User user = userService.findByGoogleId(providerId);
                if (user != null) {
                    result.append("Этот Google аккаунт уже привязан к пользователю: ")
                        .append(user.getEmail());
                }
            } else {
                result.append("Google аккаунт свободен для привязки");
            }
        } else if ("github".equals(provider)) {
            if (userService.isGithubIdAlreadyUsed(providerId, currentEmail)) {
                User user = userService.findByGithubId(providerId);
                if (user != null) {
                    result.append("Этот GitHub аккаунт уже привязан к пользователю: ")
                        .append(user.getEmail());
                }
            } else {
                result.append("GitHub аккаунт свободен для привязки");
            }
        }
        
        return result.toString();
    }
    
}
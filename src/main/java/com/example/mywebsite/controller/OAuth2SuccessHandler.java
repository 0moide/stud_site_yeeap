package com.example.mywebsite.controller;

import com.example.mywebsite.service.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.Map;

@Component
public class OAuth2SuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    @Autowired
    private UserService userService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, 
                                       HttpServletResponse response, 
                                       Authentication authentication) throws IOException, ServletException {
        
        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        String provider = oauthToken.getAuthorizedClientRegistrationId();
        Map<String, Object> attributes = oauthToken.getPrincipal().getAttributes();
        
        HttpSession session = request.getSession();
        String linkingEmail = (String) session.getAttribute("linking_email");
        String linkingProvider = (String) session.getAttribute("linking_provider");
        
        String providerId = null;
        String providerEmail = null;
        
        if ("google".equals(provider)) {
            providerId = (String) attributes.get("sub");
            providerEmail = (String) attributes.get("email");
        } else if ("github".equals(provider)) {
            providerId = attributes.get("id").toString();
            providerEmail = attributes.get("login") + "@github.com";
            if (attributes.get("email") != null) {
                providerEmail = (String) attributes.get("email");
            }
        }
        
        System.out.println("=== OAuth2 Успешная авторизация ===");
        System.out.println("Провайдер: " + provider);
        System.out.println("Provider ID: " + providerId);
        System.out.println("Provider Email: " + providerEmail);
        System.out.println("Linking Email: " + linkingEmail);
        System.out.println("Linking Provider: " + linkingProvider);
        System.out.println("================================");
        
        // Если это привязка к существующему аккаунту
        if (linkingEmail != null && linkingProvider != null && linkingProvider.equals(provider) && providerId != null) {
            System.out.println("Режим: ПРИВЯЗКА аккаунта");
            
            // ВАЖНО: Очищаем сессию сразу, чтобы не было повторного использования
            session.removeAttribute("linking_email");
            session.removeAttribute("linking_provider");
            
            String result = null;
            String errorMessage = null;
            
            if ("google".equals(provider)) {
                // Проверяем, не привязан ли уже этот Google ID
                if (userService.isGoogleIdAlreadyUsed(providerId, linkingEmail)) {
                    errorMessage = "Этот Google аккаунт уже привязан к другому пользователю. " +
                                  "Сначала отвяжите его от того аккаунта.";
                    System.out.println("ОШИБКА: " + errorMessage);
                } else {
                    result = userService.updateGoogleId(linkingEmail, providerId);
                    System.out.println("Результат привязки Google: " + result);
                    
                    if ("SUCCESS".equals(result)) {
                        // ВАЖНО: После успешной привязки, выходим из OAuth сессии
                        // чтобы не оставаться аутентифицированным через Google
                        // и возвращаемся к исходному пользователю
                        request.logout();
                        
                        // Редирект с сообщением об успехе
                        response.sendRedirect("/login?oauth_link_success=google&email=" + 
                                            URLEncoder.encode(linkingEmail, "UTF-8"));
                        return;
                    }
                }
            } else if ("github".equals(provider)) {
                // Проверяем, не привязан ли уже этот GitHub ID
                if (userService.isGithubIdAlreadyUsed(providerId, linkingEmail)) {
                    errorMessage = "Этот GitHub аккаунт уже привязан к другому пользователю. " +
                                  "Сначала отвяжите его от того аккаунта.";
                    System.out.println("ОШИБКА: " + errorMessage);
                } else {
                    result = userService.updateGithubId(linkingEmail, providerId);
                    System.out.println("Результат привязки GitHub: " + result);
                    
                    if ("SUCCESS".equals(result)) {
                        request.logout();
                        response.sendRedirect("/login?oauth_link_success=github&email=" + 
                                            URLEncoder.encode(linkingEmail, "UTF-8"));
                        return;
                    }
                }
            }
            
            if (errorMessage != null) {
                session.setAttribute("oauth_error", errorMessage);
                // Выходим из OAuth сессии
                request.logout();
                response.sendRedirect("/login?oauth_link_error=" + URLEncoder.encode(errorMessage, "UTF-8"));
            } else if (result != null && !"SUCCESS".equals(result)) {
                session.setAttribute("oauth_error", result);
                request.logout();
                response.sendRedirect("/login?oauth_link_error=" + URLEncoder.encode(result, "UTF-8"));
            }
            return;
        }
        
        // Если это обычный вход через OAuth (не привязка)
        System.out.println("Режим: ОБЫЧНЫЙ ВХОД через OAuth");
        
        if (providerEmail != null && providerId != null) {
            com.example.mywebsite.entity.User user = null;
            
            if ("google".equals(provider)) {
                // Ищем пользователя по Google ID
                user = userService.findByGoogleId(providerId);
                
                if (user == null) {
                    // Если не нашли по Google ID, ищем по email
                    user = userService.findByEmail(providerEmail);
                    
                    if (user == null) {
                        // Создаем нового пользователя
                        System.out.println("Создание нового пользователя для Google: " + providerEmail);
                        userService.registerUser("", providerEmail);
                        user = userService.findByEmail(providerEmail);
                    }
                    
                    // Привязываем Google ID к найденному/созданному пользователю
                    System.out.println("Привязка Google ID к пользователю: " + providerEmail);
                    userService.updateGoogleId(providerEmail, providerId);
                } else {
                    System.out.println("Найден существующий пользователь по Google ID: " + user.getEmail());
                }
            } else if ("github".equals(provider)) {
                // Аналогично для GitHub
                user = userService.findByGithubId(providerId);
                
                if (user == null) {
                    user = userService.findByEmail(providerEmail);
                    
                    if (user == null) {
                        System.out.println("Создание нового пользователя для GitHub: " + providerEmail);
                        userService.registerUser("", providerEmail);
                        user = userService.findByEmail(providerEmail);
                    }
                    
                    System.out.println("Привязка GitHub ID к пользователю: " + providerEmail);
                    userService.updateGithubId(providerEmail, providerId);
                } else {
                    System.out.println("Найден существующий пользователь по GitHub ID: " + user.getEmail());
                }
            }
            
            // Обновляем authentication с email вместо ID
            if (user != null && user.getEmail() != null) {
                System.out.println("Устанавливаем email в authentication: " + user.getEmail());
                authentication = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                    user.getEmail(), 
                    authentication.getCredentials(), 
                    authentication.getAuthorities()
                );
                org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        
        // Стандартная обработка успешного входа
        try {
            super.onAuthenticationSuccess(request, response, authentication);
        } catch (ServletException e) {
            System.err.println("Ошибка при обработке успешного входа: " + e.getMessage());
            response.sendRedirect("/");
        }
    }
}
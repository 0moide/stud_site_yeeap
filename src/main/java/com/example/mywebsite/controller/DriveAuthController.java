// DriveAuthController.java - новый файл
package com.example.mywebsite.controller;

import com.example.mywebsite.service.GoogleDriveService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/drive")
public class DriveAuthController {

    @Autowired
    private GoogleDriveService googleDriveService;
    
    // Callback от Google OAuth2
    @GetMapping("/callback")
    public String handleGoogleCallback(
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "error", required = false) String error,
            @RequestParam(name = "state") String state,
            RedirectAttributes redirectAttributes) {
        
        if (error != null) {
            redirectAttributes.addFlashAttribute("error", 
                "Ошибка авторизации: " + error);
            return "redirect:/export/drive";
        }
        
        if (code == null || code.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", 
                "Не получен код авторизации");
            return "redirect:/export/drive";
        }
        
        try {
            if (googleDriveService.exchangeCodeForToken(code, state)) {
                redirectAttributes.addFlashAttribute("success", 
                    "Аккаунт Google Drive успешно подключен!");
            } else {
                redirectAttributes.addFlashAttribute("error", 
                    "Не удалось подключить аккаунт Google Drive");
            }
        } catch (Exception e) {
            System.err.println("Ошибка при обработке callback: " + e.getMessage());
            redirectAttributes.addFlashAttribute("error", 
                "Внутренняя ошибка: " + e.getMessage());
        }
        
        return "redirect:/export/drive";
    }
}
package com.example.mywebsite.controller;

import com.example.mywebsite.service.CsvExportService;
import com.example.mywebsite.service.GoogleDriveService;
import com.example.mywebsite.service.PdfExportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/export")
public class ExportController {

    @Autowired
    private GoogleDriveService googleDriveService;
    
    @Autowired
    private PdfExportService pdfExportService;

    @Autowired
    private CsvExportService csvExportService;
    
    // Страница управления экспортом
    @GetMapping("/drive")
    public String driveManagement(Model model) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String email = auth.getName();
            
            boolean hasAccess = googleDriveService.hasDriveAccess(email);
            List<Map<String, Object>> history = googleDriveService.getExportHistory(email);
            
            model.addAttribute("email", email);
            model.addAttribute("hasDriveAccess", hasAccess);
            model.addAttribute("history", history);
            
            if (!hasAccess) {
                String authUrl = googleDriveService.getGoogleDriveAuthUrl(email);
                model.addAttribute("authUrl", authUrl);
            } else {
                model.addAttribute("authUrl", null);
            }
            
            return "drive-management";
        } catch (Exception e) {
            System.err.println("Ошибка в driveManagement: " + e.getMessage());
            e.printStackTrace();
            return "error";
        }
    }
    
    // Инициировать выгрузку на Google Drive
    @PostMapping("/drive/upload")
    public String uploadToDrive(RedirectAttributes redirectAttributes) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String email = auth.getName();
            
            // Проверяем доступ к Drive
            if (!googleDriveService.hasDriveAccess(email)) {
                redirectAttributes.addFlashAttribute("error", 
                    "Требуется авторизация в Google Drive. Сначала подключите аккаунт.");
                return "redirect:/export/drive";
            }
            
            // Создаем PDF
            byte[] pdfContent = pdfExportService.createGradesPdf(email);
            if (pdfContent == null || pdfContent.length == 0) {
                redirectAttributes.addFlashAttribute("error", 
                    "Ошибка при создании PDF документа");
                return "redirect:/export/drive";
            }
            
            // Генерируем имя файла
            String fileName = String.format("Оценки_%s_%s.pdf", 
                email.replace("@", "_"),
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
            );
            
            // Загружаем на Google Drive
            Map<String, String> result = googleDriveService.uploadToDrive(email, pdfContent, fileName);
            
            if (result != null && "true".equals(result.get("success"))) {
                redirectAttributes.addFlashAttribute("success", 
                    "Файл успешно загружен на Google Drive!");
                redirectAttributes.addFlashAttribute("fileUrl", result.get("fileUrl"));
                redirectAttributes.addFlashAttribute("fileName", result.get("fileName"));
            } else {
                String error = result != null ? result.get("error") : "Неизвестная ошибка";
                redirectAttributes.addFlashAttribute("error", 
                    "Ошибка при загрузке на Google Drive: " + error);
            }
            
        } catch (Exception e) {
            System.err.println("Ошибка при выгрузке на Drive: " + e.getMessage());
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", 
                "Внутренняя ошибка: " + e.getMessage());
        }
        
        return "redirect:/export/drive";
    }
    
    // Отозвать доступ к Google Drive
    @PostMapping("/drive/revoke")
    public String revokeDriveAccess(RedirectAttributes redirectAttributes) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String email = auth.getName();
            
            if (googleDriveService.revokeDriveAccess(email)) {
                redirectAttributes.addFlashAttribute("success", 
                    "Доступ к Google Drive отозван");
            } else {
                redirectAttributes.addFlashAttribute("error", 
                    "Ошибка при отзыве доступа");
            }
            
        } catch (Exception e) {
            System.err.println("Ошибка при отзыве доступа: " + e.getMessage());
            redirectAttributes.addFlashAttribute("error", 
                "Внутренняя ошибка: " + e.getMessage());
        }
        
        return "redirect:/export/drive";
    }
    
    // Скачать PDF локально (для тестирования)
    @GetMapping("/download/pdf")
    @ResponseBody
    public byte[] downloadPdf() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String email = auth.getName();
            
            byte[] pdf = pdfExportService.createGradesPdf(email);
            if (pdf != null && pdf.length > 0) {
                return pdf;
            }
            return "Ошибка при создании PDF".getBytes();
        } catch (Exception e) {
            System.err.println("Ошибка в downloadPdf: " + e.getMessage());
            return ("Ошибка: " + e.getMessage()).getBytes();
        }
    }

    @GetMapping("/download/csv")
    @ResponseBody
    public ResponseEntity<byte[]> downloadCsv() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        
        try {
            byte[] csvContent = csvExportService.createCsvReport(email);
            String fileName = csvExportService.getCsvFileName(email);
            
            return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + fileName + "\"")
                .header("Content-Type", "text/csv; charset=UTF-8")
                .body(csvContent);
                
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(("Error: " + e.getMessage()).getBytes());
        }
    }
    
    // Новый метод для загрузки CSV на Google Drive
    @PostMapping("/drive/upload-csv")
    public String uploadCsvToDrive(RedirectAttributes redirectAttributes) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        
        try {
            // Проверяем доступ к Drive
            if (!googleDriveService.hasDriveAccess(email)) {
                redirectAttributes.addFlashAttribute("error", 
                    "Требуется авторизация в Google Drive");
                return "redirect:/export/drive";
            }
            
            // Создаем CSV
            byte[] csvContent = csvExportService.createCsvReport(email);
            String fileName = csvExportService.getCsvFileName(email);
            
            // Загружаем на Google Drive
            Map<String, String> result = googleDriveService.uploadToDrive(email, csvContent, fileName);
            
            if ("true".equals(result.get("success"))) {
                redirectAttributes.addFlashAttribute("success", 
                    "CSV файл успешно загружен на Google Drive!");
                redirectAttributes.addFlashAttribute("fileUrl", result.get("fileUrl"));
                redirectAttributes.addFlashAttribute("fileName", result.get("fileName"));
            } else {
                redirectAttributes.addFlashAttribute("error", 
                    "Ошибка при загрузке CSV: " + result.get("error"));
            }
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", 
                "Внутренняя ошибка: " + e.getMessage());
        }
        
        return "redirect:/export/drive";
    }
}
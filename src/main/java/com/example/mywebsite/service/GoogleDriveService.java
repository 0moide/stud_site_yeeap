// GoogleDriveService.java - новый файл
package com.example.mywebsite.service;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class GoogleDriveService {

    @Autowired
    private DatabaseService databaseService;
    
    @Value("${spring.security.oauth2.client.registration.google.client-id:}")
    private String clientId;
    
    @Value("${spring.security.oauth2.client.registration.google.client-secret:}")
    private String clientSecret;
    
    private final RestTemplate restTemplate = new RestTemplate();
    
    // Получаем URL для авторизации Google Drive
    public String getGoogleDriveAuthUrl(String email) {
        try {
            Integer userId = databaseService.getUserIdByEmail(email);
            if (userId == null) return null;
            
            String redirectUri = "http://localhost:8080/drive/callback";
            
            return UriComponentsBuilder.fromHttpUrl("https://accounts.google.com/o/oauth2/v2/auth")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", "https://www.googleapis.com/auth/drive.file")
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .queryParam("state", userId.toString())
                .build()
                .toUriString();
                
        } catch (Exception e) {
            System.err.println("Ошибка при получении URL авторизации: " + e.getMessage());
            return null;
        }
    }
    
    // Обмениваем code на токен
    public boolean exchangeCodeForToken(String code, String state) {
        try {
            Integer userId = Integer.parseInt(state);
            String redirectUri = "http://localhost:8080/drive/callback";
            
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("client_id", clientId);
            params.add("client_secret", clientSecret);
            params.add("code", code);
            params.add("grant_type", "authorization_code");
            params.add("redirect_uri", redirectUri);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(
                "https://oauth2.googleapis.com/token",
                request,
                Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> tokenData = response.getBody();
                String accessToken = (String) tokenData.get("access_token");
                String refreshToken = (String) tokenData.get("refresh_token");
                Integer expiresIn = (Integer) tokenData.get("expires_in");
                
                LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(expiresIn);
                String scope = "https://www.googleapis.com/auth/drive.file";
                
                databaseService.saveGoogleToken(userId, accessToken, refreshToken, expiresAt, scope);
                return true;
            }
        } catch (Exception e) {
            System.err.println("Ошибка при обмене code на токен: " + e.getMessage());
        }
        return false;
    }
    
    // Обновляем токен
    public boolean refreshToken(Integer userId) {
        try {
            String refreshToken = databaseService.getRefreshToken(userId);
            if (refreshToken == null || refreshToken.isEmpty()) {
                return false;
            }
            
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("client_id", clientId);
            params.add("client_secret", clientSecret);
            params.add("refresh_token", refreshToken);
            params.add("grant_type", "refresh_token");
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(
                "https://oauth2.googleapis.com/token",
                request,
                Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> tokenData = response.getBody();
                String accessToken = (String) tokenData.get("access_token");
                Integer expiresIn = (Integer) tokenData.get("expires_in");
                
                LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(expiresIn);
                databaseService.updateAccessToken(userId, accessToken, expiresAt);
                return true;
            }
        } catch (Exception e) {
            System.err.println("Ошибка при обновлении токена: " + e.getMessage());
        }
        return false;
    }
    
    // Загружаем файл на Google Drive
    public Map<String, String> uploadToDrive(String email, byte[] fileContent, String fileName) {
        Map<String, String> result = new HashMap<>();
        
        try {
            Integer userId = databaseService.getUserIdByEmail(email);
            if (userId == null) {
                result.put("error", "Пользователь не найден");
                return result;
            }
            
            // 1. ПОЛУЧАЕМ ТОКЕН ИЗ БАЗЫ
            Map<String, Object> tokenData = databaseService.getGoogleToken(userId);
            if (tokenData == null) {
                result.put("error", "Требуется авторизация в Google Drive");
                return result;
            }
            
            String accessToken = (String) tokenData.get("access_token");
            LocalDateTime expiresAt = (LocalDateTime) tokenData.get("expires_at");
            
            // 2. ВАЖНО: ПРОВЕРЯЕМ И ОБНОВЛЯЕМ ТОКЕН ПЕРЕД ИСПОЛЬЗОВАНИЕМ
            if (expiresAt == null || expiresAt.isBefore(LocalDateTime.now().minusMinutes(5))) {
                System.out.println("Токен истёк или скоро истечёт. Обновляем...");
                if (!refreshToken(userId)) {
                    result.put("error", "Не удалось обновить токен. Требуется повторная авторизация");
                    return result;
                }
                // Получаем обновлённый токен
                tokenData = databaseService.getGoogleToken(userId);
                if (tokenData == null) {
                    result.put("error", "Токен не найден после обновления");
                    return result;
                }
                accessToken = (String) tokenData.get("access_token");
            }
            
            // 3. ТЕПЕРЬ ЗАГРУЖАЕМ ФАЙЛ С АКТУАЛЬНЫМ ТОКЕНОМ
            // Создаем метаданные файла
            String mimeType;
            if (fileName.toLowerCase().endsWith(".csv")) {
                mimeType = "text/csv";
            } else if (fileName.toLowerCase().endsWith(".pdf")) {
                mimeType = "application/pdf";
            } else {
                mimeType = "text/plain";
            }
            
            // Создаем метаданные файла
            String fileMetadata = String.format("""
                {
                    "name": "%s",
                    "mimeType": "%s"
                }
                """, fileName, mimeType);
            
            // Подготавливаем multipart запрос
            String boundary = "boundary_" + System.currentTimeMillis();
            
            StringBuilder requestBody = new StringBuilder();
            requestBody.append("--").append(boundary).append("\r\n");
            requestBody.append("Content-Type: application/json; charset=UTF-8\r\n\r\n");
            requestBody.append(fileMetadata).append("\r\n");
            
            requestBody.append("--").append(boundary).append("\r\n");
            requestBody.append("Content-Type: application/pdf\r\n\r\n");
            
            // Собираем все части
            byte[] metadataPart = requestBody.toString().getBytes("UTF-8");
            byte[] filePart = fileContent;
            byte[] boundaryEnd = ("\r\n--" + boundary + "--\r\n").getBytes("UTF-8");
            
            byte[] fullBody = new byte[metadataPart.length + filePart.length + boundaryEnd.length];
            System.arraycopy(metadataPart, 0, fullBody, 0, metadataPart.length);
            System.arraycopy(filePart, 0, fullBody, metadataPart.length, filePart.length);
            System.arraycopy(boundaryEnd, 0, fullBody, metadataPart.length + filePart.length, boundaryEnd.length);
            
            // Отправляем запрос
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            headers.setContentType(MediaType.parseMediaType("multipart/related; boundary=" + boundary));
            
            HttpEntity<byte[]> request = new HttpEntity<>(fullBody, headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart",
                HttpMethod.POST,
                request,
                Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                String fileId = (String) responseBody.get("id");
                String fileUrl = "https://drive.google.com/file/d/" + fileId + "/view";
                
                // Сохраняем в историю
                databaseService.saveExportHistory(userId, fileId, fileName, fileUrl, 
                    "SUCCESS", null);
                
                result.put("fileId", fileId);
                result.put("fileUrl", fileUrl);
                result.put("fileName", fileName);
                result.put("success", "true");
                
                return result;
            }
            
        } catch (Exception e) {
            System.err.println("Ошибка при загрузке на Google Drive: " + e.getMessage());
            e.printStackTrace();
            
            Integer userId = databaseService.getUserIdByEmail(email);
            if (userId != null) {
                databaseService.saveExportHistory(userId, null, fileName, null, 
                    "ERROR", e.getMessage());
            }
            
            result.put("error", "Ошибка при загрузке файла: " + e.getMessage());
        }
        
        return result;
    }
    
    // Получаем историю выгрузок
    @Cacheable(value = "exports", key = "#email")
    public List<Map<String, Object>> getExportHistory(String email) {
        try {
            Integer userId = databaseService.getUserIdByEmail(email);
            if (userId != null) {
                return databaseService.getExportHistory(userId);
            }
        } catch (Exception e) {
            System.err.println("Ошибка при получении истории экспорта: " + e.getMessage());
        }
        return List.of();
    }

    @CacheEvict(value = "exports", key = "#email")
    public void clearExportHistoryCache(String email) {
        System.out.println("CACHE EVICT: Clearing export history for " + email);
    }
    
    // Проверяем авторизацию в Google Drive
    public boolean hasDriveAccess(String email) {
        try {
            Integer userId = databaseService.getUserIdByEmail(email);
            return userId != null && databaseService.hasGoogleDriveToken(userId);
        } catch (Exception e) {
            System.err.println("Ошибка при проверке доступа к Drive: " + e.getMessage());
            return false;
        }
    }
    
    // Удаляем связь с Google Drive
    public boolean revokeDriveAccess(String email) {
        try {
            Integer userId = databaseService.getUserIdByEmail(email);
            if (userId != null) {
                databaseService.deleteGoogleToken(userId);
                return true;
            }
        } catch (Exception e) {
            System.err.println("Ошибка при отзыве доступа к Drive: " + e.getMessage());
        }
        return false;
    }
}
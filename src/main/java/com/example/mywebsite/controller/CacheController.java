package com.example.mywebsite.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.web.bind.annotation.*;
import com.example.mywebsite.service.GradeService;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/cache")
public class CacheController {
    
    @Autowired
    private CacheManager cacheManager;
    
    @Autowired
    private GradeService gradeService;
    
    @GetMapping("/stats")
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        
        cacheManager.getCacheNames().forEach(cacheName -> {
            org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                Object nativeCache = cache.getNativeCache();
                stats.put(cacheName, nativeCache.getClass().getSimpleName());
            }
        });
        
        stats.put("totalCaches", cacheManager.getCacheNames().size());
        stats.put("cacheNames", cacheManager.getCacheNames());
        
        return stats;
    }
    
    @PostMapping("/clear/user")
    public String clearUserCache(@RequestParam String email) {
        gradeService.clearStudentCache(email);
        return "Кэш очищен для пользователя: " + email;
    }
    
    @PostMapping("/clear/all")
    public String clearAllCaches() {
        gradeService.clearAllCaches();
        return "Все кэши очищены";
    }
    
    @GetMapping("/test")
    public String testCache() {
        return """
            <h1>Тест кэширования</h1>
            <p><a href="/api/cache/stats">Статистика кэша</a></p>
            <p><a href="/grades">Проверить кэширование оценок</a></p>
            <form action="/api/cache/clear/all" method="post">
                <button type="submit">Очистить все кэши</button>
            </form>
            """;
    }
}
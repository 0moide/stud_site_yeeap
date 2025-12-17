package com.example.mywebsite;

import com.example.mywebsite.entity.User;
import com.example.mywebsite.service.AuthService;
import com.example.mywebsite.service.DatabaseService;
import com.example.mywebsite.service.ScheduleService;
import com.example.mywebsite.service.UserService;

import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Controller
public class HomeController {

    @Autowired
    private DatabaseService databaseService;

    @Autowired
    private UserService userService;

    @Autowired
    private AuthService authService;

    @Autowired
    private ScheduleService scheduleService;

    @GetMapping("/")
    public String home(Model model, Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            String username = authentication.getName();
            
            // Если имя пользователя похоже на ID (только цифры), ищем email в базе
            if (username.matches("\\d+")) {
                // Это похоже на Google ID (только цифры) или GitHub ID
                User user = userService.findByGoogleId(username);
                if (user == null) {
                    user = userService.findByGithubId(username);
                }
                
                if (user != null && user.getEmail() != null) {
                    model.addAttribute("username", user.getEmail());
                } else {
                    model.addAttribute("username", username);
                }
            } else {
                model.addAttribute("username", username);
            }
            
            model.addAttribute("isAuthenticated", true);
            // Добавляем данные для расписания (для всех пользователей)
            addScheduleDataToModel(model);
            return "index";
        } else {
            model.addAttribute("username", "anonymous");
            model.addAttribute("isAuthenticated", false);
            return "login";
        }
        
        
        
        
    }
    
    // Метод для добавления данных расписания в модель
    private void addScheduleDataToModel(Model model) {
        try {
            Map<String, List<Map<String, Object>>> scheduleByDays = scheduleService.getGroupedSchedule();
            Map<String, Object> weekInfo = scheduleService.getWeekInfo();
            List<Map<String, Object>> todaysSchedule = scheduleService.getTodaysSchedule();
            Map<String, Object> stats = scheduleService.getScheduleStats();
            
            model.addAttribute("scheduleByDays", scheduleByDays);
            model.addAttribute("weekInfo", weekInfo);
            model.addAttribute("todaysSchedule", todaysSchedule);
            model.addAttribute("stats", stats);
            model.addAttribute("hasSchedule", !scheduleByDays.isEmpty());
            
        } catch (Exception e) {
            System.err.println("Ошибка при загрузке расписания: " + e.getMessage());
            model.addAttribute("hasSchedule", false);
            model.addAttribute("scheduleError", "Не удалось загрузить расписание");
        }
    }

    @GetMapping("/login")
    public String login(@RequestParam(value = "oauth_link_success", required = false) String oauthSuccess,
                    @RequestParam(value = "oauth_link_error", required = false) String oauthError,
                    @RequestParam(value = "email", required = false) String email,
                    Model model) {
        
        if (oauthSuccess != null) {
            model.addAttribute("oauth_link_success", oauthSuccess);
        }
        if (oauthError != null) {
            model.addAttribute("oauth_link_error", oauthError);
        }
        if (email != null) {
            model.addAttribute("email", email);
        }
        
        return "login";
    }

    @GetMapping("/register")
    public String register() {
        return "register";
    }

    @PostMapping("/api/register")
    @ResponseBody
    public String registerUser(@RequestParam String password,
                            @RequestParam String email) {
        if (authService.registerUserWithHash(password, email)) {
            return "Пользователь " + email + " успешно зарегистрирован!";
        } else {
            return "Ошибка при регистрации. Возможно, пользователь уже существует.";
        }
    }
    
    @GetMapping("/api/db-test")
    @ResponseBody
    public String testDatabase() {
        String connectionTest = databaseService.testConnection();
        String tableExists = "Таблица существует: " + databaseService.tableExists();
        return connectionTest + " | " + tableExists;
    }
    
    @PostMapping("/api/create-table")
    @ResponseBody
    public String createTable() {
        databaseService.initTable();
        return "Таблица создана или уже существует. Проверка: " + databaseService.tableExists();
    }
    
    @PostMapping("/api/save-message")
    @ResponseBody
    public String saveMessage(@RequestParam String text) {
        try {
            databaseService.saveMessage(text);
            return "Сообщение '" + text + "' сохранено в базу!";
        } catch (Exception e) {
            return "Ошибка при сохранении: " + e.getMessage();
        }
    }
    
    @GetMapping("/api/get-messages")
    @ResponseBody
    public Object getMessages() {
        try {
            List<Map<String, Object>> messages = databaseService.getAllMessages();
            if (messages.isEmpty()) {
                return "В базе нет сообщений. Таблица существует: " + databaseService.tableExists();
            }
            return messages;
        } catch (Exception e) {
            return "Ошибка при получении сообщений: " + e.getMessage() + 
                   ". Таблица существует: " + databaseService.tableExists();
        }
    }

    @GetMapping("/debug/users")
    @ResponseBody
    public String debugUsers() {
        StringBuilder debug = new StringBuilder();
        debug.append("=== Users in Database ===<br>");
        
        try {
            List<Map<String, Object>> users = userService.getAllUsers();
            for (Map<String, Object> user : users) {
                debug.append("Username: ").append(user.get("username"))
                    .append(" | Password: ").append(user.get("password"))
                    .append("<br>");
            }
            
            if (users.isEmpty()) {
                debug.append("Нет пользователей в базе<br>");
            }
        } catch (Exception e) {
            debug.append("Ошибка: ").append(e.getMessage()).append("<br>");
        }
        
        return debug.toString();
    }

    @PostMapping("/api/recreate-users")
    @ResponseBody
    public String recreateUsersTable() {
        userService.recreateTable();
        return "Таблица users пересоздана. Проверьте <a href='/debug/users'>здесь</a>";
    }
    
    @GetMapping("/oauth-management")
    public String oauthManagement(Model model, Authentication authentication, HttpSession session) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }
        
        String username = authentication.getName();
        String email = username;
        
        // Если username это ID (цифры), ищем email
        if (username.matches("\\d+")) {
            User userById = userService.findByGoogleId(username);
            if (userById == null) {
                userById = userService.findByGithubId(username);
            }
            if (userById != null) {
                email = userById.getEmail();
            }
        }
        
        model.addAttribute("email", email);
        model.addAttribute("username", username);
        
        // Получаем полную информацию о пользователе
        User user = userService.getUserWithDetails(email);
        
        if (user != null) {
            boolean googleConnected = user.getGoogleId() != null && !user.getGoogleId().isEmpty();
            boolean githubConnected = user.getGithubId() != null && !user.getGithubId().isEmpty();
            
            model.addAttribute("googleConnected", googleConnected);
            model.addAttribute("githubConnected", githubConnected);
            model.addAttribute("googleId", user.getGoogleId());
            model.addAttribute("githubId", user.getGithubId());
            
            System.out.println("Статус привязки для " + email + 
                            ": Google=" + googleConnected + " (ID: " + user.getGoogleId() + ")" +
                            ", GitHub=" + githubConnected + " (ID: " + user.getGithubId() + ")");
        } else {
            model.addAttribute("googleConnected", false);
            model.addAttribute("githubConnected", false);
            model.addAttribute("googleId", null);
            model.addAttribute("githubId", null);
            System.out.println("Пользователь не найден в БД: " + email);
        }
        
        return "oauth-management";
    }

    @GetMapping("/debug/oauth-session")
    @ResponseBody
    public String debugOAuthSession(HttpSession session, Authentication authentication) {
        StringBuilder result = new StringBuilder();
        result.append("<h1>Отладка OAuth сессии</h1>");
        
        result.append("<h2>Текущий пользователь:</h2>");
        if (authentication != null && authentication.isAuthenticated()) {
            result.append("Имя: ").append(authentication.getName()).append("<br>");
            result.append("Аутентифицирован: ").append(authentication.isAuthenticated()).append("<br>");
        } else {
            result.append("Не аутентифицирован<br>");
        }
        
        result.append("<h2>Данные в сессии:</h2>");
        String linkingEmail = (String) session.getAttribute("linking_email");
        String linkingProvider = (String) session.getAttribute("linking_provider");
        
        result.append("linking_email: ").append(linkingEmail != null ? linkingEmail : "null").append("<br>");
        result.append("linking_provider: ").append(linkingProvider != null ? linkingProvider : "null").append("<br>");
        
        result.append("<h2>Действия:</h2>");
        result.append("<a href='/oauth/link/google'>Привязать Google</a><br>");
        result.append("<a href='/oauth/link/github'>Привязать GitHub</a><br>");
        result.append("<a href='/oauth-management'>Вернуться к управлению</a>");
        
        return result.toString();
    }

    @GetMapping("/debug/user-info")
    @ResponseBody
    public String debugUserInfo(Authentication authentication) {
        StringBuilder result = new StringBuilder();
        result.append("<h1>Информация о пользователе</h1>");
        
        if (authentication != null && authentication.isAuthenticated()) {
            String username = authentication.getName();
            result.append("<h2>Authentication:</h2>");
            result.append("Имя: ").append(username).append("<br>");
            result.append("Тип: ").append(authentication.getClass().getSimpleName()).append("<br>");
            
            if (authentication instanceof OAuth2AuthenticationToken) {
                OAuth2AuthenticationToken oauth = (OAuth2AuthenticationToken) authentication;
                result.append("Провайдер: ").append(oauth.getAuthorizedClientRegistrationId()).append("<br>");
            }
            
            result.append("<h2>Поиск в БД:</h2>");
            
            // Поиск по email
            User userByEmail = userService.findByEmail(username);
            if (userByEmail != null) {
                result.append("Найден по email:<br>");
                result.append("- Email: ").append(userByEmail.getEmail()).append("<br>");
                result.append("- Google ID: ").append(userByEmail.getGoogleId()).append("<br>");
                result.append("- GitHub ID: ").append(userByEmail.getGithubId()).append("<br>");
            } else {
                result.append("Не найден по email<br>");
            }
            
            // Если username это цифры, ищем по ID
            if (username.matches("\\d+")) {
                User userByGoogleId = userService.findByGoogleId(username);
                User userByGithubId = userService.findByGithubId(username);
                
                if (userByGoogleId != null) {
                    result.append("Найден по Google ID:<br>");
                    result.append("- Email: ").append(userByGoogleId.getEmail()).append("<br>");
                }
                
                if (userByGithubId != null) {
                    result.append("Найден по GitHub ID:<br>");
                    result.append("- Email: ").append(userByGithubId.getEmail()).append("<br>");
                }
            }
        } else {
            result.append("Не аутентифицирован");
        }
        
        result.append("<br><a href='/'>На главную</a>");
        return result.toString();
    }

    @GetMapping("/debug/oauth-links")
    @ResponseBody
    public String debugOAuthLinks() {
        StringBuilder result = new StringBuilder();
        result.append("<h1>📊 Все OAuth привязки</h1>");
        
        try {
            // Получаем все данные через DatabaseService
            List<Map<String, Object>> allUsers = databaseService.getUsersWithOAuthLinks();
            List<Map<String, Object>> googleUsers = databaseService.getUsersWithGoogle();
            List<Map<String, Object>> githubUsers = databaseService.getUsersWithGithub();
            List<Map<String, Object>> googleDuplicates = databaseService.getGoogleDuplicates();
            List<Map<String, Object>> githubDuplicates = databaseService.getGithubDuplicates();
            
            // Все пользователи
            result.append("<h2>👥 Все пользователи:</h2>");
            if (!allUsers.isEmpty()) {
                result.append("<table border='1' style='border-collapse: collapse; width: 100%;'>");
                result.append("<tr style='background-color: #f2f2f2;'>");
                result.append("<th style='padding: 8px;'>Email</th>");
                result.append("<th style='padding: 8px;'>Google ID</th>");
                result.append("<th style='padding: 8px;'>GitHub ID</th>");
                result.append("</tr>");
                
                for (Map<String, Object> user : allUsers) {
                    String email = (String) user.get("email");
                    String googleId = (String) user.get("google_id");
                    String githubId = (String) user.get("github_id");
                    
                    result.append("<tr>");
                    result.append("<td style='padding: 8px;'>").append(email).append("</td>");
                    result.append("<td style='padding: 8px;'>").append(googleId != null && !googleId.isEmpty() ? googleId : "—").append("</td>");
                    result.append("<td style='padding: 8px;'>").append(githubId != null && !githubId.isEmpty() ? githubId : "—").append("</td>");
                    result.append("</tr>");
                }
                result.append("</table>");
                result.append("<p>Всего пользователей: ").append(allUsers.size()).append("</p>");
            } else {
                result.append("<p>Нет пользователей в базе</p>");
            }
            
            // Google привязки
            result.append("<h2>🔴 Google привязки:</h2>");
            if (!googleUsers.isEmpty()) {
                result.append("<table border='1' style='border-collapse: collapse;'>");
                result.append("<tr><th>Email</th><th>Google ID</th></tr>");
                for (Map<String, Object> user : googleUsers) {
                    result.append("<tr>");
                    result.append("<td>").append(user.get("email")).append("</td>");
                    result.append("<td>").append(user.get("google_id")).append("</td>");
                    result.append("</tr>");
                }
                result.append("</table>");
                result.append("<p>Пользователей с Google: ").append(googleUsers.size()).append("</p>");
            } else {
                result.append("<p>Нет Google привязок</p>");
            }
            
            // GitHub привязки
            result.append("<h2>⚫ GitHub привязки:</h2>");
            if (!githubUsers.isEmpty()) {
                result.append("<table border='1' style='border-collapse: collapse;'>");
                result.append("<tr><th>Email</th><th>GitHub ID</th></tr>");
                for (Map<String, Object> user : githubUsers) {
                    result.append("<tr>");
                    result.append("<td>").append(user.get("email")).append("</td>");
                    result.append("<td>").append(user.get("github_id")).append("</td>");
                    result.append("</tr>");
                }
                result.append("</table>");
                result.append("<p>Пользователей с GitHub: ").append(githubUsers.size()).append("</p>");
            } else {
                result.append("<p>Нет GitHub привязок</p>");
            }
            
            // Статистика
            result.append("<h2>📈 Статистика:</h2>");
            result.append("<ul>");
            result.append("<li>Всего пользователей: ").append(allUsers.size()).append("</li>");
            result.append("<li>С привязанным Google: ").append(googleUsers.size()).append("</li>");
            result.append("<li>С привязанным GitHub: ").append(githubUsers.size()).append("</li>");
            result.append("<li>Без OAuth привязок: ").append(allUsers.size() - googleUsers.size() - githubUsers.size()).append("</li>");
            result.append("</ul>");
            
            // Проверка дубликатов
            result.append("<h2>🔍 Проверка дубликатов:</h2>");
            
            if (!googleDuplicates.isEmpty()) {
                result.append("<div style='background-color: #ffe6e6; padding: 15px; border-left: 4px solid #ff3333; margin: 10px 0;'>");
                result.append("<h3 style='color: #ff3333; margin-top: 0;'>⚠️ Обнаружены дубликаты Google ID:</h3>");
                for (Map<String, Object> dup : googleDuplicates) {
                    result.append("<p><strong>Google ID: ").append(dup.get("google_id")).append("</strong><br>");
                    result.append("Используется ").append(dup.get("count")).append(" раз(а)<br>");
                    result.append("Пользователи: ").append(dup.get("emails")).append("</p>");
                }
                result.append("</div>");
            } else {
                result.append("<div style='background-color: #e6ffe6; padding: 10px; border-left: 4px solid #33cc33;'>");
                result.append("<p>✅ Нет дубликатов Google ID</p>");
                result.append("</div>");
            }
            
            if (!githubDuplicates.isEmpty()) {
                result.append("<div style='background-color: #ffe6e6; padding: 15px; border-left: 4px solid #ff3333; margin: 10px 0;'>");
                result.append("<h3 style='color: #ff3333; margin-top: 0;'>⚠️ Обнаружены дубликаты GitHub ID:</h3>");
                for (Map<String, Object> dup : githubDuplicates) {
                    result.append("<p><strong>GitHub ID: ").append(dup.get("github_id")).append("</strong><br>");
                    result.append("Используется ").append(dup.get("count")).append(" раз(а)<br>");
                    result.append("Пользователи: ").append(dup.get("emails")).append("</p>");
                }
                result.append("</div>");
            } else {
                result.append("<div style='background-color: #e6ffe6; padding: 10px; border-left: 4px solid #33cc33;'>");
                result.append("<p>✅ Нет дубликатов GitHub ID</p>");
                result.append("</div>");
            }
            
            // Действия
            result.append("<h2>🚀 Действия:</h2>");
            result.append("<div style='margin: 20px 0;'>");
            result.append("<a href='/oauth-management' style='background-color: #4CAF50; color: white; padding: 10px 15px; text-decoration: none; border-radius: 5px; margin-right: 10px;'>Управление OAuth</a> ");
            result.append("<a href='/' style='background-color: #2196F3; color: white; padding: 10px 15px; text-decoration: none; border-radius: 5px;'>На главную</a>");
            result.append("</div>");
            
        } catch (Exception e) {
            result.append("<div style='background-color: #ffe6e6; padding: 15px; border-left: 4px solid #ff3333;'>");
            result.append("<h3 style='color: #ff3333;'>Ошибка:</h3>");
            result.append("<p>").append(e.getMessage()).append("</p>");
            result.append("</div>");
        }
        
        return result.toString();
    }

    @GetMapping("/api/fix-oauth-duplicates")
    @ResponseBody
    public String fixOAuthDuplicates() {
        StringBuilder result = new StringBuilder();
        result.append("<h1>🔧 Исправление OAuth дубликатов</h1>");
        
        try {
            int totalFixed = 0;
            
            // Исправляем Google дубликаты
            List<Map<String, Object>> googleDuplicates = databaseService.getGoogleDuplicates();
            
            if (!googleDuplicates.isEmpty()) {
                result.append("<h2>🔄 Исправление Google дубликатов:</h2>");
                
                for (Map<String, Object> dup : googleDuplicates) {
                    String googleId = (String) dup.get("google_id");
                    int count = ((Number) dup.get("count")).intValue();
                    String emails = (String) dup.get("emails");
                    
                    result.append("<div style='background-color: #fff3cd; padding: 10px; border-left: 4px solid #ffc107; margin: 10px 0;'>");
                    result.append("<strong>Google ID:</strong> ").append(googleId).append("<br>");
                    result.append("<strong>Найден у пользователей:</strong> ").append(emails).append("<br>");
                    result.append("<strong>Количество дубликатов:</strong> ").append(count).append("<br>");
                    
                    // Получаем всех пользователей с этим Google ID
                    List<Map<String, Object>> usersWithSameGoogle = databaseService.getUsersByGoogleId(googleId);
                    
                    if (usersWithSameGoogle.size() > 1) {
                        result.append("<strong>Действие:</strong> Оставляем у первого пользователя, у остальных очищаем<br>");
                        
                        boolean first = true;
                        for (Map<String, Object> user : usersWithSameGoogle) {
                            Integer userId = (Integer) user.get("id");
                            String userEmail = (String) user.get("email");
                            
                            result.append("- ").append(userEmail);
                            
                            if (!first) {
                                databaseService.clearGoogleId(userId);
                                result.append(" <span style='color: #28a745;'>✓ очищен</span>");
                                totalFixed++;
                            } else {
                                result.append(" <span style='color: #007bff;'>✓ оставлен</span>");
                            }
                            
                            result.append("<br>");
                            first = false;
                        }
                    }
                    
                    result.append("</div>");
                }
            } else {
                result.append("<div style='background-color: #d4edda; padding: 10px; border-left: 4px solid #28a745; margin: 10px 0;'>");
                result.append("✅ Нет Google дубликатов");
                result.append("</div>");
            }
            
            // Исправляем GitHub дубликаты
            List<Map<String, Object>> githubDuplicates = databaseService.getGithubDuplicates();
            
            if (!githubDuplicates.isEmpty()) {
                result.append("<h2>🔄 Исправление GitHub дубликатов:</h2>");
                
                for (Map<String, Object> dup : githubDuplicates) {
                    String githubId = (String) dup.get("github_id");
                    int count = ((Number) dup.get("count")).intValue();
                    String emails = (String) dup.get("emails");
                    
                    result.append("<div style='background-color: #fff3cd; padding: 10px; border-left: 4px solid #ffc107; margin: 10px 0;'>");
                    result.append("<strong>GitHub ID:</strong> ").append(githubId).append("<br>");
                    result.append("<strong>Найден у пользователей:</strong> ").append(emails).append("<br>");
                    result.append("<strong>Количество дубликатов:</strong> ").append(count).append("<br>");
                    
                    // Получаем всех пользователей с этим GitHub ID
                    List<Map<String, Object>> usersWithSameGithub = databaseService.getUsersByGithubId(githubId);
                    
                    if (usersWithSameGithub.size() > 1) {
                        result.append("<strong>Действие:</strong> Оставляем у первого пользователя, у остальных очищаем<br>");
                        
                        boolean first = true;
                        for (Map<String, Object> user : usersWithSameGithub) {
                            Integer userId = (Integer) user.get("id");
                            String userEmail = (String) user.get("email");
                            
                            result.append("- ").append(userEmail);
                            
                            if (!first) {
                                databaseService.clearGithubId(userId);
                                result.append(" <span style='color: #28a745;'>✓ очищен</span>");
                                totalFixed++;
                            } else {
                                result.append(" <span style='color: #007bff;'>✓ оставлен</span>");
                            }
                            
                            result.append("<br>");
                            first = false;
                        }
                    }
                    
                    result.append("</div>");
                }
            } else {
                result.append("<div style='background-color: #d4edda; padding: 10px; border-left: 4px solid #28a745; margin: 10px 0;'>");
                result.append("✅ Нет GitHub дубликатов");
                result.append("</div>");
            }
            
            // Итог
            result.append("<h2>📊 Итоги исправления:</h2>");
            result.append("<div style='background-color: #e9ecef; padding: 15px; border-radius: 5px;'>");
            result.append("<p><strong>Всего исправлено дубликатов:</strong> ").append(totalFixed).append("</p>");
            result.append("<p><strong>Google дубликатов:</strong> ").append(googleDuplicates.size()).append("</p>");
            result.append("<p><strong>GitHub дубликатов:</strong> ").append(githubDuplicates.size()).append("</p>");
            
            if (totalFixed > 0) {
                result.append("<p style='color: #28a745;'>✅ Дубликаты успешно исправлены!</p>");
            } else {
                result.append("<p>✅ Проблем с дубликатами не обнаружено</p>");
            }
            result.append("</div>");
            
            // Ссылки для дальнейших действий
            result.append("<h2>🚀 Дальнейшие действия:</h2>");
            result.append("<div style='margin: 20px 0;'>");
            result.append("<a href='/debug/oauth-links' style='background-color: #17a2b8; color: white; padding: 10px 15px; text-decoration: none; border-radius: 5px; margin-right: 10px;'>📊 Проверить привязки</a> ");
            result.append("<a href='/oauth-management' style='background-color: #28a745; color: white; padding: 10px 15px; text-decoration: none; border-radius: 5px; margin-right: 10px;'>🔐 Управление OAuth</a> ");
            result.append("<a href='/' style='background-color: #007bff; color: white; padding: 10px 15px; text-decoration: none; border-radius: 5px;'>🏠 На главную</a>");
            result.append("</div>");
            
            // Кнопка для повторной проверки
            result.append("<p><a href='/api/fix-oauth-duplicates' style='color: #6c757d;'>🔄 Проверить снова</a></p>");
            
        } catch (Exception e) {
            result.append("<div style='background-color: #f8d7da; padding: 15px; border-left: 4px solid #dc3545;'>");
            result.append("<h3 style='color: #dc3545;'>❌ Ошибка:</h3>");
            result.append("<p>").append(e.getMessage()).append("</p>");
            result.append("</div>");
        }
        
        return result.toString();
    }


    @GetMapping("/schedule")
    public String schedulePage(Model model, Authentication authentication) {
        // Проверка аутентификации
        if (authentication != null && authentication.isAuthenticated()) {
            String username = authentication.getName();
            
            // Если имя пользователя похоже на ID (только цифры), ищем email в базе
            if (username.matches("\\d+")) {
                // Это похоже на Google ID (только цифры) или GitHub ID
                User user = userService.findByGoogleId(username);
                if (user == null) {
                    user = userService.findByGithubId(username);
                }
                
                if (user != null && user.getEmail() != null) {
                    model.addAttribute("username", user.getEmail());
                } else {
                    model.addAttribute("username", username);
                }
            } else {
                model.addAttribute("username", username);
            }
            
            model.addAttribute("isAuthenticated", true);
        } else {
            model.addAttribute("username", "anonymous");
            model.addAttribute("isAuthenticated", false);
        }
        
        // Добавляем данные расписания
        addScheduleDataToModel(model);
        
        return "schedule"; // если создадите отдельную страницу schedule.html
    }

    @GetMapping("/api/schedule/today")
    @ResponseBody
    public List<Map<String, Object>> getTodayScheduleApi() {
        return scheduleService.getTodaysSchedule();
    }

    @GetMapping("/api/schedule/week")
    @ResponseBody
    public Map<String, List<Map<String, Object>>> getWeekScheduleApi() {
        return scheduleService.getGroupedSchedule();
    }
}
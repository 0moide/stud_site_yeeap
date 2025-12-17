package com.example.mywebsite.service;

import com.example.mywebsite.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;

@Service
public class UserService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void init() {
        createUsersTable();
        createDefaultUser();
    }

    private void createUsersTable() {
        try {
            jdbcTemplate.execute("""
                IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='users' AND xtype='U')
                CREATE TABLE users (
                    id INT IDENTITY(1,1) PRIMARY KEY,
                    password NVARCHAR(100) NOT NULL,
                    email NVARCHAR(100) NOT NULL UNIQUE,
                    google_id NVARCHAR(100),
                    github_id NVARCHAR(100),
                    created_date DATETIME DEFAULT GETDATE()
                )
            """);
            
            System.out.println("Таблица users создана или уже существует");
        } catch (Exception e) {
            System.err.println("Ошибка при создании таблицы users: " + e.getMessage());
        }
    }

    private void createDefaultUser() {
        try {
            // Проверяем, есть ли уже пользователи
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email = 'admin@example.com'", Integer.class);
            
            if (count == null || count == 0) {
                // Сохраняем пароль без хэширования для тестирования
                jdbcTemplate.update(
                    "INSERT INTO users (password, email) VALUES (?, ?)",
                    "123", "admin@example.com"
                );
                System.out.println("Создан пользователь по умолчанию: admin@example.com/123 (пароль без хэширования)");
            } else {
                System.out.println("Пользователь admin@example.com уже существует");
                
                // Проверяем какой пароль в базе
                List<Map<String, Object>> users = jdbcTemplate.queryForList(
                    "SELECT password FROM users WHERE email = 'admin@example.com'");
                if (!users.isEmpty()) {
                    Map<String, Object> user = users.get(0);
                    System.out.println("Текущий пароль в базе для admin@example.com: " + user.get("password"));
                }
            }
        } catch (Exception e) {
            System.err.println("Ошибка при создании пользователя по умолчанию: " + e.getMessage());
        }
    }

    public User findByUsername(String username) {
        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(
                "SELECT * FROM users WHERE username = ?", username);
            
            if (!results.isEmpty()) {
                Map<String, Object> row = results.get(0);
                User user = new User();
                user.setPassword((String) row.get("password"));
                user.setEmail((String) row.get("email"));
                return user;
            }
            return null;
        } catch (Exception e) {
            System.err.println("Ошибка при поиске пользователя: " + e.getMessage());
            return null;
        }
    }

    public User findByEmail(String email) {
        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(
                "SELECT * FROM users WHERE email = ?", email);
            
            if (!results.isEmpty()) {
                Map<String, Object> row = results.get(0);
                User user = new User();
                user.setEmail((String) row.get("email"));
                user.setPassword((String) row.get("password"));
                user.setGoogleId((String) row.get("google_id"));
                user.setGithubId((String) row.get("github_id"));
                
                System.out.println("Найден пользователь " + email + 
                                ": google_id=" + user.getGoogleId() + 
                                ", github_id=" + user.getGithubId());
                
                return user;
            }
            return null;
        } catch (Exception e) {
            System.err.println("Ошибка при поиске пользователя: " + e.getMessage());
            return null;
        }
    }

    public boolean registerUser(String password, String email) {
        try {
            jdbcTemplate.update(
                "INSERT INTO users (password, email) VALUES (?, ?)",
                password, email
            );
            return true;
        } catch (Exception e) {
            System.err.println("Ошибка при регистрации пользователя: " + e.getMessage());
            return false;
        }
    }
    
    // Метод для отладки - получить всех пользователей
    public List<Map<String, Object>> getAllUsers() {
        try {
            return jdbcTemplate.queryForList("SELECT email, password FROM users");
        } catch (Exception e) {
            System.err.println("Ошибка при получении пользователей: " + e.getMessage());
            return List.of();
        }
    }

    public void recreateTable() {
        try {
            // Удаляем таблицу если существует
            jdbcTemplate.execute("DROP TABLE IF EXISTS users");
            System.out.println("Таблица users удалена");
            
            // Создаем заново
            createUsersTable();
            createDefaultUser();
            System.out.println("Таблица users пересоздана");
        } catch (Exception e) {
            System.err.println("Ошибка при пересоздании таблицы: " + e.getMessage());
        }
    }

    public User findByGoogleId(String googleId) {
        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(
                "SELECT * FROM users WHERE google_id = ?", googleId);
            
            if (!results.isEmpty()) {
                Map<String, Object> row = results.get(0);
                User user = new User();
                user.setEmail((String) row.get("email"));
                user.setPassword((String) row.get("password"));
                user.setGoogleId((String) row.get("google_id"));
                user.setGithubId((String) row.get("github_id"));
                return user;
            }
            return null;
        } catch (Exception e) {
            System.err.println("Ошибка при поиске пользователя по Google ID: " + e.getMessage());
            return null;
        }
    }

    public User findByGithubId(String githubId) {
        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(
                "SELECT * FROM users WHERE github_id = ?", githubId);
            
            if (!results.isEmpty()) {
                Map<String, Object> row = results.get(0);
                User user = new User();
                user.setEmail((String) row.get("email"));
                user.setPassword((String) row.get("password"));
                user.setGoogleId((String) row.get("google_id"));
                user.setGithubId((String) row.get("github_id"));
                return user;
            }
            return null;
        } catch (Exception e) {
            System.err.println("Ошибка при поиске пользователя по GitHub ID: " + e.getMessage());
            return null;
        }
    }

    // Проверяем, привязан ли Google ID к другому пользователю
    public boolean isGoogleIdAlreadyUsed(String googleId, String currentUserEmail) {
        try {
            if (googleId == null || googleId.isEmpty()) {
                return false;
            }
            
            List<Map<String, Object>> results = jdbcTemplate.queryForList(
                "SELECT email FROM users WHERE google_id = ? AND email != ?", 
                googleId, currentUserEmail);
            
            return !results.isEmpty();
        } catch (Exception e) {
            System.err.println("Ошибка при проверке Google ID: " + e.getMessage());
            return false;
        }
    }

    public boolean isGithubIdAlreadyUsed(String githubId, String currentUserEmail) {
        try {
            if (githubId == null || githubId.isEmpty()) {
                return false;
            }
            
            List<Map<String, Object>> results = jdbcTemplate.queryForList(
                "SELECT email FROM users WHERE github_id = ? AND email != ?", 
                githubId, currentUserEmail);
            
            return !results.isEmpty();
        } catch (Exception e) {
            System.err.println("Ошибка при проверке GitHub ID: " + e.getMessage());
            return false;
        }
    }

    public String updateGoogleId(String email, String googleId) {
        try {
            if (isGoogleIdAlreadyUsed(googleId, email)) {
                return "Google ID уже привязан к другому аккаунту";
            }
            
            System.out.println("Обновление Google ID для " + email + ": " + googleId);
            
            int updated = jdbcTemplate.update(
                "UPDATE users SET google_id = ? WHERE email = ?",
                googleId, email
            );
            
            if (updated > 0) {
                System.out.println("Google ID успешно обновлен для " + email);
                return "SUCCESS";
            } else {
                return "Пользователь не найден";
            }
        } catch (Exception e) {
            System.err.println("Ошибка при обновлении Google ID: " + e.getMessage());
            
            // Пробуем camelCase
            try {
                int updated = jdbcTemplate.update(
                    "UPDATE users SET googleId = ? WHERE email = ?",
                    googleId, email
                );
                
                if (updated > 0) {
                    System.out.println("Google ID успешно обновлен (camelCase)");
                    return "SUCCESS";
                } else {
                    return "Пользователь не найден";
                }
            } catch (Exception e2) {
                return "Ошибка: " + e2.getMessage();
            }
        }
    }

    public String updateGithubId(String email, String githubId) {
        try {
            if (isGithubIdAlreadyUsed(githubId, email)) {
                return "GitHub ID уже привязан к другому аккаунту";
            }
            
            System.out.println("Обновление GitHub ID для " + email + ": " + githubId);
            
            int updated = jdbcTemplate.update(
                "UPDATE users SET github_id = ? WHERE email = ?",
                githubId, email
            );
            
            if (updated > 0) {
                System.out.println("GitHub ID успешно обновлен для " + email);
                return "SUCCESS";
            } else {
                return "Пользователь не найден";
            }
        } catch (Exception e) {
            System.err.println("Ошибка при обновлении GitHub ID: " + e.getMessage());
            
            // Пробуем camelCase
            try {
                int updated = jdbcTemplate.update(
                    "UPDATE users SET githubId = ? WHERE email = ?",
                    githubId, email
                );
                
                if (updated > 0) {
                    System.out.println("GitHub ID успешно обновлен (camelCase)");
                    return "SUCCESS";
                } else {
                    return "Пользователь не найден";
                }
            } catch (Exception e2) {
                return "Ошибка: " + e2.getMessage();
            }
        }
    }

    public void unlinkGoogleId(String email) {
        try {
            System.out.println("Отвязка Google от пользователя: " + email);
            
            int updated = jdbcTemplate.update(
                "UPDATE users SET google_id = NULL WHERE email = ?",
                email
            );
            
            if (updated > 0) {
                System.out.println("Google успешно отвязан от: " + email);
            } else {
                System.out.println("Пользователь не найден: " + email);
            }
        } catch (Exception e) {
            System.err.println("Ошибка при отвязке Google ID: " + e.getMessage());
        }
    }

    public void unlinkGithubId(String email) {
        try {
            System.out.println("Отвязка GitHub от пользователя: " + email);
            
            int updated = jdbcTemplate.update(
                "UPDATE users SET github_id = NULL WHERE email = ?",
                email
            );
            
            if (updated > 0) {
                System.out.println("GitHub успешно отвязан от: " + email);
            } else {
                System.out.println("Пользователь не найден: " + email);
            }
        } catch (Exception e) {
            System.err.println("Ошибка при отвязке GitHub ID: " + e.getMessage());
        }
    }

    public User getUserWithDetails(String email) {
        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(
                "SELECT * FROM users WHERE email = ?", email);
            
            if (!results.isEmpty()) {
                Map<String, Object> row = results.get(0);
                User user = new User();
                user.setEmail((String) row.get("email"));
                user.setPassword((String) row.get("password"));
                user.setGoogleId((String) row.get("google_id"));
                user.setGithubId((String) row.get("github_id"));
                
                System.out.println("=== Детали пользователя " + email + " ===");
                System.out.println("ID в БД: " + row.get("id"));
                System.out.println("Email: " + user.getEmail());
                System.out.println("Google ID: " + user.getGoogleId());
                System.out.println("GitHub ID: " + user.getGithubId());
                System.out.println("Пароль: " + user.getPassword());
                System.out.println("=============================");
                
                return user;
            } else {
                System.out.println("Пользователь не найден: " + email);
                return null;
            }
        } catch (Exception e) {
            System.err.println("Ошибка: " + e.getMessage());
            return null;
        }
    }

    // Метод для получения информации о том, к какому аккаунту привязан OAuth
    public String getAccountLinkedToGoogleId(String googleId) {
        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(
                "SELECT email FROM users WHERE google_id = ? OR googleId = ?", 
                googleId, googleId);
            
            if (!results.isEmpty()) {
                return (String) results.get(0).get("email");
            }
            return null;
        } catch (Exception e) {
            System.err.println("Ошибка: " + e.getMessage());
            return null;
        }
    }

    public String getAccountLinkedToGithubId(String githubId) {
        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(
                "SELECT email FROM users WHERE github_id = ? OR githubId = ?", 
                githubId, githubId);
            
            if (!results.isEmpty()) {
                return (String) results.get(0).get("email");
            }
            return null;
        } catch (Exception e) {
            System.err.println("Ошибка: " + e.getMessage());
            return null;
        }
    }

}
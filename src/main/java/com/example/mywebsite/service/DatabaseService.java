package com.example.mywebsite.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Service
public class DatabaseService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ScheduleService scheduleService;

    // Этот метод выполнится автоматически при запуске приложения
    @PostConstruct
    public void init() {
        initTable();
        initAcademicTables();
        initTestUsersAndStudents();
        initExportTables();
        scheduleService.initScheduleTable();
    }

    // Создаем простую таблицу для сообщений
    public void initTable() {
        try {
            System.out.println("Пытаемся создать таблицу...");
            
            jdbcTemplate.execute("""
                IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='simple_messages' AND xtype='U')
                CREATE TABLE simple_messages (
                    id INT IDENTITY(1,1) PRIMARY KEY,
                    text NVARCHAR(255),
                    created_date DATETIME DEFAULT GETDATE()
                )
            """);
            
            System.out.println("Таблица simple_messages создана или уже существует");
            
        } catch (Exception e) {
            System.err.println("Ошибка при создании таблицы: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Сохраняем сообщение
    public void saveMessage(String text) {
        try {
            jdbcTemplate.update("INSERT INTO simple_messages (text) VALUES (?)", text);
            System.out.println("Сообщение сохранено: " + text);
        } catch (Exception e) {
            System.err.println("Ошибка при сохранении сообщения: " + e.getMessage());
            throw e;
        }
    }

    // Получаем все сообщения
    public List<Map<String, Object>> getAllMessages() {
        try {
            return jdbcTemplate.queryForList("SELECT * FROM simple_messages ORDER BY created_date DESC");
        } catch (Exception e) {
            System.err.println("Ошибка при получении сообщений: " + e.getMessage());
            throw e;
        }
    }

    // Простой тест подключения
    public String testConnection() {
        try {
            String result = jdbcTemplate.queryForObject("SELECT @@VERSION", String.class);
            return "Подключение к SQL Server успешно! Версия: " + result;
        } catch (Exception e) {
            return "Ошибка подключения: " + e.getMessage();
        }
    }
    
    // Проверяем существование таблицы
    public boolean tableExists() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'simple_messages'", 
                Integer.class
            );
            return count != null && count > 0;
        } catch (Exception e) {
            System.err.println("Ошибка при проверке таблицы: " + e.getMessage());
            return false;
        }
    }

    public void initAcademicTables() {
        try {
            // Таблица предметов
            jdbcTemplate.execute("""
                IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='subjects' AND xtype='U')
                CREATE TABLE subjects (
                    id INT IDENTITY(1,1) PRIMARY KEY,
                    name NVARCHAR(100) NOT NULL,
                    description NVARCHAR(255)
                )
            """);
            
            // Таблица студентов
            jdbcTemplate.execute("""
                IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='students' AND xtype='U')
                CREATE TABLE students (
                    id INT IDENTITY(1,1) PRIMARY KEY,
                    user_id INT,
                    full_name NVARCHAR(100) NOT NULL,
                    group_name NVARCHAR(50),
                    FOREIGN KEY (user_id) REFERENCES users(id)
                )
            """);
            
            // Таблица оценок
            jdbcTemplate.execute("""
                IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='grades' AND xtype='U')
                CREATE TABLE grades (
                    id INT IDENTITY(1,1) PRIMARY KEY,
                    student_id INT NOT NULL,
                    subject_id INT NOT NULL,
                    grade INT NOT NULL,
                    exam_date DATE,
                    created_date DATETIME DEFAULT GETDATE(),
                    FOREIGN KEY (student_id) REFERENCES students(id),
                    FOREIGN KEY (subject_id) REFERENCES subjects(id)
                )
            """);
            
            // Добавляем тестовые данные
            addTestAcademicData();
            
            System.out.println("Академические таблицы созданы или уже существуют");
        } catch (Exception e) {
            System.err.println("Ошибка при создании академических таблиц: " + e.getMessage());
        }
    }

    // Метод для добавления тестовых данных
    private void addTestAcademicData() {
        try {
            // Добавляем предметы
            Integer subjectsCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM subjects", Integer.class);
            System.out.println("1");
            if (subjectsCount == null || subjectsCount == 0) {
                jdbcTemplate.update("INSERT INTO subjects (name, description) VALUES (?, ?)", 
                    "Математика", "Основы высшей математики");
                jdbcTemplate.update("INSERT INTO subjects (name, description) VALUES (?, ?)", 
                    "Программирование", "Java и Spring Framework");
                jdbcTemplate.update("INSERT INTO subjects (name, description) VALUES (?, ?)", 
                    "Базы данных", "SQL и проектирование баз данных");
                jdbcTemplate.update("INSERT INTO subjects (name, description) VALUES (?, ?)", 
                    "Веб-разработка", "HTML, CSS, JavaScript");
                    
                System.out.println("Добавлены тестовые предметы");
            }
            System.out.println("2");
            // Связываем пользователя admin со студентом
            Integer studentsCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM students WHERE user_id = 1", Integer.class);
            System.out.println("3");
            if (studentsCount == null || studentsCount == 0) {
                jdbcTemplate.update(
                    "INSERT INTO students (user_id, full_name, group_name) VALUES (?, ?, ?)",
                    1, "Иванов Алексей Сергеевич", "ИТ-21"
                );
                System.out.println("Создан студент для пользователя admin");
            }
            System.out.println("4");
            // Добавляем оценки
            Integer gradesCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM grades", Integer.class);
            System.out.println("5");
            if (gradesCount == null || gradesCount == 0) {
                // Получаем ID студента и предметов
                Integer studentId = null;
                List<Map<String, Object>> studentResults = jdbcTemplate.queryForList("SELECT id FROM students WHERE user_id = 1");
                if (!studentResults.isEmpty()) {
                    studentId = (Integer) studentResults.get(0).get("id");
                    System.out.println("Student ID: " + studentId);
                }

                // Получаем все предметы и сопоставляем по имени
                List<Map<String, Object>> allSubjects = jdbcTemplate.queryForList("SELECT id, name FROM subjects");
                System.out.println("Все предметы для сопоставления:");
                allSubjects.forEach(subject -> {
                    System.out.println(" - ID: " + subject.get("id") + ", Name: '" + subject.get("name") + "'");
                });

                Integer mathId = null, progId = null, dbId = null, webId = null;

                for (Map<String, Object> subject : allSubjects) {
                    String name = (String) subject.get("name");
                    Integer id = (Integer) subject.get("id");
                    
                    if ("Математика".equals(name)) {
                        mathId = id;
                        System.out.println("Найдена Математика с ID: " + mathId);
                    } else if ("Программирование".equals(name)) {
                        progId = id;
                        System.out.println("Найдено Программирование с ID: " + progId);
                    } else if ("Базы данных".equals(name)) {
                        dbId = id;
                        System.out.println("Найдены Базы данных с ID: " + dbId);
                    } else if ("Веб-разработка".equals(name)) {
                        webId = id;
                        System.out.println("Найдена Веб-разработка с ID: " + webId);
                    }
                }

                System.out.println("Итоговые ID:");
                System.out.println(" - Математика: " + mathId);
                System.out.println(" - Программирование: " + progId);
                System.out.println(" - Базы данных: " + dbId);
                System.out.println(" - Веб-разработка: " + webId);            

                    
                if (studentId != null && mathId != null) {
                    jdbcTemplate.update(
                        "INSERT INTO grades (student_id, subject_id, grade, exam_date) VALUES (?, ?, ?, ?)",
                        studentId, mathId, 85, "2024-01-15"
                    );
                    jdbcTemplate.update(
                        "INSERT INTO grades (student_id, subject_id, grade, exam_date) VALUES (?, ?, ?, ?)",
                        studentId, progId, 92, "2024-01-20"
                    );
                    jdbcTemplate.update(
                        "INSERT INTO grades (student_id, subject_id, grade, exam_date) VALUES (?, ?, ?, ?)",
                        studentId, dbId, 88, "2024-01-25"
                    );
                    jdbcTemplate.update(
                        "INSERT INTO grades (student_id, subject_id, grade, exam_date) VALUES (?, ?, ?, ?)",
                        studentId, webId, 95, "2024-02-01"
                    );
                    System.out.println("Добавлены тестовые оценки");
                }
            }
        } catch (Exception e) {
            System.err.println("Ошибка при добавлении тестовых данных: " + e.getMessage());
        }
    }

    public List<Map<String, Object>> getUsersWithOAuthLinks() {
        try {
            return jdbcTemplate.queryForList("""
                SELECT 
                    email,
                    COALESCE(google_id, '') as google_id,
                    COALESCE(github_id, '') as github_id
                FROM users 
                ORDER BY email
            """);
        } catch (Exception e) {
            System.err.println("Ошибка при получении пользователей с OAuth: " + e.getMessage());
            return List.of();
        }
    }

    // Получаем пользователей с привязанным Google
    public List<Map<String, Object>> getUsersWithGoogle() {
        try {
            return jdbcTemplate.queryForList("""
                SELECT email, google_id 
                FROM users 
                WHERE google_id IS NOT NULL AND google_id != ''
                ORDER BY email
            """);
        } catch (Exception e) {
            System.err.println("Ошибка при получении пользователей с Google: " + e.getMessage());
            return List.of();
        }
    }

    // Получаем пользователей с привязанным GitHub
    public List<Map<String, Object>> getUsersWithGithub() {
        try {
            return jdbcTemplate.queryForList("""
                SELECT email, github_id 
                FROM users 
                WHERE github_id IS NOT NULL AND github_id != ''
                ORDER BY email
            """);
        } catch (Exception e) {
            System.err.println("Ошибка при получении пользователей с GitHub: " + e.getMessage());
            return List.of();
        }
    }

    public List<Map<String, Object>> getUsersByGoogleId(String googleId) {
        try {
            return jdbcTemplate.queryForList(
                "SELECT id, email FROM users WHERE google_id = ? ORDER BY id", 
                googleId
            );
        } catch (Exception e) {
            System.err.println("Ошибка при получении пользователей по Google ID: " + e.getMessage());
            return List.of();
        }
    }

    public List<Map<String, Object>> getUsersByGithubId(String githubId) {
        try {
            return jdbcTemplate.queryForList(
                "SELECT id, email FROM users WHERE github_id = ? ORDER BY id", 
                githubId
            );
        } catch (Exception e) {
            System.err.println("Ошибка при получении пользователей по GitHub ID: " + e.getMessage());
            return List.of();
        }
    }

    public void clearGoogleId(Integer userId) {
        try {
            jdbcTemplate.update("UPDATE users SET google_id = NULL WHERE id = ?", userId);
            System.out.println("Очищен Google ID у пользователя с ID: " + userId);
        } catch (Exception e) {
            System.err.println("Ошибка при очистке Google ID: " + e.getMessage());
        }
    }

    public void clearGithubId(Integer userId) {
        try {
            jdbcTemplate.update("UPDATE users SET github_id = NULL WHERE id = ?", userId);
            System.out.println("Очищен GitHub ID у пользователя с ID: " + userId);
        } catch (Exception e) {
            System.err.println("Ошибка при очистке GitHub ID: " + e.getMessage());
        }
    }

    public List<Map<String, Object>> getGoogleDuplicates() {
        try {
            return jdbcTemplate.queryForList("""
                SELECT 
                    COALESCE(google_id, '') as google_id, 
                    COUNT(*) as count,
                    STRING_AGG(email, ', ') as emails
                FROM users 
                WHERE google_id IS NOT NULL AND google_id != ''
                GROUP BY google_id
                HAVING COUNT(*) > 1
                ORDER BY google_id
            """);
        } catch (Exception e) {
            System.err.println("Ошибка при получении Google дубликатов: " + e.getMessage());
            return List.of();
        }
    }

    public List<Map<String, Object>> getGithubDuplicates() {
        try {
            return jdbcTemplate.queryForList("""
                SELECT 
                    COALESCE(github_id, '') as github_id, 
                    COUNT(*) as count,
                    STRING_AGG(email, ', ') as emails
                FROM users 
                WHERE github_id IS NOT NULL AND github_id != ''
                GROUP BY github_id
                HAVING COUNT(*) > 1
                ORDER BY github_id
            """);
        } catch (Exception e) {
            System.err.println("Ошибка при получении GitHub дубликатов: " + e.getMessage());
            return List.of();
        }
    }

    public void initTestUsersAndStudents() {
        try {
            System.out.println("=== Инициализация тестовых пользователей и студентов ===");
            
            // Создаем 10 тестовых пользователей если их нет
            for (int i = 1; i <= 10; i++) {
                String email = String.format("student%d@university.edu", i);
                
                // Проверяем существование пользователя
                Integer userExists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, email);
                
                if (userExists == null || userExists == 0) {
                    // Создаем пользователя
                    jdbcTemplate.update(
                        "INSERT INTO users (password, email) VALUES (?, ?)",
                        "password123", email
                    );
                    System.out.println("Создан пользователь: " + email);
                }
            }
            
            // Создаем студентов для всех пользователей
            initStudentsForAllUsers();
            
            // Создаем уникальные оценки для каждого студента
            initGradesForAllStudents();
            
            System.out.println("=== Завершена инициализация тестовых данных ===");
            
        } catch (Exception e) {
            System.err.println("Ошибка при создании тестовых пользователей: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void initStudentsForAllUsers() {
        try {
            System.out.println("=== Создание студентов для пользователей ===");
            
            // Получаем всех пользователей
            List<Map<String, Object>> users = jdbcTemplate.queryForList(
                "SELECT id, email FROM users ORDER BY id");
            
            // Список возможных ФИО и групп
            String[] firstNames = {"Иван", "Алексей", "Дмитрий", "Сергей", "Андрей", 
                                  "Мария", "Анна", "Екатерина", "Ольга", "Наталья"};
            String[] lastNames = {"Иванов", "Петров", "Сидоров", "Кузнецов", "Смирнов", 
                                 "Попов", "Васильев", "Михайлов", "Фёдоров", "Морозов"};
            String[] middleNames = {"Александрович", "Сергеевич", "Дмитриевич", "Андреевич", 
                                   "Владимирович", "Алексеевич", "Игоревич", "Юрьевич", 
                                   "Олегович", "Николаевич"};
            String[] groups = {"ИТ-21", "ИТ-22", "ИТ-23", "КБ-21", "КБ-22", 
                              "ПИ-21", "ПИ-22", "СА-21", "СА-22", "РП-21"};
            
            Random random = new Random();
            
            for (Map<String, Object> user : users) {
                Integer userId = (Integer) user.get("id");
                String email = (String) user.get("email");
                
                // Проверяем, есть ли уже студент у этого пользователя
                Integer studentExists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM students WHERE user_id = ?", Integer.class, userId);
                
                if (studentExists == null || studentExists == 0) {
                    // Генерируем уникальное ФИО и группу
                    String firstName = firstNames[random.nextInt(firstNames.length)];
                    String lastName = lastNames[random.nextInt(lastNames.length)];
                    String middleName = middleNames[random.nextInt(middleNames.length)];
                    String group = groups[random.nextInt(groups.length)];
                    
                    String fullName = lastName + " " + firstName + " " + middleName;
                    
                    // Создаем студента
                    jdbcTemplate.update(
                        "INSERT INTO students (user_id, full_name, group_name) VALUES (?, ?, ?)",
                        userId, fullName, group
                    );
                    
                    System.out.println("Создан студент: " + fullName + " (" + group + 
                                     ") для пользователя " + email);
                } else {
                    System.out.println("Студент уже существует для пользователя " + email);
                }
            }
            
        } catch (Exception e) {
            System.err.println("Ошибка при создании студентов: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void initGradesForAllStudents() {
        try {
            System.out.println("=== Создание оценок для студентов ===");
            
            // Получаем всех студентов
            List<Map<String, Object>> students = jdbcTemplate.queryForList(
                "SELECT s.id as student_id, s.full_name, u.email " +
                "FROM students s " +
                "JOIN users u ON s.user_id = u.id " +
                "ORDER BY s.id");
            
            // Получаем все предметы
            List<Map<String, Object>> subjects = jdbcTemplate.queryForList(
                "SELECT id, name FROM subjects ORDER BY id");
            
            if (subjects.isEmpty()) {
                System.out.println("Нет предметов для создания оценок!");
                return;
            }
            
            Random random = new Random();
            
            for (Map<String, Object> student : students) {
                Integer studentId = (Integer) student.get("student_id");
                String studentName = (String) student.get("full_name");
                
                // Проверяем, есть ли уже оценки у этого студента
                Integer gradesExist = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM grades WHERE student_id = ?", Integer.class, studentId);
                
                if (gradesExist == null || gradesExist == 0) {
                    // Создаем оценки по каждому предмету
                    for (Map<String, Object> subject : subjects) {
                        Integer subjectId = (Integer) subject.get("id");
                        String subjectName = (String) subject.get("name");
                        
                        // Генерируем случайную оценку (60-100)
                        int grade = 60 + random.nextInt(41);
                        
                        // Генерируем случайную дату в пределах учебного года
                        int month = 9 + random.nextInt(4); // сентябрь-декабрь
                        int day = 1 + random.nextInt(28);
                        int year = 2024;
                        String examDate = String.format("%d-%02d-%02d", year, month, day);
                        
                        // Создаем оценку
                        jdbcTemplate.update(
                            "INSERT INTO grades (student_id, subject_id, grade, exam_date) " +
                            "VALUES (?, ?, ?, ?)",
                            studentId, subjectId, grade, examDate
                        );
                        
                        System.out.println("Создана оценка: " + studentName + 
                                         " - " + subjectName + ": " + grade);
                    }
                } else {
                    System.out.println("Оценки уже существуют для студента " + studentName);
                }
            }
            
        } catch (Exception e) {
            System.err.println("Ошибка при создании оценок: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Новый метод для получения студента по email пользователя
    public Integer getStudentIdByUserEmail(String email) {
        try {
            String sql = """
                SELECT s.id 
                FROM students s 
                JOIN users u ON s.user_id = u.id 
                WHERE u.email = ?
            """;
            
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, email);
            if (!results.isEmpty()) {
                return (Integer) results.get(0).get("id");
            }
            return null;
        } catch (Exception e) {
            System.err.println("Ошибка при получении student_id: " + e.getMessage());
            return null;
        }
    }

    // Метод для получения userId по email
    public Integer getUserIdByEmail(String email) {
        try {
            String sql = "SELECT id FROM users WHERE email = ?";
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, email);
            if (!results.isEmpty()) {
                return (Integer) results.get(0).get("id");
            }
            return null;
        } catch (Exception e) {
            System.err.println("Ошибка при получении user_id: " + e.getMessage());
            return null;
        }
    }

    // Метод для получения информации о студенте по email пользователя
    public Map<String, Object> getStudentInfoByUserEmail(String email) {
        try {
            String sql = """
                SELECT s.full_name, s.group_name, u.email
                FROM students s 
                JOIN users u ON s.user_id = u.id 
                WHERE u.email = ?
            """;
            
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, email);
            if (!results.isEmpty()) {
                Map<String, Object> studentInfo = results.get(0);
                
                // Добавляем информацию о количестве оценок
                Integer studentId = getStudentIdByUserEmail(email);
                if (studentId != null) {
                    Integer gradesCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM grades WHERE student_id = ?", 
                        Integer.class, studentId);
                    studentInfo.put("grades_count", gradesCount != null ? gradesCount : 0);
                }
                
                return studentInfo;
            }
            return null;
        } catch (Exception e) {
            System.err.println("Ошибка при получении информации о студенте: " + e.getMessage());
            return null;
        }
    }

    // Метод для получения всех пользователей и их студентов
    public List<Map<String, Object>> getAllUsersWithStudents() {
        try {
            String sql = """
                SELECT 
                    u.id as user_id,
                    u.email,
                    u.created_date as user_created,
                    COALESCE(s.full_name, 'Не назначен') as student_name,
                    COALESCE(s.group_name, 'Не назначена') as student_group,
                    COALESCE(s.id, 0) as student_id,
                    (SELECT COUNT(*) FROM grades g WHERE g.student_id = s.id) as grades_count
                FROM users u
                LEFT JOIN students s ON u.id = s.user_id
                ORDER BY u.id
            """;
            
            return jdbcTemplate.queryForList(sql);
        } catch (Exception e) {
            System.err.println("Ошибка при получении пользователей со студентами: " + e.getMessage());
            return List.of();
        }
    }

    // Метод для отладки: показывает связь пользователей и студентов
    public void debugUserStudentRelations() {
        try {
            System.out.println("=== DEBUG: Пользователи и студенты ===");
            
            List<Map<String, Object>> relations = getAllUsersWithStudents();
            
            System.out.println("Всего пользователей: " + relations.size());
            System.out.println("--------------------------------------------------");
            System.out.printf("%-30s | %-25s | %-10s | %s%n", 
                "Email", "ФИО студента", "Группа", "Оценок");
            System.out.println("--------------------------------------------------");
            
            for (Map<String, Object> relation : relations) {
                String email = (String) relation.get("email");
                String studentName = (String) relation.get("student_name");
                String group = (String) relation.get("student_group");
                Integer gradesCount = (Integer) relation.get("grades_count");
                
                System.out.printf("%-30s | %-25s | %-10s | %d%n", 
                    email, studentName, group, gradesCount != null ? gradesCount : 0);
            }
            
            System.out.println("--------------------------------------------------");
            
        } catch (Exception e) {
            System.err.println("Ошибка при отладке связей: " + e.getMessage());
        }
    }

    // Метод для принудительного пересоздания тестовых данных
    public void recreateTestData() {
        try {
            System.out.println("=== Пересоздание тестовых данных ===");
            
            // Удаляем старые данные
            jdbcTemplate.execute("DELETE FROM grades");
            jdbcTemplate.execute("DELETE FROM students");
            jdbcTemplate.execute("DELETE FROM users WHERE email != 'admin@example.com'");
            
            System.out.println("Старые тестовые данные удалены");
            
            // Создаем заново
            initTestUsersAndStudents();
            
            System.out.println("Новые тестовые данные созданы");
            
            // Показываем результат
            debugUserStudentRelations();
            
        } catch (Exception e) {
            System.err.println("Ошибка при пересоздании тестовых данных: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void initExportTables() {
        try {
            // Таблица для хранения Google OAuth2 токенов
            jdbcTemplate.execute("""
                IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='user_google_tokens' AND xtype='U')
                CREATE TABLE user_google_tokens (
                    id INT IDENTITY(1,1) PRIMARY KEY,
                    user_id INT NOT NULL,
                    access_token NVARCHAR(2000),
                    refresh_token NVARCHAR(1000),
                    expires_at DATETIME,
                    scope NVARCHAR(500),
                    created_date DATETIME DEFAULT GETDATE(),
                    updated_date DATETIME DEFAULT GETDATE(),
                    FOREIGN KEY (user_id) REFERENCES users(id),
                    UNIQUE (user_id)
                )
            """);
            
            // Таблица истории выгрузок
            jdbcTemplate.execute("""
                IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='export_history' AND xtype='U')
                CREATE TABLE export_history (
                    id INT IDENTITY(1,1) PRIMARY KEY,
                    user_id INT NOT NULL,
                    file_id NVARCHAR(255),
                    file_name NVARCHAR(500),
                    file_url NVARCHAR(1000),
                    export_date DATETIME DEFAULT GETDATE(),
                    status NVARCHAR(50),
                    error_message NVARCHAR(2000),
                    FOREIGN KEY (user_id) REFERENCES users(id)
                )
            """);
            
            System.out.println("Таблицы для экспорта созданы или уже существуют");
        } catch (Exception e) {
            System.err.println("Ошибка при создании таблиц для экспорта: " + e.getMessage());
        }
    }

    // Методы для работы с Google токенами
    public void saveGoogleToken(Integer userId, String accessToken, String refreshToken, 
                          LocalDateTime expiresAt, String scope) {
        try {
            // Преобразуем LocalDateTime в Timestamp для SQL Server
            java.sql.Timestamp expiresAtSql = expiresAt != null ? 
                java.sql.Timestamp.valueOf(expiresAt) : null;
            
            // Проверяем, существует ли уже запись
            Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_google_tokens WHERE user_id = ?", 
                Integer.class, userId);
            
            if (exists != null && exists > 0) {
                // Обновляем существующую запись
                jdbcTemplate.update("""
                    UPDATE user_google_tokens 
                    SET access_token = ?, refresh_token = ?, expires_at = ?, 
                        scope = ?, updated_date = GETDATE()
                    WHERE user_id = ?
                    """, 
                    accessToken, refreshToken, expiresAtSql, scope, userId);
            } else {
                // Создаем новую запись
                jdbcTemplate.update("""
                    INSERT INTO user_google_tokens 
                    (user_id, access_token, refresh_token, expires_at, scope)
                    VALUES (?, ?, ?, ?, ?)
                    """, 
                    userId, accessToken, refreshToken, expiresAtSql, scope);
            }
            System.out.println("Google токен сохранен для user_id: " + userId);
        } catch (Exception e) {
            System.err.println("Ошибка при сохранении Google токена: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public Map<String, Object> getGoogleToken(Integer userId) {
        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList("""
                SELECT access_token, refresh_token, expires_at, scope
                FROM user_google_tokens 
                WHERE user_id = ?
                """, userId);
            
            if (!results.isEmpty()) {
                Map<String, Object> tokenData = results.get(0);
                
                // Конвертируем Timestamp обратно в LocalDateTime
                java.sql.Timestamp expiresAtSql = (java.sql.Timestamp) tokenData.get("expires_at");
                if (expiresAtSql != null) {
                    tokenData.put("expires_at", expiresAtSql.toLocalDateTime());
                }
                
                return tokenData;
            }
            return null;
        } catch (Exception e) {
            System.err.println("Ошибка при получении Google токена: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public void deleteGoogleToken(Integer userId) {
        try {
            jdbcTemplate.update("DELETE FROM user_google_tokens WHERE user_id = ?", userId);
            System.out.println("Google токен удален для user_id: " + userId);
        } catch (Exception e) {
            System.err.println("Ошибка при удалении Google токена: " + e.getMessage());
        }
    }

    public void saveExportHistory(Integer userId, String fileId, String fileName, 
                                String fileUrl, String status, String errorMessage) {
        try {
            jdbcTemplate.update("""
                INSERT INTO export_history 
                (user_id, file_id, file_name, file_url, status, error_message)
                VALUES (?, ?, ?, ?, ?, ?)
                """, 
                userId, fileId, fileName, fileUrl, status, errorMessage);
            System.out.println("История экспорта сохранена для user_id: " + userId);
        } catch (Exception e) {
            System.err.println("Ошибка при сохранении истории экспорта: " + e.getMessage());
        }
    }

    public List<Map<String, Object>> getExportHistory(Integer userId) {
        try {
            return jdbcTemplate.queryForList("""
                SELECT file_name, file_url, export_date, status
                FROM export_history 
                WHERE user_id = ?
                ORDER BY export_date DESC
                """, userId);
        } catch (Exception e) {
            System.err.println("Ошибка при получении истории экспорта: " + e.getMessage());
            return List.of();
        }
    }

    // Проверяем, есть ли у пользователя токен Google Drive
    public boolean hasGoogleDriveToken(Integer userId) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_google_tokens WHERE user_id = ? AND access_token IS NOT NULL", 
                Integer.class, userId);
            return count != null && count > 0;
        } catch (Exception e) {
            System.err.println("Ошибка при проверке токена Google Drive: " + e.getMessage());
            return false;
        }
    }

    // Получаем токен для обновления
    public String getRefreshToken(Integer userId) {
        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(
                "SELECT refresh_token FROM user_google_tokens WHERE user_id = ?", 
                userId);
            
            if (!results.isEmpty()) {
                String refreshToken = (String) results.get(0).get("refresh_token");
                return refreshToken != null ? refreshToken : "";
            }
            return "";
        } catch (Exception e) {
            System.err.println("Ошибка при получении refresh token: " + e.getMessage());
            return "";
        }
    }

    // Обновляем access token
    public void updateAccessToken(Integer userId, String accessToken, LocalDateTime expiresAt) {
        try {
            // Преобразуем LocalDateTime в Timestamp
            java.sql.Timestamp expiresAtSql = expiresAt != null ? 
                java.sql.Timestamp.valueOf(expiresAt) : null;
            
            jdbcTemplate.update("""
                UPDATE user_google_tokens 
                SET access_token = ?, expires_at = ?, updated_date = GETDATE()
                WHERE user_id = ?
                """, 
                accessToken, expiresAtSql, userId);
            System.out.println("Access token обновлен для user_id: " + userId);
        } catch (Exception e) {
            System.err.println("Ошибка при обновлении access token: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
}
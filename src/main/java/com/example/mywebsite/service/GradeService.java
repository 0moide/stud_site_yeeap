package com.example.mywebsite.service;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

@Service
public class GradeService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DatabaseService databaseService;

    // Получаем оценки студента по email пользователя
    @Cacheable(value = "grades", key = "#userEmail")
    public List<Map<String, Object>> getStudentGrades(String userEmail) {
        try {
            System.out.println("Получение оценок для пользователя: " + userEmail);
            
            // Получаем student_id по email пользователя
            Integer studentId = databaseService.getStudentIdByUserEmail(userEmail);
            
            if (studentId == null) {
                System.out.println("Студент не найден для пользователя: " + userEmail);
                return List.of();
            }
            
            System.out.println("Найден student_id: " + studentId + " для пользователя: " + userEmail);
            
            String sql = """
                SELECT 
                    s.name as subject_name,
                    s.description,
                    g.grade,
                    g.exam_date,
                    st.full_name,
                    st.group_name
                FROM grades g
                JOIN students st ON g.student_id = st.id
                JOIN subjects s ON g.subject_id = s.id
                WHERE st.id = ?
                ORDER BY g.exam_date DESC
            """;
            
            List<Map<String, Object>> grades = jdbcTemplate.queryForList(sql, studentId);
            System.out.println("Найдено оценок: " + grades.size() + " для студента ID: " + studentId);
            
            return grades;
        } catch (Exception e) {
            System.err.println("Ошибка при получении оценок для пользователя " + userEmail + ": " + e.getMessage());
            e.printStackTrace();
            return List.of();
        }
    }

    // Получаем средний балл по email пользователя
    @Cacheable(value = "average", key = "#userEmail")
    public Double getAverageGrade(String userEmail) {
        try {
            Integer studentId = databaseService.getStudentIdByUserEmail(userEmail);
            if (studentId == null) return 0.0;
            
            String sql = """
                SELECT AVG(g.grade) as average_grade
                FROM grades g
                WHERE g.student_id = ?
            """;
            
            return jdbcTemplate.queryForObject(sql, Double.class, studentId);
        } catch (Exception e) {
            System.err.println("Ошибка при расчете среднего балла: " + e.getMessage());
            return 0.0;
        }
    }

    // Получаем информацию о студенте по email пользователя
    @Cacheable(value = "student", key = "#userEmail")
    public Map<String, Object> getStudentInfo(String userEmail) {
        return databaseService.getStudentInfoByUserEmail(userEmail);
    }

    // Новый метод: получаем оценки по userId
    @Cacheable(value = "grades", key = "'userid:' + #userId")
    public List<Map<String, Object>> getStudentGrades(Integer userId) {
        try {
            // Получаем email пользователя по ID
            List<Map<String, Object>> userResults = jdbcTemplate.queryForList(
                "SELECT email FROM users WHERE id = ?", userId);
            
            if (userResults.isEmpty()) {
                return List.of();
            }
            
            String email = (String) userResults.get(0).get("email");
            return getStudentGrades(email);
            
        } catch (Exception e) {
            System.err.println("Ошибка при получении оценок по userId: " + e.getMessage());
            return List.of();
        }
    }

    // Новый метод: получаем средний балл по userId
    @Cacheable(value = "average", key = "'userid:' + #userId")
    public Double getAverageGrade(Integer userId) {
        try {
            List<Map<String, Object>> userResults = jdbcTemplate.queryForList(
                "SELECT email FROM users WHERE id = ?", userId);
            
            if (userResults.isEmpty()) {
                return 0.0;
            }
            
            String email = (String) userResults.get(0).get("email");
            return getAverageGrade(email);
            
        } catch (Exception e) {
            System.err.println("Ошибка при расчете среднего балла по userId: " + e.getMessage());
            return 0.0;
        }
    }

    // Новый метод: получаем информацию о студенте по userId
    @Cacheable(value = "student", key = "'userid:' + #userId")
    public Map<String, Object> getStudentInfo(Integer userId) {
        try {
            List<Map<String, Object>> userResults = jdbcTemplate.queryForList(
                "SELECT email FROM users WHERE id = ?", userId);
            
            if (userResults.isEmpty()) {
                return null;
            }
            
            String email = (String) userResults.get(0).get("email");
            return getStudentInfo(email);
            
        } catch (Exception e) {
            System.err.println("Ошибка при получении информации о студенте по userId: " + e.getMessage());
            return null;
        }
    }

    @CacheEvict(value = {"grades", "average"}, key = "#userEmail")
    public void clearStudentCache(String userEmail) {
        System.out.println("CACHE EVICT: Clearing cache for " + userEmail);
        // Просто очищаем кэш, тело метода может быть пустым
    }

    @CacheEvict(value = {"grades", "average", "student"}, key = "'userid:' + #userId")
    public void clearStudentCache(Integer userId) {
        System.out.println("CACHE EVICT: Clearing cache for user ID " + userId);
    }

    @CacheEvict(value = {"grades", "average", "student", "exports"}, allEntries = true)
    public void clearAllCaches() {
        System.out.println("CACHE EVICT: Clearing ALL caches");
    }


    public void debugAcademicData() {
        try {
            System.out.println("=== DEBUG ACADEMIC DATA ===");
            
            // Проверяем предметы
            List<Map<String, Object>> subjects = jdbcTemplate.queryForList("SELECT * FROM subjects");
            System.out.println("Предметы в базе: " + subjects.size());
            subjects.forEach(subject -> System.out.println(" - " + subject.get("name")));
            
            // Проверяем студентов
            List<Map<String, Object>> students = jdbcTemplate.queryForList("SELECT * FROM students");
            System.out.println("Студенты в базе: " + students.size());
            students.forEach(student -> System.out.println(" - " + student.get("full_name") + 
                " (user_id: " + student.get("user_id") + ")"));
            
            // Проверяем оценки
            List<Map<String, Object>> grades = jdbcTemplate.queryForList("SELECT * FROM grades");
            System.out.println("Оценок в базе: " + grades.size());
            
            databaseService.debugUserStudentRelations();
            
            System.out.println("=== END DEBUG ===");
        } catch (Exception e) {
            System.err.println("Ошибка при отладке данных: " + e.getMessage());
        }
    }

    // Метод для создания тестовых данных с учетом нового формата
    public void createTestData() {
        databaseService.recreateTestData();
    }
}
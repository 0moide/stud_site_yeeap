package com.example.mywebsite.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class ScheduleService {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    // Инициализация таблицы расписания
    public void initScheduleTable() {
        try {
            jdbcTemplate.execute("""
                IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='schedule' AND xtype='U')
                CREATE TABLE schedule (
                    id INT IDENTITY(1,1) PRIMARY KEY,
                    day_of_week NVARCHAR(20) NOT NULL,
                    time_slot NVARCHAR(20) NOT NULL,
                    subject_name NVARCHAR(100) NOT NULL,
                    room NVARCHAR(20),
                    teacher NVARCHAR(100),
                    group_name NVARCHAR(50),
                    created_date DATETIME DEFAULT GETDATE()
                )
            """);
            
            // Добавляем тестовые данные если таблица пуста
            if (isTableEmpty()) {
                addSampleSchedule();
            }
            
        } catch (Exception e) {
            System.err.println("Ошибка при инициализации таблицы расписания: " + e.getMessage());
        }
    }
    
    private boolean isTableEmpty() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM schedule", Integer.class);
            return count == null || count == 0;
        } catch (Exception e) {
            return true;
        }
    }
    
    private void addSampleSchedule() {
        try {
            String[][] schedule = {
                {"Понедельник", "9:00-10:30", "Математика", "А-101", "Иванова А.П.", "ИТ-21"},
                {"Понедельник", "10:45-12:15", "Программирование", "Б-205", "Петров С.М.", "ИТ-21"},
                {"Понедельник", "13:00-14:30", "Базы данных", "В-302", "Сидорова Е.В.", "ИТ-21"},
                {"Вторник", "9:00-10:30", "Веб-разработка", "Г-104", "Кузнецов Д.А.", "ИТ-21"},
                {"Вторник", "10:45-12:15", "Английский язык", "Л-201", "Смирнова О.И.", "ИТ-21"},
                {"Среда", "9:00-10:30", "Программирование", "Б-205", "Петров С.М.", "ИТ-21"},
                {"Среда", "10:45-12:15", "Физкультура", "Спортзал", "Николаев В.С.", "ИТ-21"},
                {"Четверг", "9:00-10:30", "Математика", "А-101", "Иванова А.П.", "ИТ-21"},
                {"Четверг", "10:45-12:15", "Базы данных", "В-302", "Сидорова Е.В.", "ИТ-21"},
                {"Пятница", "9:00-10:30", "Веб-разработка", "Г-104", "Кузнецов Д.А.", "ИТ-21"},
                {"Пятница", "10:45-12:15", "Проектная работа", "Б-210", "Петров С.М.", "ИТ-21"}
            };
            
            for (String[] row : schedule) {
                jdbcTemplate.update(
                    "INSERT INTO schedule (day_of_week, time_slot, subject_name, room, teacher, group_name) VALUES (?, ?, ?, ?, ?, ?)",
                    row[0], row[1], row[2], row[3], row[4], row[5]
                );
            }
            
            System.out.println("Добавлено тестовое расписание");
            
        } catch (Exception e) {
            System.err.println("Ошибка при добавлении тестового расписания: " + e.getMessage());
        }
    }
    
    // Получаем расписание на неделю
    public List<Map<String, Object>> getWeeklySchedule() {
        try {
            return jdbcTemplate.queryForList("""
                SELECT * FROM schedule 
                ORDER BY 
                    CASE day_of_week
                        WHEN 'Понедельник' THEN 1
                        WHEN 'Вторник' THEN 2
                        WHEN 'Среда' THEN 3
                        WHEN 'Четверг' THEN 4
                        WHEN 'Пятница' THEN 5
                        WHEN 'Суббота' THEN 6
                        WHEN 'Воскресенье' THEN 7
                        ELSE 8
                    END,
                    time_slot
            """);
        } catch (Exception e) {
            System.err.println("Ошибка при получении недельного расписания: " + e.getMessage());
            return Collections.emptyList();
        }
    }
    
    // Получаем расписание сгруппированное по дням
    public Map<String, List<Map<String, Object>>> getGroupedSchedule() {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        List<Map<String, Object>> schedule = getWeeklySchedule();
        
        // Порядок дней недели
        String[] daysOrder = {"Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье"};
        
        // Инициализация
        for (String day : daysOrder) {
            result.put(day, new ArrayList<>());
        }
        
        // Группировка
        for (Map<String, Object> lesson : schedule) {
            String day = (String) lesson.get("day_of_week");
            if (result.containsKey(day)) {
                result.get(day).add(lesson);
            }
        }
        
        // Удаляем пустые дни
        result.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        
        return result;
    }
    
    // Получаем расписание на сегодня
    public List<Map<String, Object>> getTodaysSchedule() {
        try {
            String today = getTodayInRussian();
            return jdbcTemplate.queryForList(
                "SELECT * FROM schedule WHERE day_of_week = ? ORDER BY time_slot",
                today
            );
        } catch (Exception e) {
            System.err.println("Ошибка при получении расписания на сегодня: " + e.getMessage());
            return Collections.emptyList();
        }
    }
    
    // Получаем все группы
    public List<String> getAllGroups() {
        try {
            return jdbcTemplate.queryForList(
                "SELECT DISTINCT group_name FROM schedule ORDER BY group_name",
                String.class
            );
        } catch (Exception e) {
            System.err.println("Ошибка при получении списка групп: " + e.getMessage());
            return Arrays.asList("ИТ-21", "ИТ-22", "ИТ-23");
        }
    }
    
    // Получаем статистику расписания
    public Map<String, Object> getScheduleStats() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            Integer totalLessons = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM schedule", Integer.class);
            Integer totalDays = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT day_of_week) FROM schedule", Integer.class);
            Integer totalSubjects = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT subject_name) FROM schedule", Integer.class);
            Integer totalTeachers = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT teacher) FROM schedule", Integer.class);
            
            stats.put("totalLessons", totalLessons != null ? totalLessons : 0);
            stats.put("totalDays", totalDays != null ? totalDays : 0);
            stats.put("totalSubjects", totalSubjects != null ? totalSubjects : 0);
            stats.put("totalTeachers", totalTeachers != null ? totalTeachers : 0);
            
        } catch (Exception e) {
            System.err.println("Ошибка при получении статистики: " + e.getMessage());
            stats.put("totalLessons", 11);
            stats.put("totalDays", 5);
            stats.put("totalSubjects", 6);
            stats.put("totalTeachers", 5);
        }
        
        return stats;
    }
    
    // Получаем информацию о текущей неделе
    public Map<String, Object> getWeekInfo() {
        Map<String, Object> info = new HashMap<>();
        
        java.time.LocalDate now = java.time.LocalDate.now();
        info.put("weekNumber", now.get(java.time.temporal.WeekFields.of(Locale.getDefault()).weekOfWeekBasedYear()));
        info.put("currentDay", getTodayInRussian());
        info.put("nextHoliday", "Новогодние каникулы");
        
        return info;
    }
    
    // Вспомогательный метод для получения сегодняшнего дня на русском
    private String getTodayInRussian() {
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.DayOfWeek dayOfWeek = today.getDayOfWeek();
        
        Map<java.time.DayOfWeek, String> daysMap = Map.of(
            java.time.DayOfWeek.MONDAY, "Понедельник",
            java.time.DayOfWeek.TUESDAY, "Вторник",
            java.time.DayOfWeek.WEDNESDAY, "Среда",
            java.time.DayOfWeek.THURSDAY, "Четверг",
            java.time.DayOfWeek.FRIDAY, "Пятница",
            java.time.DayOfWeek.SATURDAY, "Суббота",
            java.time.DayOfWeek.SUNDAY, "Воскресенье"
        );
        
        return daysMap.getOrDefault(dayOfWeek, "Понедельник");
    }
}
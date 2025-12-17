package com.example.mywebsite.controller;

import com.example.mywebsite.service.GradeService;
import com.example.mywebsite.service.DatabaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Controller
public class GradesController {

    @Autowired
    private GradeService gradeService;

    @Autowired
    private DatabaseService databaseService;

    @GetMapping("/grades")
    public String grades(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName(); // Это email пользователя
        
        System.out.println("Запрос оценок для пользователя: " + username);
        long startTime = System.currentTimeMillis();
        
        // Получаем данные по email пользователя
        List<Map<String, Object>> grades = gradeService.getStudentGrades(username);
        Double averageGrade = gradeService.getAverageGrade(username);
        Map<String, Object> studentInfo = gradeService.getStudentInfo(username);
        
        long endTime = System.currentTimeMillis();
        System.out.println("Время выполнения: " + (endTime - startTime) + "мс");
        System.out.println("=== Конец запроса ===");

        // Подготавливаем данные для диаграммы
        Map<String, Object> chartData = prepareChartData(grades);
        
        model.addAttribute("username", username);
        model.addAttribute("grades", grades);
        model.addAttribute("averageGrade", averageGrade != null ? 
            String.format("%.2f", averageGrade) : "Нет данных");
        model.addAttribute("studentInfo", studentInfo);
        model.addAttribute("gradesCount", grades.size());
        model.addAttribute("chartData", chartData);
        
        return "grades";
    }

    private Map<String, Object> prepareChartData(List<Map<String, Object>> grades) {
        Map<String, Object> chartData = new HashMap<>();
        
        int excellent = 0;
        int good = 0;
        int satisfactory = 0;
        int unsatisfactory = 0;
        
        for (Map<String, Object> grade : grades) {
            Integer gradeValue = (Integer) grade.get("grade");
            if (gradeValue != null) {
                if (gradeValue >= 90) {
                    excellent++;
                } else if (gradeValue >= 75) {
                    good++;
                } else if (gradeValue >= 60) {
                    satisfactory++;
                } else {
                    unsatisfactory++;
                }
            }
        }
        
        chartData.put("excellent", excellent);
        chartData.put("good", good);
        chartData.put("satisfactory", satisfactory);
        chartData.put("unsatisfactory", unsatisfactory);
        
        int total = excellent + good + satisfactory + unsatisfactory;
        if (total > 0) {
            chartData.put("percentageExcellent", String.format("%.1f", (excellent * 100.0 / total)));
            chartData.put("percentageGood", String.format("%.1f", (good * 100.0 / total)));
            chartData.put("percentageSatisfactory", String.format("%.1f", (satisfactory * 100.0 / total)));
            chartData.put("percentageUnsatisfactory", String.format("%.1f", (unsatisfactory * 100.0 / total)));
        } else {
            chartData.put("percentageExcellent", "0.0");
            chartData.put("percentageGood", "0.0");
            chartData.put("percentageSatisfactory", "0.0");
            chartData.put("percentageUnsatisfactory", "0.0");
        }
        
        return chartData;
    }

    // Страница для просмотра всех пользователей и студентов
    @GetMapping("/admin/users-students")
    public String usersStudents(Model model) {
        List<Map<String, Object>> usersWithStudents = databaseService.getAllUsersWithStudents();
        model.addAttribute("users", usersWithStudents);
        return "users-students";
    }

    // API для отладки
    @GetMapping("/debug/users-students")
    @ResponseBody
    public String debugUsersStudents() {
        databaseService.debugUserStudentRelations();
        
        StringBuilder result = new StringBuilder();
        result.append("<h1>Отладка: Пользователи и студенты</h1>");
        
        List<Map<String, Object>> users = databaseService.getAllUsersWithStudents();
        
        result.append("<table border='1' cellpadding='10'>");
        result.append("<tr><th>Email</th><th>ФИО студента</th><th>Группа</th><th>Оценок</th></tr>");
        
        for (Map<String, Object> user : users) {
            result.append("<tr>");
            result.append("<td>").append(user.get("email")).append("</td>");
            result.append("<td>").append(user.get("student_name")).append("</td>");
            result.append("<td>").append(user.get("student_group")).append("</td>");
            result.append("<td>").append(user.get("grades_count")).append("</td>");
            result.append("</tr>");
        }
        
        result.append("</table>");
        result.append("<br><a href='/api/recreate-test-data'>Пересоздать тестовые данные</a><br>");
        result.append("<a href='/grades'>Вернуться к оценкам</a>");
        
        return result.toString();
    }

    // API для пересоздания тестовых данных
    @PostMapping("/api/recreate-test-data")
    @ResponseBody
    public String recreateTestData() {
        databaseService.recreateTestData();
        return "Тестовые данные пересозданы! <a href='/grades'>Посмотреть оценки</a>";
    }

    // API для проверки текущего пользователя
    @GetMapping("/api/my-student-info")
    @ResponseBody
    public String myStudentInfo() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        
        Integer userId = databaseService.getUserIdByEmail(email);
        Integer studentId = databaseService.getStudentIdByUserEmail(email);
        Map<String, Object> studentInfo = gradeService.getStudentInfo(email);
        List<Map<String, Object>> grades = gradeService.getStudentGrades(email);
        Double average = gradeService.getAverageGrade(email);
        
        StringBuilder result = new StringBuilder();
        result.append("<h1>Информация для пользователя: ").append(email).append("</h1>");
        result.append("<p>User ID: ").append(userId).append("</p>");
        result.append("<p>Student ID: ").append(studentId).append("</p>");
        result.append("<p>Student Info: ").append(studentInfo).append("</p>");
        result.append("<p>Количество оценок: ").append(grades.size()).append("</p>");
        result.append("<p>Средний балл: ").append(average).append("</p>");
        result.append("<br><a href='/grades'>Вернуться к оценкам</a>");
        
        return result.toString();
    }
}
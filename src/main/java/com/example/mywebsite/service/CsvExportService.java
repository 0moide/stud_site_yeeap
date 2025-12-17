package com.example.mywebsite.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class CsvExportService {

    @Autowired
    private GradeService gradeService;
    
    public byte[] createCsvReport(String email) {
        try {
            StringBuilder csv = new StringBuilder();
            
            // 1. Простые данные студента
            Map<String, Object> studentInfo = gradeService.getStudentInfo(email);
            if (studentInfo != null) {
                csv.append("Full Name: ").append(studentInfo.get("full_name")).append("\n");
                csv.append("Group: ").append(studentInfo.get("group_name")).append("\n");
                csv.append("Email: ").append(email).append("\n");
            }
            
            csv.append("\n");
            
            // 2. Средний балл
            Double averageGrade = gradeService.getAverageGrade(email);
            if (averageGrade != null) {
                csv.append("Average: ").append(String.format("%.2f", averageGrade)).append("\n");
            }
            
            csv.append("\n");
            
            // 3. Заголовок таблицы
            csv.append("Subject,Grade,Date\n");
            
            // 4. Оценки
            List<Map<String, Object>> grades = gradeService.getStudentGrades(email);
            if (grades != null) {
                for (Map<String, Object> grade : grades) {
                    // Берем значения или пустые строки
                    Object subjectObj = grade.get("subject_name");
                    Object gradeObj = grade.get("grade");
                    Object dateObj = grade.get("exam_date");
                    
                    String subject = subjectObj != null ? subjectObj.toString() : "";
                    String gradeValue = gradeObj != null ? gradeObj.toString() : "";
                    String date = dateObj != null ? dateObj.toString() : "";
                    
                    // Обрезаем длинную дату
                    if (date.length() > 10) {
                        date = date.substring(0, 10);
                    }
                    
                    // Записываем строку
                    csv.append(subject).append(",");
                    csv.append(gradeValue).append(",");
                    csv.append(date).append("\n");
                }
            }
            
            return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            // Возвращаем простой CSV с ошибкой
            return ("Error creating CSV: " + e.getMessage()).getBytes();
        }
    }
    
    // Метод для получения имени файла
    public String getCsvFileName(String email) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String user = email.replace("@", "_").replace(".", "_");
        return "grades_" + user + "_" + timestamp + ".csv";
    }
    
}

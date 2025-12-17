package com.example.mywebsite.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class RouteTestController {
    
    @GetMapping("/test/routes")
    @ResponseBody
    public String testRoutes() {
        return """
            <h1>Доступные маршруты:</h1>
            <ul>
                <li><a href="/">Главная страница</a></li>
                <li><a href="/grades">Оценки</a></li>
                <li><a href="/export/drive">Google Drive экспорт</a></li>
                <li><a href="/test/pdf">Тест PDF</a></li>
                <li><a href="/export/download/pdf">Скачать тестовый PDF</a></li>
                <li><a href="/login">Вход</a></li>
                <li><a href="/logout">Выход</a></li>
            </ul>
            """;
    }
    
    @GetMapping("/test/pdf")
    @ResponseBody
    public String testPdfPage() {
        return """
            <h1>Тест PDF экспорта</h1>
            <p><a href="/export/download/pdf" download>Скачать PDF</a></p>
            <p><a href="/export/drive">Google Drive экспорт</a></p>
            <p><a href="/grades">Назад к оценкам</a></p>
            """;
    }
}
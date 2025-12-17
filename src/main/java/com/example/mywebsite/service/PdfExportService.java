package com.example.mywebsite.service;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class PdfExportService {

    @Autowired
    private GradeService gradeService;
    
    @Autowired
    private DatabaseService databaseService;
    
    // Создаем PDF с оценками и диаграммой
    public byte[] createGradesPdf(String email) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        try {
            // Получаем данные
            List<Map<String, Object>> grades = gradeService.getStudentGrades(email);
            Double averageGrade = gradeService.getAverageGrade(email);
            Map<String, Object> studentInfo = gradeService.getStudentInfo(email);
            Map<String, Object> chartData = prepareChartData(grades);
            
            // Создаем документ
            Document document = new Document(PageSize.A4);
            PdfWriter writer = PdfWriter.getInstance(document, baos);
            
            // Добавляем обработчик для колонтитулов
            writer.setPageEvent(new PdfPageEvent());
            
            document.open();
            
            // Шрифты (используем стандартные)
            Font titleFont = new Font(Font.FontFamily.HELVETICA, 18, Font.BOLD);
            Font headerFont = new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD);
            Font normalFont = new Font(Font.FontFamily.HELVETICA, 10);
            Font smallFont = new Font(Font.FontFamily.HELVETICA, 9);
            Font gradeFont = new Font(Font.FontFamily.HELVETICA, 16, Font.BOLD, new BaseColor(76, 175, 80));
            
            // Заголовок
            Paragraph title = new Paragraph("Зачетная книжка студента", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(20);
            document.add(title);
            
            // Информация о студенте
            if (studentInfo != null) {
                Paragraph studentInfoPara = new Paragraph();
                studentInfoPara.add(new Chunk("ФИО: ", headerFont));
                studentInfoPara.add(new Chunk(studentInfo.get("full_name") != null ? 
                    studentInfo.get("full_name").toString() : "Не указано", normalFont));
                studentInfoPara.add(Chunk.NEWLINE);
                studentInfoPara.add(new Chunk("Группа: ", headerFont));
                studentInfoPara.add(new Chunk(studentInfo.get("group_name") != null ? 
                    studentInfo.get("group_name").toString() : "Не указано", normalFont));
                studentInfoPara.add(Chunk.NEWLINE);
                studentInfoPara.add(new Chunk("Email: ", headerFont));
                studentInfoPara.add(new Chunk(email, normalFont));
                studentInfoPara.add(Chunk.NEWLINE);
                studentInfoPara.add(new Chunk("Дата выгрузки: ", headerFont));
                studentInfoPara.add(new Chunk(
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")), 
                    normalFont
                ));
                studentInfoPara.setSpacingAfter(15);
                document.add(studentInfoPara);
            }
            
            // Разделитель
            document.add(new Paragraph(" "));
            PdfPTable separator = new PdfPTable(1);
            separator.setWidthPercentage(100);
            PdfPCell cell = new PdfPCell(new Phrase(" ", normalFont));
            cell.setFixedHeight(1);
            cell.setBorder(PdfPCell.NO_BORDER);
            cell.setBackgroundColor(new BaseColor(220, 220, 220));
            separator.addCell(cell);
            document.add(separator);
            document.add(new Paragraph(" "));
            
            // Общая статистика
            Paragraph statsTitle = new Paragraph("Общая статистика успеваемости", headerFont);
            statsTitle.setSpacingAfter(10);
            document.add(statsTitle);
            
            // Таблица статистики
            PdfPTable statsTable = new PdfPTable(2);
            statsTable.setWidthPercentage(60);
            statsTable.setHorizontalAlignment(Element.ALIGN_LEFT);
            
            addStatRow(statsTable, "Количество оценок:", String.valueOf(grades.size()), normalFont);
            addStatRow(statsTable, "Средний балл:", 
                averageGrade != null ? String.format("%.2f", averageGrade) : "Нет данных", 
                normalFont);
            
            document.add(statsTable);
            document.add(new Paragraph(" "));
            
            // Статистика по категориям
            Paragraph categoryTitle = new Paragraph("Распределение оценок", headerFont);
            categoryTitle.setSpacingAfter(10);
            document.add(categoryTitle);
            
            PdfPTable categoryTable = new PdfPTable(3);
            categoryTable.setWidthPercentage(80);
            categoryTable.setHorizontalAlignment(Element.ALIGN_LEFT);
            
            addCategoryRow(categoryTable, "Отлично (90-100)", 
                chartData.get("excellent").toString(), 
                chartData.get("percentageExcellent").toString() + "%", 
                new BaseColor(76, 175, 80), normalFont);
            
            addCategoryRow(categoryTable, "Хорошо (75-89)", 
                chartData.get("good").toString(), 
                chartData.get("percentageGood").toString() + "%", 
                new BaseColor(255, 193, 7), normalFont);
            
            addCategoryRow(categoryTable, "Удовлетворительно (60-74)", 
                chartData.get("satisfactory").toString(), 
                chartData.get("percentageSatisfactory").toString() + "%", 
                new BaseColor(255, 152, 0), normalFont);
            
            addCategoryRow(categoryTable, "Неудовлетворительно (<60)", 
                chartData.get("unsatisfactory").toString(), 
                chartData.get("percentageUnsatisfactory").toString() + "%", 
                new BaseColor(244, 67, 54), normalFont);
            
            document.add(categoryTable);
            document.add(new Paragraph(" "));
            
            // Разделитель
            document.add(separator);
            document.add(new Paragraph(" "));
            
            // Список оценок
            Paragraph gradesTitle = new Paragraph("Оценки по предметам", headerFont);
            gradesTitle.setSpacingAfter(10);
            document.add(gradesTitle);
            
            if (!grades.isEmpty()) {
                // Таблица оценок
                PdfPTable gradesTable = new PdfPTable(4);
                gradesTable.setWidthPercentage(100);
                gradesTable.setWidths(new float[]{50, 30, 20, 30});
                
                // Заголовки таблицы
                addTableHeader(gradesTable, "Предмет", headerFont);
                addTableHeader(gradesTable, "Описание", headerFont);
                addTableHeader(gradesTable, "Оценка", headerFont);
                addTableHeader(gradesTable, "Дата экзамена", headerFont);
                
                // Данные оценок
                for (Map<String, Object> grade : grades) {
                    String subjectName = grade.get("subject_name") != null ? 
                        grade.get("subject_name").toString() : "";
                    String description = grade.get("description") != null ? 
                        grade.get("description").toString() : "";
                    String gradeValue = grade.get("grade") != null ? 
                        grade.get("grade").toString() : "";
                    String examDate = grade.get("exam_date") != null ? 
                        grade.get("exam_date").toString() : "";
                    
                    addTableRow(gradesTable, subjectName, normalFont);
                    addTableRow(gradesTable, description, smallFont);
                    
                    // Оценка с цветом в зависимости от значения
                    PdfPCell gradeCell = new PdfPCell(new Phrase(gradeValue, normalFont));
                    gradeCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                    gradeCell.setPadding(5);
                    
                    if (!gradeValue.isEmpty()) {
                        try {
                            int gradeInt = Integer.parseInt(gradeValue);
                            if (gradeInt >= 90) {
                                gradeCell.setBackgroundColor(new BaseColor(76, 175, 80, 50));
                            } else if (gradeInt >= 75) {
                                gradeCell.setBackgroundColor(new BaseColor(255, 193, 7, 50));
                            } else if (gradeInt >= 60) {
                                gradeCell.setBackgroundColor(new BaseColor(255, 152, 0, 50));
                            } else {
                                gradeCell.setBackgroundColor(new BaseColor(244, 67, 54, 50));
                            }
                        } catch (NumberFormatException e) {
                            // Если оценка не число, оставляем без цвета
                        }
                    }
                    
                    gradesTable.addCell(gradeCell);
                    addTableRow(gradesTable, examDate, normalFont);
                }
                
                document.add(gradesTable);
            } else {
                Paragraph noGrades = new Paragraph("Оценок пока нет", normalFont);
                noGrades.setAlignment(Element.ALIGN_CENTER);
                noGrades.setSpacingBefore(20);
                document.add(noGrades);
            }
            
            // Добавим "диаграмму" в виде текстового представления
            document.add(new Paragraph(" "));
            document.add(separator);
            document.add(new Paragraph(" "));
            
            Paragraph diagramTitle = new Paragraph("Диаграмма успеваемости", headerFont);
            diagramTitle.setSpacingAfter(10);
            document.add(diagramTitle);
            
            // Создаем текстовую диаграмму
            int excellent = Integer.parseInt(chartData.get("excellent").toString());
            int good = Integer.parseInt(chartData.get("good").toString());
            int satisfactory = Integer.parseInt(chartData.get("satisfactory").toString());
            int unsatisfactory = Integer.parseInt(chartData.get("unsatisfactory").toString());
            int total = excellent + good + satisfactory + unsatisfactory;
            
            if (total > 0) {
                // Создаем простую текстовую диаграмму
                String diagram = createTextDiagram(excellent, good, satisfactory, unsatisfactory);
                Paragraph diagramPara = new Paragraph(diagram, new Font(Font.FontFamily.COURIER, 9));
                diagramPara.setSpacingBefore(10);
                document.add(diagramPara);
                
                // Легенда
                Paragraph legend = new Paragraph();
                legend.add(new Chunk("█ ", new Font(Font.FontFamily.HELVETICA, 10, Font.NORMAL, new BaseColor(76, 175, 80))));
                legend.add(new Chunk("Отлично (" + excellent + ")", smallFont));
                legend.add(Chunk.TABBING);
                legend.add(new Chunk("█ ", new Font(Font.FontFamily.HELVETICA, 10, Font.NORMAL, new BaseColor(255, 193, 7))));
                legend.add(new Chunk("Хорошо (" + good + ")", smallFont));
                legend.add(Chunk.NEWLINE);
                legend.add(new Chunk("█ ", new Font(Font.FontFamily.HELVETICA, 10, Font.NORMAL, new BaseColor(255, 152, 0))));
                legend.add(new Chunk("Удовлетворительно (" + satisfactory + ")", smallFont));
                legend.add(Chunk.TABBING);
                legend.add(new Chunk("█ ", new Font(Font.FontFamily.HELVETICA, 10, Font.NORMAL, new BaseColor(244, 67, 54))));
                legend.add(new Chunk("Неудовлетворительно (" + unsatisfactory + ")", smallFont));
                legend.setSpacingBefore(10);
                document.add(legend);
            }
            
            // Подпись
            document.add(new Paragraph(" "));
            Paragraph signature = new Paragraph(
                "Документ сгенерирован автоматически. Для внесения изменений обратитесь к деканату.",
                smallFont
            );
            signature.setAlignment(Element.ALIGN_CENTER);
            signature.setSpacingBefore(20);
            document.add(signature);
            
            document.close();
            
        } catch (Exception e) {
            System.err.println("Ошибка при создании PDF: " + e.getMessage());
            e.printStackTrace();
            return new byte[0];
        }
        
        return baos.toByteArray();
    }
    
    // Создаем текстовую диаграмму
    private String createTextDiagram(int excellent, int good, int satisfactory, int unsatisfactory) {
        int total = excellent + good + satisfactory + unsatisfactory;
        if (total == 0) return "Нет данных для диаграммы";
        
        int maxBarLength = 40; // Максимальная длина полоски
        int scale = Math.max(1, (maxBarLength + total - 1) / total); // Масштаб
        
        StringBuilder diagram = new StringBuilder();
        diagram.append("Распределение оценок:\n");
        diagram.append("\n");
        
        // Отлично
        diagram.append("Отлично    (");
        diagram.append(String.format("%2d", excellent));
        diagram.append(") ");
        diagram.append("▇".repeat(Math.min(excellent * scale, maxBarLength)));
        if (total > 0) {
            diagram.append(" ");
            diagram.append(String.format("%5.1f%%", (excellent * 100.0 / total)));
        }
        diagram.append("\n");
        
        // Хорошо
        diagram.append("Хорошо     (");
        diagram.append(String.format("%2d", good));
        diagram.append(") ");
        diagram.append("▇".repeat(Math.min(good * scale, maxBarLength)));
        if (total > 0) {
            diagram.append(" ");
            diagram.append(String.format("%5.1f%%", (good * 100.0 / total)));
        }
        diagram.append("\n");
        
        // Удовлетворительно
        diagram.append("Удовл.     (");
        diagram.append(String.format("%2d", satisfactory));
        diagram.append(") ");
        diagram.append("▇".repeat(Math.min(satisfactory * scale, maxBarLength)));
        if (total > 0) {
            diagram.append(" ");
            diagram.append(String.format("%5.1f%%", (satisfactory * 100.0 / total)));
        }
        diagram.append("\n");
        
        // Неудовлетворительно
        diagram.append("Неудовл.   (");
        diagram.append(String.format("%2d", unsatisfactory));
        diagram.append(") ");
        diagram.append("▇".repeat(Math.min(unsatisfactory * scale, maxBarLength)));
        if (total > 0) {
            diagram.append(" ");
            diagram.append(String.format("%5.1f%%", (unsatisfactory * 100.0 / total)));
        }
        
        return diagram.toString();
    }
    
    // Подготовка данных для диаграммы
    private Map<String, Object> prepareChartData(List<Map<String, Object>> grades) {
        Map<String, Object> chartData = new java.util.HashMap<>();
        
        int excellent = 0;
        int good = 0;
        int satisfactory = 0;
        int unsatisfactory = 0;
        
        for (Map<String, Object> grade : grades) {
            Object gradeObj = grade.get("grade");
            if (gradeObj != null) {
                try {
                    int gradeValue = ((Number) gradeObj).intValue();
                    if (gradeValue >= 90) {
                        excellent++;
                    } else if (gradeValue >= 75) {
                        good++;
                    } else if (gradeValue >= 60) {
                        satisfactory++;
                    } else {
                        unsatisfactory++;
                    }
                } catch (Exception e) {
                    // Пропускаем некорректные данные
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
    
    // Вспомогательные методы для создания таблиц
    private void addStatRow(PdfPTable table, String label, String value, Font font) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, font));
        labelCell.setBorder(PdfPCell.NO_BORDER);
        labelCell.setPadding(5);
        
        PdfPCell valueCell = new PdfPCell(new Phrase(value, font));
        valueCell.setBorder(PdfPCell.NO_BORDER);
        valueCell.setPadding(5);
        
        table.addCell(labelCell);
        table.addCell(valueCell);
    }
    
    private void addCategoryRow(PdfPTable table, String category, String count, 
                              String percentage, BaseColor color, Font font) {
        PdfPCell categoryCell = new PdfPCell(new Phrase(category, font));
        categoryCell.setBorder(PdfPCell.NO_BORDER);
        categoryCell.setPadding(5);
        categoryCell.setBackgroundColor(new BaseColor(color.getRed(), color.getGreen(), 
            color.getBlue(), 20));
        
        PdfPCell countCell = new PdfPCell(new Phrase(count, font));
        countCell.setBorder(PdfPCell.NO_BORDER);
        countCell.setPadding(5);
        countCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        
        PdfPCell percentCell = new PdfPCell(new Phrase(percentage, font));
        percentCell.setBorder(PdfPCell.NO_BORDER);
        percentCell.setPadding(5);
        percentCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        
        table.addCell(categoryCell);
        table.addCell(countCell);
        table.addCell(percentCell);
    }
    
    private void addTableHeader(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPadding(5);
        cell.setBackgroundColor(new BaseColor(102, 126, 234));
        cell.setBorderWidth(1);
        cell.setBorderColor(BaseColor.WHITE);
        cell.setPhrase(new Phrase(text, new Font(font.getBaseFont(), font.getSize(), Font.NORMAL, BaseColor.WHITE)));
        table.addCell(cell);
    }
    
    private void addTableRow(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(5);
        cell.setBorderWidth(1);
        cell.setBorderColor(new BaseColor(220, 220, 220));
        table.addCell(cell);
    }
    
    // Класс для колонтитулов PDF
    private class PdfPageEvent extends PdfPageEventHelper {
        private Phrase header;
        
        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            header = new Phrase("Университетская система оценок", 
                new Font(Font.FontFamily.HELVETICA, 8, Font.NORMAL, BaseColor.GRAY));
        }
        
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfPTable table = new PdfPTable(3);
            table.setTotalWidth(document.getPageSize().getWidth() - document.leftMargin() - document.rightMargin());
            
            PdfPCell leftCell = new PdfPCell(header);
            leftCell.setBorder(PdfPCell.NO_BORDER);
            leftCell.setHorizontalAlignment(Element.ALIGN_LEFT);
            
            PdfPCell centerCell = new PdfPCell(new Phrase("Страница " + writer.getPageNumber(), 
                new Font(Font.FontFamily.HELVETICA, 8, Font.NORMAL, BaseColor.GRAY)));
            centerCell.setBorder(PdfPCell.NO_BORDER);
            centerCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            
            PdfPCell rightCell = new PdfPCell(new Phrase(
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
                new Font(Font.FontFamily.HELVETICA, 8, Font.NORMAL, BaseColor.GRAY)));
            rightCell.setBorder(PdfPCell.NO_BORDER);
            rightCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            
            table.addCell(leftCell);
            table.addCell(centerCell);
            table.addCell(rightCell);
            
            table.writeSelectedRows(0, -1, 
                document.leftMargin(), 
                document.bottomMargin() - 10, 
                writer.getDirectContent());
        }
    }
}
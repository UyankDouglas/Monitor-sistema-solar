package com.solarmonitor.energy.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.solarmonitor.energy.web.dto.DailyGenerationDto;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Gera os arquivos de exportação do histórico diário: CSV (separador ';' e
 * BOM UTF-8, amigável ao Excel pt-BR), XLSX (Apache POI) e PDF (OpenPDF).
 */
@Service
public class ExportService {

    private static final String[] HEADERS = {
            "Data", "Geração (kWh)", "Pico (W)", "Consumo (kWh)", "Exportado (kWh)",
            "Importado (kWh)", "Autoconsumo (kWh)", "Autossuficiência (%)",
            "Economia", "CO₂ evitado (kg)"};

    public byte[] toCsv(List<DailyGenerationDto> days) {
        StringBuilder sb = new StringBuilder("﻿");           // BOM p/ Excel reconhecer UTF-8
        sb.append(String.join(";", HEADERS)).append("\r\n");
        for (DailyGenerationDto d : days) {
            sb.append(d.date()).append(';')
                    .append(num(d.energyKwh())).append(';')
                    .append(d.peakPowerW() == null ? "" : d.peakPowerW()).append(';')
                    .append(num(d.consumptionKwh())).append(';')
                    .append(num(d.exportKwh())).append(';')
                    .append(num(d.importKwh())).append(';')
                    .append(num(d.selfConsumptionKwh())).append(';')
                    .append(num(d.selfSufficiencyPct())).append(';')
                    .append(num(d.savings())).append(';')
                    .append(num(d.co2AvoidedKg())).append("\r\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] toXlsx(List<DailyGenerationDto> days) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Geração diária");
            CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font bold = workbook.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);

            Row header = sheet.createRow(0);
            for (int c = 0; c < HEADERS.length; c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(HEADERS[c]);
                cell.setCellStyle(headerStyle);
            }
            int r = 1;
            for (DailyGenerationDto d : days) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(d.date().toString());
                setNum(row, 1, d.energyKwh());
                if (d.peakPowerW() != null) {
                    row.createCell(2).setCellValue(d.peakPowerW());
                }
                setNum(row, 3, d.consumptionKwh());
                setNum(row, 4, d.exportKwh());
                setNum(row, 5, d.importKwh());
                setNum(row, 6, d.selfConsumptionKwh());
                setNum(row, 7, d.selfSufficiencyPct());
                setNum(row, 8, d.savings());
                setNum(row, 9, d.co2AvoidedKg());
            }
            for (int c = 0; c < HEADERS.length; c++) {
                sheet.autoSizeColumn(c);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Falha gerando XLSX", e);
        }
    }

    public byte[] toPdf(List<DailyGenerationDto> days, String title) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4.rotate(), 24, 24, 24, 24);
        PdfWriter.getInstance(document, out);
        document.open();

        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
        Paragraph heading = new Paragraph(title, titleFont);
        heading.setSpacingAfter(12);
        document.add(heading);

        PdfPTable table = new PdfPTable(HEADERS.length);
        table.setWidthPercentage(100);
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8);
        Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 8);
        for (String h : HEADERS) {
            // Helvetica Type1 (WinAnsi) descarta U+2082 silenciosamente —
            // "CO₂" viraria "CO" no PDF. Substitui pelo dígito comum.
            PdfPCell cell = new PdfPCell(new Phrase(h.replace('₂', '2'), headerFont));
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }
        for (DailyGenerationDto d : days) {
            table.addCell(new Phrase(String.valueOf(d.date()), cellFont));
            table.addCell(new Phrase(num(d.energyKwh()), cellFont));
            table.addCell(new Phrase(d.peakPowerW() == null ? "" : String.valueOf(d.peakPowerW()), cellFont));
            table.addCell(new Phrase(num(d.consumptionKwh()), cellFont));
            table.addCell(new Phrase(num(d.exportKwh()), cellFont));
            table.addCell(new Phrase(num(d.importKwh()), cellFont));
            table.addCell(new Phrase(num(d.selfConsumptionKwh()), cellFont));
            table.addCell(new Phrase(num(d.selfSufficiencyPct()), cellFont));
            table.addCell(new Phrase(num(d.savings()), cellFont));
            table.addCell(new Phrase(num(d.co2AvoidedKg()), cellFont));
        }
        document.add(table);
        document.close();
        return out.toByteArray();
    }

    private void setNum(Row row, int col, BigDecimal value) {
        if (value != null) {
            row.createCell(col).setCellValue(value.doubleValue());
        }
    }

    /**
     * Vírgula decimal (pt-BR): no Excel pt-BR o ponto é separador de MILHAR —
     * "42.500" viraria 42500 (corrupção ×1000). O ';' como separador de campo
     * torna a vírgula segura no CSV; no PDF é a convenção correta.
     */
    private String num(BigDecimal value) {
        return value == null ? "" : value.toPlainString().replace('.', ',');
    }
}

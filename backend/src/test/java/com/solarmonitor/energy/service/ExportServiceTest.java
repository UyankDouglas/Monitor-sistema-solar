package com.solarmonitor.energy.service;

import com.solarmonitor.energy.web.dto.DailyGenerationDto;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExportServiceTest {

    private final ExportService exportService = new ExportService();

    private final List<DailyGenerationDto> days = List.of(
            new DailyGenerationDto(LocalDate.of(2026, 7, 10), new BigDecimal("42.500"),
                    8200, Instant.parse("2026-07-10T15:12:00Z"), 320,
                    new BigDecimal("18.300"), new BigDecimal("30.100"), new BigDecimal("2.400"),
                    new BigDecimal("12.400"), new BigDecimal("67.76"),
                    new BigDecimal("40.38"), new BigDecimal("3.472")),
            new DailyGenerationDto(LocalDate.of(2026, 7, 11), new BigDecimal("38.900"),
                    7900, null, null,
                    new BigDecimal("20.000"), new BigDecimal("25.000"), new BigDecimal("3.100"),
                    new BigDecimal("13.900"), new BigDecimal("69.50"),
                    new BigDecimal("36.96"), new BigDecimal("3.178")));

    @Test
    void csvHasBomHeaderAndOneLinePerDay() {
        String csv = new String(exportService.toCsv(days), StandardCharsets.UTF_8);

        assertThat(csv).startsWith("﻿");                    // BOM p/ Excel pt-BR
        assertThat(csv).contains("Data;Geração (kWh);");
        // Vírgula decimal: ponto seria lido como separador de milhar no Excel pt-BR
        assertThat(csv).contains("2026-07-10;42,500;8200;");
        assertThat(csv).contains("2026-07-11;38,900;7900;");
        assertThat(csv.trim().split("\r\n")).hasSize(3);         // header + 2 dias
    }

    @Test
    void xlsxIsValidWorkbookWithDataRows() throws Exception {
        byte[] bytes = exportService.toXlsx(days);

        assertThat(bytes).startsWith((byte) 'P', (byte) 'K');    // zip magic
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(2);      // header + 2 dias
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Data");
            assertThat(sheet.getRow(1).getCell(1).getNumericCellValue()).isEqualTo(42.5);
            assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("2026-07-11");
        }
    }

    @Test
    void pdfIsGeneratedWithPdfMagicBytes() {
        byte[] bytes = exportService.toPdf(days, "Geração diária — teste");

        assertThat(new String(bytes, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(bytes.length).isGreaterThan(1_000);
    }
}

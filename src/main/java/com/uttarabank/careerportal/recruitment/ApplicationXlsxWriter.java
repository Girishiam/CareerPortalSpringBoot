package com.uttarabank.careerportal.recruitment;

import java.io.*;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

final class ApplicationXlsxWriter {
  private static final String[] HEADERS = {
    "Tracking Number",
    "Candidate Name",
    "CV Number",
    "Email",
    "Mobile",
    "Job Code",
    "Job Title",
    "Designation",
    "Employment Type",
    "Job Location",
    "Application Status",
    "Eligibility",
    "Submitted At",
    "Father's Name",
    "Mother's Name",
    "Date of Birth",
    "Gender",
    "Marital Status",
    "Nationality",
    "NID Number",
    "Passport Number",
    "Present Address",
    "Permanent Address",
    "Education",
    "Experience",
    "Training",
    "Languages",
    "Extracurricular Activities",
    "References",
    "Documents"
  };
  private static final String[] KEYS = {
    "tracking_number",
    "full_name",
    "cv_number",
    "email",
    "mobile",
    "job_code",
    "job_title",
    "job_designation",
    "employment_type",
    "job_location",
    "status",
    "eligibility_status",
    "submitted_at",
    "father_name",
    "mother_name",
    "date_of_birth",
    "gender",
    "marital_status",
    "nationality",
    "nid_number",
    "passport_number",
    "present_address",
    "permanent_address",
    "education",
    "experience",
    "training",
    "languages",
    "activities",
    "reference_details",
    "documents"
  };

  private ApplicationXlsxWriter() {}

  static byte[] write(List<Map<String, Object>> rows) {
    return write(rows, "Submitted Candidate Applications", false);
  }

  static byte[] writeStage(List<Map<String, Object>> rows, String titleText) {
    return write(rows, titleText, true);
  }

  private static byte[] write(
      List<Map<String, Object>> rows, String titleText, boolean stageFields) {
    try (SXSSFWorkbook book = new SXSSFWorkbook(200);
        ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      book.setCompressTempFiles(true);
      Sheet sheet = book.createSheet(stageFields ? "Stage Applicants" : "Submitted Applications");
      CellStyle title =
          style(book, true, (short) 14, IndexedColors.DARK_BLUE, IndexedColors.WHITE, false);
      CellStyle header =
          style(book, true, (short) 11, IndexedColors.BLUE_GREY, IndexedColors.WHITE, true);
      CellStyle body = style(book, false, (short) 10, null, null, true);
      CellStyle wrapped = style(book, false, (short) 10, null, null, true);
      wrapped.setWrapText(true);
      wrapped.setVerticalAlignment(VerticalAlignment.TOP);
      CellStyle editable =
          style(book, true, (short) 10, IndexedColors.LIGHT_YELLOW, IndexedColors.DARK_GREEN, true);
      int columns = HEADERS.length + (stageFields ? 3 : 0);
      Row titleRow = sheet.createRow(0);
      titleRow.setHeightInPoints(26);
      Cell titleCell = titleRow.createCell(0);
      titleCell.setCellValue(titleText);
      titleCell.setCellStyle(title);
      sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, columns - 1));
      Row headerRow = sheet.createRow(1);
      headerRow.setHeightInPoints(30);
      for (int i = 0; i < HEADERS.length; i++) {
        Cell cell = headerRow.createCell(i);
        cell.setCellValue(HEADERS[i]);
        cell.setCellStyle(header);
      }
      if (stageFields) {
        String[] extra = {"Application ID (do not edit)", "Selected (YES/NO)", "Remarks"};
        for (int i = 0; i < extra.length; i++) {
          Cell cell = headerRow.createCell(HEADERS.length + i);
          cell.setCellValue(extra[i]);
          cell.setCellStyle(header);
        }
      }
      int rowNumber = 2;
      for (Map<String, Object> data : rows) {
        Row row = sheet.createRow(rowNumber++);
        row.setHeightInPoints(34);
        for (int i = 0; i < KEYS.length; i++) {
          Cell cell = row.createCell(i);
          cell.setCellValue(text(data, KEYS[i]));
          cell.setCellStyle(i >= 21 ? wrapped : body);
        }
        if (stageFields) {
          Cell id = row.createCell(HEADERS.length);
          id.setCellValue(text(data, "application_id"));
          id.setCellStyle(body);
          Cell selected = row.createCell(HEADERS.length + 1);
          selected.setCellValue(text(data, "selected"));
          selected.setCellStyle(editable);
          Cell remarks = row.createCell(HEADERS.length + 2);
          remarks.setCellValue(text(data, "remarks"));
          remarks.setCellStyle(editable);
        }
      }
      sheet.createFreezePane(0, 2);
      sheet.setAutoFilter(new CellRangeAddress(1, Math.max(1, rowNumber - 1), 0, columns - 1));
      int[] widths = {
        18, 28, 20, 30, 17, 13, 30, 25, 18, 22, 18, 15, 22, 25, 25, 15, 13, 15, 15, 20, 20, 38, 38,
        55, 55, 55, 48, 55, 48, 55
      };
      for (int i = 0; i < widths.length; i++)
        sheet.setColumnWidth(i, Math.min(255, widths[i]) * 256);
      if (stageFields) {
        sheet.setColumnWidth(HEADERS.length, 24 * 256);
        sheet.setColumnWidth(HEADERS.length + 1, 22 * 256);
        sheet.setColumnWidth(HEADERS.length + 2, 45 * 256);
        DataValidationHelper helper = sheet.getDataValidationHelper();
        DataValidation validation =
            helper.createValidation(
                helper.createExplicitListConstraint(new String[] {"YES", "NO"}),
                new org.apache.poi.ss.util.CellRangeAddressList(
                    2, Math.max(2, rowNumber - 1), HEADERS.length + 1, HEADERS.length + 1));
        validation.setShowErrorBox(true);
        validation.createErrorBox("Invalid selection", "Choose YES or NO.");
        validation.setShowPromptBox(true);
        validation.createPromptBox(
            "Stage selection",
            "Choose YES to select this applicant or NO to remove the selection.");
        sheet.addValidationData(validation);
      }
      book.write(output);
      book.dispose();
      return output.toByteArray();
    } catch (IOException exception) {
      throw new IllegalStateException("Could not generate application export.", exception);
    }
  }

  private static CellStyle style(
      Workbook book,
      boolean bold,
      short size,
      IndexedColors fill,
      IndexedColors fontColor,
      boolean borders) {
    CellStyle style = book.createCellStyle();
    Font font = book.createFont();
    font.setBold(bold);
    font.setFontHeightInPoints(size);
    if (fontColor != null) font.setColor(fontColor.getIndex());
    style.setFont(font);
    if (fill != null) {
      style.setFillForegroundColor(fill.getIndex());
      style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    }
    if (borders) {
      style.setBorderBottom(BorderStyle.THIN);
      style.setBorderTop(BorderStyle.THIN);
      style.setBorderLeft(BorderStyle.THIN);
      style.setBorderRight(BorderStyle.THIN);
      short color = IndexedColors.GREY_25_PERCENT.getIndex();
      style.setBottomBorderColor(color);
      style.setTopBorderColor(color);
      style.setLeftBorderColor(color);
      style.setRightBorderColor(color);
    }
    style.setVerticalAlignment(VerticalAlignment.CENTER);
    return style;
  }

  private static String text(Map<String, Object> row, String key) {
    Object value = row.get(key);
    if (value == null) value = row.get(key.toUpperCase(Locale.ROOT));
    return value == null ? "" : value.toString();
  }
}

package com.uttarabank.careerportal.recruitment;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

final class ApplicationXlsxWriter {
  private ApplicationXlsxWriter() {}

  static byte[] write(List<Map<String, Object>> rows) {
    try {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
        entry(
            zip,
            "[Content_Types].xml",
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
              <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
              <Default Extension="xml" ContentType="application/xml"/>
              <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
              <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
              <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
            </Types>
            """);
        entry(
            zip,
            "_rels/.rels",
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
            </Relationships>
            """);
        entry(
            zip,
            "xl/workbook.xml",
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
              <sheets><sheet name="Applications" sheetId="1" r:id="rId1"/></sheets>
            </workbook>
            """);
        entry(
            zip,
            "xl/_rels/workbook.xml.rels",
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
              <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
            </Relationships>
            """);
        entry(
            zip,
            "xl/styles.xml",
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <fonts count="2"><font/><font><b/></font></fonts>
              <fills count="1"><fill><patternFill patternType="none"/></fill></fills>
              <borders count="1"><border/></borders>
              <cellStyleXfs count="1"><xf/></cellStyleXfs>
              <cellXfs count="2"><xf/><xf fontId="1" applyFont="1"/></cellXfs>
            </styleSheet>
            """);
        entry(zip, "xl/worksheets/sheet1.xml", sheet(rows));
      }
      return output.toByteArray();
    } catch (IOException exception) {
      throw new IllegalStateException("Could not generate application export.", exception);
    }
  }

  private static String sheet(List<Map<String, Object>> rows) {
    StringBuilder xml =
        new StringBuilder(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
            <sheetData>
            """);
    row(
        xml,
        1,
        List.of(
            "Tracking Number",
            "Candidate",
            "CV Number",
            "Email",
            "Mobile",
            "Job Code",
            "Job Title",
            "Job Designation",
            "Employment Type",
            "Job Location",
            "Status",
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
            "Documents"),
        true);
    int number = 2;
    for (Map<String, Object> data : rows)
      row(
          xml,
          number++,
          List.of(
              text(data, "tracking_number"),
              text(data, "full_name"),
              text(data, "cv_number"),
              text(data, "email"),
              text(data, "mobile"),
              text(data, "job_code"),
              text(data, "job_title"),
              text(data, "job_designation"),
              text(data, "employment_type"),
              text(data, "job_location"),
              text(data, "status"),
              text(data, "eligibility_status"),
              text(data, "submitted_at"),
              text(data, "father_name"),
              text(data, "mother_name"),
              text(data, "date_of_birth"),
              text(data, "gender"),
              text(data, "marital_status"),
              text(data, "nationality"),
              text(data, "nid_number"),
              text(data, "passport_number"),
              text(data, "present_address"),
              text(data, "permanent_address"),
              text(data, "education"),
              text(data, "experience"),
              text(data, "training"),
              text(data, "languages"),
              text(data, "activities"),
              text(data, "reference_details"),
              text(data, "documents")),
          false);
    return xml.append("</sheetData></worksheet>").toString();
  }

  private static void row(
      StringBuilder xml, int rowNumber, List<String> values, boolean header) {
    xml.append("<row r=\"").append(rowNumber).append("\">");
    for (int i = 0; i < values.size(); i++) {
      String reference = column(i) + rowNumber;
      xml.append("<c r=\"")
          .append(reference)
          .append("\" t=\"inlineStr\"")
          .append(header ? " s=\"1\"" : "")
          .append("><is><t>")
          .append(escape(values.get(i)))
          .append("</t></is></c>");
    }
    xml.append("</row>");
  }

  private static String column(int index) {
    StringBuilder value = new StringBuilder();
    for (int current = index; current >= 0; current = current / 26 - 1)
      value.insert(0, (char) ('A' + current % 26));
    return value.toString();
  }

  private static String text(Map<String, Object> row, String key) {
    Object value = row.get(key);
    if (value == null) value = row.get(key.toUpperCase(Locale.ROOT));
    return value == null ? "" : value.toString();
  }

  private static String escape(String value) {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }

  private static void entry(ZipOutputStream zip, String name, String content)
      throws IOException {
    zip.putNextEntry(new ZipEntry(name));
    zip.write(content.strip().getBytes(StandardCharsets.UTF_8));
    zip.closeEntry();
  }
}

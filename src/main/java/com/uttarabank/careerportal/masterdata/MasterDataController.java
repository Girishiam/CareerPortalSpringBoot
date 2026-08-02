package com.uttarabank.careerportal.masterdata;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/master-data")
public class MasterDataController {
  private final JdbcTemplate jdbc;

  public MasterDataController(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @GetMapping("/divisions")
  public List<Map<String, Object>> divisions() {
    return jdbc.queryForList("SELECT division_id id,name FROM dbo.division ORDER BY name");
  }

  @GetMapping("/districts")
  public List<Map<String, Object>> districts(@RequestParam long divisionId) {
    return jdbc.queryForList(
        "SELECT district_id id,name FROM dbo.district WHERE division_id=? ORDER BY name",
        divisionId);
  }

  @GetMapping("/upazilas")
  public List<Map<String, Object>> upazilas(@RequestParam long districtId) {
    return jdbc.queryForList(
        "SELECT upazila_id id,name FROM dbo.upazila WHERE district_id=? ORDER BY name", districtId);
  }

  @GetMapping("/qualifications")
  public List<Map<String, Object>> qualifications() {
    return jdbc.queryForList(
        "SELECT qualification_id id,name FROM dbo.qualification ORDER BY COALESCE(display_order, 2147483647),level_rank,name");
  }

  @GetMapping("/subjects")
  public List<Map<String, Object>> subjects(
      @RequestParam(required = false) Long qualificationId) {
    if (qualificationId != null) {
      List<String> qualificationCodes =
          jdbc.queryForList(
              "SELECT code FROM dbo.qualification WHERE qualification_id=?",
              String.class,
              qualificationId);
      if (!qualificationCodes.isEmpty()
          && Set.of("SSC", "DAKHIL", "O_LEVEL", "HSC", "ALIM", "A_LEVEL")
              .contains(qualificationCodes.getFirst())) {
        return jdbc.queryForList(
            """
            SELECT subject_id id,name
            FROM dbo.subject
            WHERE name IN ('Science','Arts','Commerce','Humanities','Business Studies','Others')
            ORDER BY COALESCE(display_order,2147483647),name
            """);
      }
    }
    return jdbc.queryForList(
        "SELECT subject_id id,name FROM dbo.subject ORDER BY COALESCE(display_order,2147483647),name");
  }

  @GetMapping("/institutions")
  public List<Map<String, Object>> institutions() {
    return jdbc.queryForList(
        "SELECT institution_id id,name FROM dbo.institution ORDER BY COALESCE(display_order,2147483647),name");
  }

  @GetMapping("/departments")
  public List<Map<String, Object>> departments() {
    return jdbc.queryForList("SELECT department_id id,name FROM dbo.department ORDER BY name");
  }
}

INSERT
  dbo.role (code, name)
VALUES
  ('APPLICANT', 'Applicant'),
  ('HR_ADMIN', 'HR Administrator'),
  ('SYSTEM_ADMIN', 'System Administrator');

INSERT
  dbo.department (department_id, code, name)
VALUES
  (1, 'HR', 'Human Resources');

INSERT
  dbo.division (division_id, name)
VALUES
  (1, 'Dhaka');

INSERT
  dbo.district (district_id, division_id, name)
VALUES
  (1, 1, 'Dhaka');

INSERT
  dbo.upazila (upazila_id, district_id, name)
VALUES
  (1, 1, 'Dhaka City');

INSERT
  dbo.qualification (qualification_id, code, name, level_rank)
VALUES
  (1, 'SSC', 'Secondary School Certificate', 10),
  (2, 'HSC', 'Higher Secondary Certificate', 12),
  (8, 'BACHELOR', 'Bachelor Degree', 16);

INSERT
  dbo.subject (subject_id, name)
VALUES
  (21, 'General');

INSERT
  dbo.institution (institution_id, name)
VALUES
  (55, 'Other');

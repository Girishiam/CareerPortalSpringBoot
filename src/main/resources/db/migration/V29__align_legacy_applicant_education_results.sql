/*
  Align education/result columns in databases created before the current
  applicant education contract.
*/

IF COL_LENGTH('dbo.applicant_education', 'subject_id') IS NULL
  ALTER TABLE dbo.applicant_education ADD subject_id BIGINT NULL;

IF COL_LENGTH('dbo.applicant_education', 'institution_id') IS NULL
  ALTER TABLE dbo.applicant_education ADD institution_id BIGINT NULL;

IF COL_LENGTH('dbo.applicant_education', 'institution_name') IS NULL
  ALTER TABLE dbo.applicant_education ADD institution_name NVARCHAR(200) NULL;

IF COL_LENGTH('dbo.applicant_education', 'result_type') IS NULL
  ALTER TABLE dbo.applicant_education ADD result_type VARCHAR(20) NULL;

IF COL_LENGTH('dbo.applicant_education', 'result_value') IS NULL
  ALTER TABLE dbo.applicant_education ADD result_value DECIMAL(5,2) NULL;

IF COL_LENGTH('dbo.applicant_education', 'result_scale') IS NULL
  ALTER TABLE dbo.applicant_education ADD result_scale DECIMAL(5,2) NULL;

IF COL_LENGTH('dbo.applicant_education', 'result_grade') IS NULL
  ALTER TABLE dbo.applicant_education ADD result_grade VARCHAR(30) NULL;

IF COL_LENGTH('dbo.applicant_education', 'passing_year') IS NULL
  ALTER TABLE dbo.applicant_education ADD passing_year SMALLINT NULL;

IF COL_LENGTH('dbo.applicant_education', 'is_highest_degree') IS NULL
BEGIN
  ALTER TABLE dbo.applicant_education
    ADD is_highest_degree BIT NOT NULL
      CONSTRAINT df_applicant_education_highest_v29 DEFAULT (0) WITH VALUES;
END;

IF COL_LENGTH('dbo.applicant_education', 'grade') IS NOT NULL
  EXEC('UPDATE dbo.applicant_education SET result_grade=CONVERT(VARCHAR(30),grade) WHERE result_grade IS NULL');

IF COL_LENGTH('dbo.application_education_snapshot', 'result_grade') IS NULL
  ALTER TABLE dbo.application_education_snapshot ADD result_grade VARCHAR(30) NULL;

IF COL_LENGTH('dbo.application_education_snapshot', 'result_value') IS NULL
  ALTER TABLE dbo.application_education_snapshot ADD result_value DECIMAL(5,2) NULL;

IF COL_LENGTH('dbo.application_education_snapshot', 'result_scale') IS NULL
  ALTER TABLE dbo.application_education_snapshot ADD result_scale DECIMAL(5,2) NULL;

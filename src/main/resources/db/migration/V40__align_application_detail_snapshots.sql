IF COL_LENGTH('dbo.application_education_snapshot','qualification_id') IS NULL
  ALTER TABLE dbo.application_education_snapshot ADD qualification_id BIGINT NULL;
IF COL_LENGTH('dbo.application_education_snapshot','subject_id') IS NULL
  ALTER TABLE dbo.application_education_snapshot ADD subject_id BIGINT NULL;
IF COL_LENGTH('dbo.application_education_snapshot','institution_name') IS NULL
  ALTER TABLE dbo.application_education_snapshot ADD institution_name NVARCHAR(200) NULL;
IF COL_LENGTH('dbo.application_education_snapshot','result_type') IS NULL
  ALTER TABLE dbo.application_education_snapshot ADD result_type VARCHAR(20) NULL;
IF COL_LENGTH('dbo.application_education_snapshot','result_value') IS NULL
  ALTER TABLE dbo.application_education_snapshot ADD result_value DECIMAL(5,2) NULL;
IF COL_LENGTH('dbo.application_education_snapshot','result_scale') IS NULL
  ALTER TABLE dbo.application_education_snapshot ADD result_scale DECIMAL(5,2) NULL;
IF COL_LENGTH('dbo.application_education_snapshot','result_grade') IS NULL
  ALTER TABLE dbo.application_education_snapshot ADD result_grade VARCHAR(30) NULL;
IF COL_LENGTH('dbo.application_education_snapshot','passing_year') IS NULL
  ALTER TABLE dbo.application_education_snapshot ADD passing_year SMALLINT NULL;

IF COL_LENGTH('dbo.application_experience_snapshot','employer_name') IS NULL
  ALTER TABLE dbo.application_experience_snapshot ADD employer_name NVARCHAR(200) NULL;
IF COL_LENGTH('dbo.application_experience_snapshot','designation') IS NULL
  ALTER TABLE dbo.application_experience_snapshot ADD designation NVARCHAR(150) NULL;
IF COL_LENGTH('dbo.application_experience_snapshot','start_date') IS NULL
  ALTER TABLE dbo.application_experience_snapshot ADD start_date DATE NULL;
IF COL_LENGTH('dbo.application_experience_snapshot','end_date') IS NULL
  ALTER TABLE dbo.application_experience_snapshot ADD end_date DATE NULL;

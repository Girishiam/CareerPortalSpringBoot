IF COL_LENGTH('dbo.applicant_education','subject_name') IS NULL
  ALTER TABLE dbo.applicant_education ADD subject_name NVARCHAR(200) NULL;
IF COL_LENGTH('dbo.application_education_snapshot','subject_name') IS NULL
  ALTER TABLE dbo.application_education_snapshot ADD subject_name NVARCHAR(200) NULL;

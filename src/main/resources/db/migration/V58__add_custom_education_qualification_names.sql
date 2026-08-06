IF COL_LENGTH('dbo.applicant_education','qualification_name') IS NULL
  ALTER TABLE dbo.applicant_education ADD qualification_name NVARCHAR(200) NULL;

IF COL_LENGTH('dbo.application_education_snapshot','qualification_name') IS NULL
  ALTER TABLE dbo.application_education_snapshot ADD qualification_name NVARCHAR(200) NULL;

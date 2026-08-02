IF COL_LENGTH('dbo.application_profile_snapshot','father_name') IS NULL
  ALTER TABLE dbo.application_profile_snapshot ADD father_name NVARCHAR(150) NULL;
IF COL_LENGTH('dbo.application_profile_snapshot','mother_name') IS NULL
  ALTER TABLE dbo.application_profile_snapshot ADD mother_name NVARCHAR(150) NULL;
IF COL_LENGTH('dbo.application_profile_snapshot','date_of_birth') IS NULL
  ALTER TABLE dbo.application_profile_snapshot ADD date_of_birth DATE NULL;
IF COL_LENGTH('dbo.application_profile_snapshot','gender') IS NULL
  ALTER TABLE dbo.application_profile_snapshot ADD gender VARCHAR(20) NULL;
IF COL_LENGTH('dbo.application_profile_snapshot','marital_status') IS NULL
  ALTER TABLE dbo.application_profile_snapshot ADD marital_status VARCHAR(30) NULL;
IF COL_LENGTH('dbo.application_profile_snapshot','nationality') IS NULL
  ALTER TABLE dbo.application_profile_snapshot ADD nationality VARCHAR(50) NULL;
IF COL_LENGTH('dbo.application_profile_snapshot','nid_number') IS NULL
  ALTER TABLE dbo.application_profile_snapshot ADD nid_number VARCHAR(30) NULL;

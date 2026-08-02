IF OBJECT_ID('dbo.applicant_training', 'U') IS NOT NULL
BEGIN
  IF OBJECT_ID('dbo.ck_applicant_training_duration', 'C') IS NOT NULL
    ALTER TABLE dbo.applicant_training DROP CONSTRAINT ck_applicant_training_duration;

  ALTER TABLE dbo.applicant_training ALTER COLUMN training_title NVARCHAR(200) NULL;
  ALTER TABLE dbo.applicant_training ALTER COLUMN duration_months INT NULL;
  ALTER TABLE dbo.applicant_training
    ADD CONSTRAINT ck_applicant_training_duration
    CHECK (duration_months IS NULL OR duration_months > 0);
END;

IF OBJECT_ID('dbo.applicant_language', 'U') IS NOT NULL
BEGIN
  IF OBJECT_ID('dbo.ck_applicant_language_speaking', 'C') IS NOT NULL
    ALTER TABLE dbo.applicant_language DROP CONSTRAINT ck_applicant_language_speaking;
  IF OBJECT_ID('dbo.ck_applicant_language_writing', 'C') IS NOT NULL
    ALTER TABLE dbo.applicant_language DROP CONSTRAINT ck_applicant_language_writing;
  IF OBJECT_ID('dbo.ck_applicant_language_listening', 'C') IS NOT NULL
    ALTER TABLE dbo.applicant_language DROP CONSTRAINT ck_applicant_language_listening;
  IF OBJECT_ID('dbo.ck_applicant_language_reading', 'C') IS NOT NULL
    ALTER TABLE dbo.applicant_language DROP CONSTRAINT ck_applicant_language_reading;

  ALTER TABLE dbo.applicant_language ALTER COLUMN speaking VARCHAR(10) NULL;
  ALTER TABLE dbo.applicant_language ALTER COLUMN writing VARCHAR(10) NULL;
  ALTER TABLE dbo.applicant_language ALTER COLUMN listening VARCHAR(10) NULL;
  ALTER TABLE dbo.applicant_language ALTER COLUMN reading VARCHAR(10) NULL;

  ALTER TABLE dbo.applicant_language
    ADD CONSTRAINT ck_applicant_language_speaking CHECK (speaking IS NULL OR speaking IN ('LOW','MEDIUM','HIGH'));
  ALTER TABLE dbo.applicant_language
    ADD CONSTRAINT ck_applicant_language_writing CHECK (writing IS NULL OR writing IN ('LOW','MEDIUM','HIGH'));
  ALTER TABLE dbo.applicant_language
    ADD CONSTRAINT ck_applicant_language_listening CHECK (listening IS NULL OR listening IN ('LOW','MEDIUM','HIGH'));
  ALTER TABLE dbo.applicant_language
    ADD CONSTRAINT ck_applicant_language_reading CHECK (reading IS NULL OR reading IN ('LOW','MEDIUM','HIGH'));
END;

IF OBJECT_ID('dbo.applicant_extracurricular_activity', 'U') IS NULL
BEGIN
  CREATE TABLE dbo.applicant_extracurricular_activity (
    activity_id BIGINT IDENTITY PRIMARY KEY,
    applicant_id BIGINT NOT NULL REFERENCES dbo.applicant_profile(applicant_id),
    activity_name NVARCHAR(200) NOT NULL,
    organization NVARCHAR(200) NULL,
    role_name NVARCHAR(150) NULL,
    activity_summary NVARCHAR(1000) NULL,
    achievement NVARCHAR(500) NULL
  );
END;

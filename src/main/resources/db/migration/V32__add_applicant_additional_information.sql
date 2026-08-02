IF OBJECT_ID('dbo.applicant_training', 'U') IS NULL
BEGIN
  CREATE TABLE dbo.applicant_training (
    training_id BIGINT IDENTITY PRIMARY KEY,
    applicant_id BIGINT NOT NULL REFERENCES dbo.applicant_profile(applicant_id),
    training_title NVARCHAR(200) NOT NULL,
    training_summary NVARCHAR(1000) NULL,
    duration_months INT NOT NULL,
    CONSTRAINT ck_applicant_training_duration CHECK (duration_months > 0)
  );
END;

IF OBJECT_ID('dbo.applicant_language', 'U') IS NULL
BEGIN
  CREATE TABLE dbo.applicant_language (
    language_id BIGINT IDENTITY PRIMARY KEY,
    applicant_id BIGINT NOT NULL REFERENCES dbo.applicant_profile(applicant_id),
    language_name NVARCHAR(100) NOT NULL,
    speaking VARCHAR(10) NOT NULL,
    writing VARCHAR(10) NOT NULL,
    listening VARCHAR(10) NOT NULL,
    reading VARCHAR(10) NOT NULL,
    CONSTRAINT uq_applicant_language UNIQUE(applicant_id,language_name),
    CONSTRAINT ck_applicant_language_speaking CHECK (speaking IN ('LOW','MEDIUM','HIGH')),
    CONSTRAINT ck_applicant_language_writing CHECK (writing IN ('LOW','MEDIUM','HIGH')),
    CONSTRAINT ck_applicant_language_listening CHECK (listening IN ('LOW','MEDIUM','HIGH')),
    CONSTRAINT ck_applicant_language_reading CHECK (reading IN ('LOW','MEDIUM','HIGH'))
  );
END;

IF OBJECT_ID('dbo.applicant_reference', 'U') IS NULL
BEGIN
  CREATE TABLE dbo.applicant_reference (
    reference_id BIGINT IDENTITY PRIMARY KEY,
    applicant_id BIGINT NOT NULL REFERENCES dbo.applicant_profile(applicant_id),
    full_name NVARCHAR(150) NOT NULL,
    organization NVARCHAR(200) NOT NULL,
    designation NVARCHAR(150) NOT NULL,
    relationship NVARCHAR(100) NULL,
    email NVARCHAR(254) NULL,
    mobile VARCHAR(20) NULL
  );
END;

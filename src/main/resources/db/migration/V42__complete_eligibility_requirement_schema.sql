IF COL_LENGTH('dbo.job_age_policy','applicant_category') IS NULL
  ALTER TABLE dbo.job_age_policy
    ADD applicant_category VARCHAR(50) NOT NULL
      CONSTRAINT df_job_age_policy_category DEFAULT 'GENERAL';
IF COL_LENGTH('dbo.job_age_policy','minimum_age') IS NULL
  ALTER TABLE dbo.job_age_policy ADD minimum_age INT NULL;
IF COL_LENGTH('dbo.job_age_policy','maximum_age') IS NULL
  ALTER TABLE dbo.job_age_policy ADD maximum_age INT NULL;
IF COL_LENGTH('dbo.job_age_policy','age_reference_date') IS NULL
  ALTER TABLE dbo.job_age_policy ADD age_reference_date DATE NULL;

IF COL_LENGTH('dbo.job_education_requirement','qualification_id') IS NULL
  ALTER TABLE dbo.job_education_requirement ADD qualification_id BIGINT NULL;
IF COL_LENGTH('dbo.job_education_requirement','minimum_result') IS NULL
  ALTER TABLE dbo.job_education_requirement ADD minimum_result DECIMAL(5,2) NULL;
IF COL_LENGTH('dbo.job_education_requirement','result_type') IS NULL
  ALTER TABLE dbo.job_education_requirement ADD result_type VARCHAR(20) NULL;

IF COL_LENGTH('dbo.job_experience_requirement','minimum_months') IS NULL
  ALTER TABLE dbo.job_experience_requirement
    ADD minimum_months INT NOT NULL
      CONSTRAINT df_job_experience_requirement_months DEFAULT 0;

IF COL_LENGTH('dbo.job_document_requirement','document_type') IS NULL
  ALTER TABLE dbo.job_document_requirement
    ADD document_type VARCHAR(40) NOT NULL
      CONSTRAINT df_job_document_requirement_type DEFAULT 'OTHER';
IF COL_LENGTH('dbo.job_document_requirement','mandatory') IS NULL
  ALTER TABLE dbo.job_document_requirement
    ADD mandatory BIT NOT NULL
      CONSTRAINT df_job_document_requirement_mandatory DEFAULT 0;

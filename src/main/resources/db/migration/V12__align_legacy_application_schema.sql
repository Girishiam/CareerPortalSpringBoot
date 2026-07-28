/*
  Compatibility for the original database, where job application state was
  named application_status and optimistic/rules-version columns did not exist.
*/

IF COL_LENGTH('dbo.job_application', 'status') IS NULL
BEGIN
  ALTER TABLE dbo.job_application
    ADD status AS application_status;
END;

IF COL_LENGTH('dbo.job_application', 'rules_version') IS NULL
BEGIN
  ALTER TABLE dbo.job_application
    ADD rules_version INT NOT NULL
      CONSTRAINT df_application_rules_version_v12 DEFAULT 1;
END;

IF COL_LENGTH('dbo.job_application', 'version') IS NULL
BEGIN
  ALTER TABLE dbo.job_application
    ADD version BIGINT NOT NULL
      CONSTRAINT df_application_version_v12 DEFAULT 0;
END;

IF COL_LENGTH('dbo.job_posting', 'circular_id') IS NULL
BEGIN
  ALTER TABLE dbo.job_posting ADD circular_id BIGINT NULL;
END;

IF COL_LENGTH('dbo.job_posting', 'age_reference_date') IS NULL
BEGIN
  ALTER TABLE dbo.job_posting ADD age_reference_date DATE NULL;
  EXEC(
    'UPDATE dbo.job_posting
     SET age_reference_date = CAST(application_end_at AS DATE)
     WHERE age_reference_date IS NULL'
  );
  ALTER TABLE dbo.job_posting ALTER COLUMN age_reference_date DATE NOT NULL;
END;

IF COL_LENGTH('dbo.job_posting', 'rules_version') IS NULL
BEGIN
  ALTER TABLE dbo.job_posting
    ADD rules_version INT NOT NULL
      CONSTRAINT df_job_rules_version_v12 DEFAULT 1;
END;

IF COL_LENGTH('dbo.job_posting', 'published_by') IS NULL
BEGIN
  ALTER TABLE dbo.job_posting ADD published_by BIGINT NULL;
END;

IF COL_LENGTH('dbo.job_posting', 'version') IS NULL
BEGIN
  ALTER TABLE dbo.job_posting
    ADD version BIGINT NOT NULL
      CONSTRAINT df_job_version_v12 DEFAULT 0;
END;

IF NOT EXISTS (
  SELECT 1
  FROM sys.indexes
  WHERE object_id = OBJECT_ID(N'dbo.job_application')
    AND is_unique = 1
    AND name = N'uq_job_applicant_v12'
)
AND NOT EXISTS (
  SELECT job_id, applicant_id
  FROM dbo.job_application
  GROUP BY job_id, applicant_id
  HAVING COUNT(*) > 1
)
BEGIN
  CREATE UNIQUE INDEX uq_job_applicant_v12
    ON dbo.job_application (job_id, applicant_id);
END;

IF NOT EXISTS (
  SELECT 1
  FROM sys.indexes
  WHERE object_id = OBJECT_ID(N'dbo.job_application')
    AND is_unique = 1
    AND name = N'uq_tracking_number_v12'
)
AND NOT EXISTS (
  SELECT tracking_number
  FROM dbo.job_application
  WHERE tracking_number IS NOT NULL
  GROUP BY tracking_number
  HAVING COUNT(*) > 1
)
BEGIN
  CREATE UNIQUE INDEX uq_tracking_number_v12
    ON dbo.job_application (tracking_number)
    WHERE tracking_number IS NOT NULL;
END;

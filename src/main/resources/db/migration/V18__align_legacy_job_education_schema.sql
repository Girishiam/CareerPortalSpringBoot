/*
  The original database attached education rows to rule groups. The portal
  attaches them directly to a job. Preserve the old table and install the
  current table shape when upgrading that legacy schema.
*/

IF OBJECT_ID(N'dbo.job_education_requirement', N'U') IS NOT NULL
   AND COL_LENGTH('dbo.job_education_requirement', 'job_id') IS NULL
BEGIN
  EXEC sp_rename
    N'dbo.job_education_requirement',
    N'job_education_requirement_legacy_v18';

  CREATE TABLE dbo.job_education_requirement (
    requirement_id BIGINT IDENTITY PRIMARY KEY,
    job_id BIGINT NOT NULL
      REFERENCES dbo.job_posting (job_id),
    qualification_id INT NOT NULL
      REFERENCES dbo.qualification (qualification_id),
    minimum_result DECIMAL(5, 2) NULL,
    rules_version INT NOT NULL
      CONSTRAINT df_job_education_rules_version_v18 DEFAULT 1,
    result_type VARCHAR(20) NULL
  );
END;

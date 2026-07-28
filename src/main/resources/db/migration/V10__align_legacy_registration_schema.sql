/*
  Align the original CareerPortal database with the registration contract.
  Every change is additive or relaxes a field that is completed later in the
  applicant profile workflow.
*/

IF COL_LENGTH('dbo.role', 'code') IS NULL
BEGIN
  ALTER TABLE dbo.role ADD code VARCHAR(40) NULL;
  EXEC('UPDATE dbo.role SET code = role_code WHERE code IS NULL');
  ALTER TABLE dbo.role ALTER COLUMN code VARCHAR(40) NOT NULL;
  CREATE UNIQUE INDEX uq_role_code_v10 ON dbo.role (code);
END;

IF COL_LENGTH('dbo.role', 'name') IS NULL
BEGIN
  ALTER TABLE dbo.role ADD name NVARCHAR(100) NULL;
  EXEC('UPDATE dbo.role SET name = role_name WHERE name IS NULL');
  ALTER TABLE dbo.role ALTER COLUMN name NVARCHAR(100) NOT NULL;
END;

IF COL_LENGTH('dbo.applicant_profile', 'version') IS NULL
BEGIN
  ALTER TABLE dbo.applicant_profile
    ADD version BIGINT NOT NULL
      CONSTRAINT df_applicant_version_v10 DEFAULT 0;
END;

IF EXISTS (
  SELECT 1
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = 'dbo'
    AND TABLE_NAME = 'applicant_profile'
    AND COLUMN_NAME = 'date_of_birth'
    AND IS_NULLABLE = 'NO'
)
BEGIN
  ALTER TABLE dbo.applicant_profile ALTER COLUMN date_of_birth DATE NULL;
END;

IF EXISTS (
  SELECT 1
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = 'dbo'
    AND TABLE_NAME = 'applicant_profile'
    AND COLUMN_NAME = 'mobile'
    AND IS_NULLABLE = 'NO'
)
BEGIN
  ALTER TABLE dbo.applicant_profile ALTER COLUMN mobile VARCHAR(20) NULL;
END;

IF EXISTS (
  SELECT 1
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = 'dbo'
    AND TABLE_NAME = 'applicant_profile'
    AND COLUMN_NAME = 'email'
    AND IS_NULLABLE = 'NO'
)
BEGIN
  ALTER TABLE dbo.applicant_profile ALTER COLUMN email NVARCHAR(254) NULL;
END;

IF EXISTS (
  SELECT 1
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = 'dbo'
    AND TABLE_NAME = 'applicant_profile'
    AND COLUMN_NAME = 'nationality'
    AND IS_NULLABLE = 'NO'
)
BEGIN
  ALTER TABLE dbo.applicant_profile ALTER COLUMN nationality NVARCHAR(50) NULL;
END;

IF COL_LENGTH('dbo.audit_log', 'actor_user_id') IS NULL
BEGIN
  ALTER TABLE dbo.audit_log ADD actor_user_id BIGINT NULL;
END;

IF COL_LENGTH('dbo.audit_log', 'correlation_id') IS NULL
BEGIN
  ALTER TABLE dbo.audit_log ADD correlation_id UNIQUEIDENTIFIER NULL;
  EXEC('UPDATE dbo.audit_log SET correlation_id = NEWID() WHERE correlation_id IS NULL');
  ALTER TABLE dbo.audit_log ALTER COLUMN correlation_id UNIQUEIDENTIFIER NOT NULL;
END;

IF COL_LENGTH('dbo.audit_log', 'details') IS NULL
BEGIN
  ALTER TABLE dbo.audit_log ADD details NVARCHAR(MAX) NULL;
END;

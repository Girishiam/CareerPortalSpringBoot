/*
  Non-destructive compatibility migration for installations that already had
  CareerPortal security tables before Flyway was introduced.
*/

IF COL_LENGTH('dbo.user_account', 'status') IS NULL
BEGIN
  ALTER TABLE dbo.user_account
    ADD status VARCHAR(30) NOT NULL
      CONSTRAINT df_user_status_v9 DEFAULT 'ACTIVE';
END;

IF COL_LENGTH('dbo.user_account', 'email_verified') IS NULL
BEGIN
  ALTER TABLE dbo.user_account
    ADD email_verified BIT NOT NULL
      CONSTRAINT df_email_verified_v9 DEFAULT 0;
END;

IF COL_LENGTH('dbo.user_account', 'mobile_verified') IS NULL
BEGIN
  ALTER TABLE dbo.user_account
    ADD mobile_verified BIT NOT NULL
      CONSTRAINT df_mobile_verified_v9 DEFAULT 0;
END;

IF COL_LENGTH('dbo.user_account', 'created_at') IS NULL
BEGIN
  ALTER TABLE dbo.user_account
    ADD created_at DATETIME2(3) NOT NULL
      CONSTRAINT df_user_created_v9 DEFAULT SYSUTCDATETIME();
END;

IF COL_LENGTH('dbo.user_account', 'version') IS NULL
BEGIN
  ALTER TABLE dbo.user_account
    ADD version BIGINT NOT NULL
      CONSTRAINT df_user_version_v9 DEFAULT 0;
END;

IF COL_LENGTH('dbo.user_account', 'username') IS NULL
BEGIN
  ALTER TABLE dbo.user_account ADD username VARCHAR(80) NULL;
END;

IF COL_LENGTH('dbo.user_account', 'employee_id') IS NULL
BEGIN
  ALTER TABLE dbo.user_account ADD employee_id VARCHAR(40) NULL;
END;

IF OBJECT_ID('dbo.audit_log', 'U') IS NULL
BEGIN
  CREATE TABLE dbo.audit_log (
    audit_id BIGINT IDENTITY PRIMARY KEY,
    actor_user_id BIGINT NULL,
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    entity_id VARCHAR(100) NULL,
    correlation_id UNIQUEIDENTIFIER NOT NULL,
    details NVARCHAR(MAX) NULL,
    created_at DATETIME2(3) NOT NULL CONSTRAINT df_audit_created_v53 DEFAULT SYSUTCDATETIME()
  );
END;

IF COL_LENGTH('dbo.audit_log', 'actor_name') IS NULL
  ALTER TABLE dbo.audit_log ADD actor_name NVARCHAR(200) NULL;
IF COL_LENGTH('dbo.audit_log', 'actor_email') IS NULL
  ALTER TABLE dbo.audit_log ADD actor_email VARCHAR(254) NULL;
IF COL_LENGTH('dbo.audit_log', 'actor_employee_id') IS NULL
  ALTER TABLE dbo.audit_log ADD actor_employee_id VARCHAR(40) NULL;
IF COL_LENGTH('dbo.audit_log', 'http_method') IS NULL
  ALTER TABLE dbo.audit_log ADD http_method VARCHAR(10) NULL;
IF COL_LENGTH('dbo.audit_log', 'request_path') IS NULL
  ALTER TABLE dbo.audit_log ADD request_path NVARCHAR(500) NULL;
IF COL_LENGTH('dbo.audit_log', 'response_status') IS NULL
  ALTER TABLE dbo.audit_log ADD response_status SMALLINT NULL;
IF COL_LENGTH('dbo.audit_log', 'success') IS NULL
  ALTER TABLE dbo.audit_log ADD success BIT NULL;
IF COL_LENGTH('dbo.audit_log', 'duration_ms') IS NULL
  ALTER TABLE dbo.audit_log ADD duration_ms BIGINT NULL;
IF COL_LENGTH('dbo.audit_log', 'client_ip') IS NULL
  ALTER TABLE dbo.audit_log ADD client_ip VARCHAR(45) NULL;
IF COL_LENGTH('dbo.audit_log', 'forwarded_for') IS NULL
  ALTER TABLE dbo.audit_log ADD forwarded_for NVARCHAR(500) NULL;
IF COL_LENGTH('dbo.audit_log', 'client_host') IS NULL
  ALTER TABLE dbo.audit_log ADD client_host NVARCHAR(255) NULL;
IF COL_LENGTH('dbo.audit_log', 'user_agent') IS NULL
  ALTER TABLE dbo.audit_log ADD user_agent NVARCHAR(1000) NULL;
IF COL_LENGTH('dbo.audit_log', 'browser_name') IS NULL
  ALTER TABLE dbo.audit_log ADD browser_name NVARCHAR(100) NULL;
IF COL_LENGTH('dbo.audit_log', 'operating_system') IS NULL
  ALTER TABLE dbo.audit_log ADD operating_system NVARCHAR(100) NULL;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.audit_log') AND name='ix_audit_actor_created_v53')
  CREATE INDEX ix_audit_actor_created_v53 ON dbo.audit_log(actor_user_id,created_at DESC);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.audit_log') AND name='ix_audit_action_created_v53')
  CREATE INDEX ix_audit_action_created_v53 ON dbo.audit_log(action,created_at DESC);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.audit_log') AND name='ix_audit_created_v53')
  CREATE INDEX ix_audit_created_v53 ON dbo.audit_log(created_at DESC);

IF COL_LENGTH('dbo.audit_log', 'event_category') IS NULL
  ALTER TABLE dbo.audit_log ADD event_category VARCHAR(30) NULL;
IF COL_LENGTH('dbo.audit_log', 'actor_roles') IS NULL
  ALTER TABLE dbo.audit_log ADD actor_roles VARCHAR(300) NULL;
IF COL_LENGTH('dbo.audit_log', 'query_string') IS NULL
  ALTER TABLE dbo.audit_log ADD query_string NVARCHAR(1000) NULL;
IF COL_LENGTH('dbo.audit_log', 'content_type') IS NULL
  ALTER TABLE dbo.audit_log ADD content_type VARCHAR(150) NULL;
IF COL_LENGTH('dbo.audit_log', 'referer') IS NULL
  ALTER TABLE dbo.audit_log ADD referer NVARCHAR(1000) NULL;

-- SQL Server compiles an entire batch before executing ALTER TABLE. Dynamic SQL is required
-- whenever this migration references a column that may have been added earlier in this file.
EXEC(N'
UPDATE dbo.audit_log
   SET event_category = CASE
     WHEN EXISTS (
       SELECT 1 FROM dbo.user_role ur
       JOIN dbo.role r ON r.role_id=ur.role_id
       WHERE ur.user_id=dbo.audit_log.actor_user_id
         AND r.code IN (''HR_ADMIN'',''SYSTEM_ADMIN'')) THEN ''ADMIN''
     WHEN actor_user_id IS NOT NULL THEN ''APPLICANT''
     ELSE ''SYSTEM''
   END
 WHERE event_category IS NULL;
');

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.audit_log') AND name='ix_audit_category_created_v54')
  EXEC(N'CREATE INDEX ix_audit_category_created_v54 ON dbo.audit_log(event_category,created_at DESC)');
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.audit_log') AND name='ix_audit_status_created_v54')
  EXEC(N'CREATE INDEX ix_audit_status_created_v54 ON dbo.audit_log(response_status,created_at DESC)');

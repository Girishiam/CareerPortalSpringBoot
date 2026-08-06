-- Some early installations recorded V5 before these foundational columns were finalized.
-- Repair that schema drift before expanding the workflow; dynamic SQL avoids SQL Server's
-- same-batch column binding problem.
IF COL_LENGTH('dbo.recruitment_stage','stage_code') IS NULL ALTER TABLE dbo.recruitment_stage ADD stage_code VARCHAR(40) NULL;
IF COL_LENGTH('dbo.recruitment_stage','stage_order') IS NULL ALTER TABLE dbo.recruitment_stage ADD stage_order INT NULL;
IF COL_LENGTH('dbo.recruitment_stage','status') IS NULL ALTER TABLE dbo.recruitment_stage ADD status VARCHAR(30) NULL;
IF COL_LENGTH('dbo.shortlist_batch','status') IS NULL ALTER TABLE dbo.shortlist_batch ADD status VARCHAR(30) NULL;
IF COL_LENGTH('dbo.shortlist_batch','created_by') IS NULL ALTER TABLE dbo.shortlist_batch ADD created_by BIGINT NULL;
IF COL_LENGTH('dbo.shortlist_batch','created_at') IS NULL ALTER TABLE dbo.shortlist_batch ADD created_at DATETIME2(3) NOT NULL CONSTRAINT df_shortlist_batch_created_v55 DEFAULT SYSUTCDATETIME();

IF COL_LENGTH('dbo.recruitment_stage','stage_name') IS NULL ALTER TABLE dbo.recruitment_stage ADD stage_name NVARCHAR(120) NULL;
IF COL_LENGTH('dbo.recruitment_stage','stage_type') IS NULL ALTER TABLE dbo.recruitment_stage ADD stage_type VARCHAR(30) NULL;
IF COL_LENGTH('dbo.recruitment_stage','candidate_label') IS NULL ALTER TABLE dbo.recruitment_stage ADD candidate_label NVARCHAR(120) NULL;
IF COL_LENGTH('dbo.recruitment_stage','requires_previous_pass') IS NULL ALTER TABLE dbo.recruitment_stage ADD requires_previous_pass BIT NOT NULL CONSTRAINT df_stage_previous_v55 DEFAULT 0;
IF COL_LENGTH('dbo.recruitment_stage','notification_event_type') IS NULL ALTER TABLE dbo.recruitment_stage ADD notification_event_type VARCHAR(80) NULL;
IF COL_LENGTH('dbo.recruitment_stage','active') IS NULL ALTER TABLE dbo.recruitment_stage ADD active BIT NOT NULL CONSTRAINT df_stage_active_v55 DEFAULT 1;

IF COL_LENGTH('dbo.stage_candidate','decision_status') IS NULL ALTER TABLE dbo.stage_candidate ADD decision_status VARCHAR(30) NOT NULL CONSTRAINT df_stage_candidate_decision_v55 DEFAULT 'SHORTLISTED';
IF COL_LENGTH('dbo.stage_candidate','result_status') IS NULL ALTER TABLE dbo.stage_candidate ADD result_status VARCHAR(20) NOT NULL CONSTRAINT df_stage_candidate_result_v55 DEFAULT 'PENDING';
IF COL_LENGTH('dbo.stage_candidate','selection_source') IS NULL ALTER TABLE dbo.stage_candidate ADD selection_source VARCHAR(20) NOT NULL CONSTRAINT df_stage_candidate_source_v55 DEFAULT 'MANUAL';
IF COL_LENGTH('dbo.stage_candidate','remarks') IS NULL ALTER TABLE dbo.stage_candidate ADD remarks NVARCHAR(1000) NULL;
IF COL_LENGTH('dbo.stage_candidate','selected_by') IS NULL ALTER TABLE dbo.stage_candidate ADD selected_by BIGINT NULL REFERENCES dbo.user_account(user_id);
IF COL_LENGTH('dbo.stage_candidate','selected_at') IS NULL ALTER TABLE dbo.stage_candidate ADD selected_at DATETIME2(3) NOT NULL CONSTRAINT df_stage_candidate_selected_v55 DEFAULT SYSUTCDATETIME();
IF COL_LENGTH('dbo.stage_candidate','notified_at') IS NULL ALTER TABLE dbo.stage_candidate ADD notified_at DATETIME2(3) NULL;

IF COL_LENGTH('dbo.shortlist_batch','total_rows') IS NULL ALTER TABLE dbo.shortlist_batch ADD total_rows INT NULL;
IF COL_LENGTH('dbo.shortlist_batch','selected_rows') IS NULL ALTER TABLE dbo.shortlist_batch ADD selected_rows INT NULL;
IF COL_LENGTH('dbo.shortlist_batch','rejected_rows') IS NULL ALTER TABLE dbo.shortlist_batch ADD rejected_rows INT NULL;
IF COL_LENGTH('dbo.shortlist_batch','error_rows') IS NULL ALTER TABLE dbo.shortlist_batch ADD error_rows INT NULL;
IF COL_LENGTH('dbo.shortlist_batch','original_filename') IS NULL ALTER TABLE dbo.shortlist_batch ADD original_filename NVARCHAR(255) NULL;
IF COL_LENGTH('dbo.shortlist_batch','summary') IS NULL ALTER TABLE dbo.shortlist_batch ADD summary NVARCHAR(2000) NULL;

IF COL_LENGTH('dbo.recruitment_exam','stage_id') IS NULL ALTER TABLE dbo.recruitment_exam ADD stage_id BIGINT NULL REFERENCES dbo.recruitment_stage(stage_id);

IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID('dbo.recruitment_exam') AND name='ck_recruitment_exam_type_v52')
  ALTER TABLE dbo.recruitment_exam DROP CONSTRAINT ck_recruitment_exam_type_v52;
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID('dbo.recruitment_exam') AND name='ck_recruitment_exam_type_v55')
  ALTER TABLE dbo.recruitment_exam ADD CONSTRAINT ck_recruitment_exam_type_v55 CHECK (exam_type IN ('MCQ','WRITTEN','COMBINED','VIVA'));

EXEC(N'UPDATE dbo.recruitment_stage SET stage_code=COALESCE(stage_code,CONCAT(''STAGE_'',stage_id)),stage_order=COALESCE(stage_order,CONVERT(INT,stage_id)*10),status=COALESCE(status,''DRAFT''),stage_name=COALESCE(stage_name,REPLACE(stage_code,''_'','' '')),stage_type=COALESCE(stage_type,stage_code),candidate_label=COALESCE(candidate_label,stage_name,stage_code),notification_event_type=COALESCE(notification_event_type,''CANDIDATE_SHORTLISTED'')');

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.recruitment_stage') AND name='ux_recruitment_stage_job_code_v55')
  EXEC(N'CREATE UNIQUE INDEX ux_recruitment_stage_job_code_v55 ON dbo.recruitment_stage(job_id,stage_code) WHERE stage_code IS NOT NULL');

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.stage_candidate') AND name='ix_stage_candidate_decision_v55')
  EXEC(N'CREATE INDEX ix_stage_candidate_decision_v55 ON dbo.stage_candidate(stage_id,decision_status,result_status) INCLUDE(application_id)');
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.shortlist_batch') AND name='ix_shortlist_batch_stage_v55')
  CREATE INDEX ix_shortlist_batch_stage_v55 ON dbo.shortlist_batch(stage_id,created_at DESC);

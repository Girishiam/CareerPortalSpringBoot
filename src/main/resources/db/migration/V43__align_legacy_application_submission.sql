/*
  Make the legacy application tables accept the canonical submission payload.
  Older installations retain presentation-only snapshot columns that the
  current application no longer writes.
*/
IF COL_LENGTH('dbo.eligibility_evaluation','failures_json') IS NULL
  ALTER TABLE dbo.eligibility_evaluation ADD failures_json NVARCHAR(MAX) NULL;

IF COL_LENGTH('dbo.eligibility_evaluation','overall_result') IS NOT NULL
   AND NOT EXISTS (
     SELECT 1
       FROM sys.default_constraints defaults
       JOIN sys.columns columns
         ON columns.object_id=defaults.parent_object_id
        AND columns.column_id=defaults.parent_column_id
      WHERE defaults.parent_object_id=OBJECT_ID('dbo.eligibility_evaluation')
        AND columns.name='overall_result'
   )
  ALTER TABLE dbo.eligibility_evaluation
    ADD CONSTRAINT df_eligibility_evaluation_overall_result_v43
      DEFAULT 'NOT_EVALUATED' FOR overall_result;

IF COL_LENGTH('dbo.eligibility_evaluation','rule_version') IS NOT NULL
   AND NOT EXISTS (
     SELECT 1
       FROM sys.default_constraints defaults
       JOIN sys.columns columns
         ON columns.object_id=defaults.parent_object_id
        AND columns.column_id=defaults.parent_column_id
      WHERE defaults.parent_object_id=OBJECT_ID('dbo.eligibility_evaluation')
        AND columns.name='rule_version'
   )
  ALTER TABLE dbo.eligibility_evaluation
    ADD CONSTRAINT df_eligibility_evaluation_rule_version_v43
      DEFAULT 1 FOR rule_version;

IF COL_LENGTH('dbo.application_profile_snapshot','cv_number') IS NULL
  ALTER TABLE dbo.application_profile_snapshot ADD cv_number VARCHAR(30) NULL;

IF COL_LENGTH('dbo.application_profile_snapshot','mobile') IS NOT NULL
  ALTER TABLE dbo.application_profile_snapshot ALTER COLUMN mobile VARCHAR(20) NULL;
IF COL_LENGTH('dbo.application_profile_snapshot','email') IS NOT NULL
  ALTER TABLE dbo.application_profile_snapshot ALTER COLUMN email NVARCHAR(254) NULL;

IF COL_LENGTH('dbo.application_education_snapshot','education_level_name') IS NOT NULL
  ALTER TABLE dbo.application_education_snapshot
    ALTER COLUMN education_level_name NVARCHAR(100) NULL;
IF COL_LENGTH('dbo.application_education_snapshot','qualification_name') IS NOT NULL
  ALTER TABLE dbo.application_education_snapshot
    ALTER COLUMN qualification_name NVARCHAR(150) NULL;

IF COL_LENGTH('dbo.application_experience_snapshot','organization') IS NOT NULL
  ALTER TABLE dbo.application_experience_snapshot
    ALTER COLUMN organization NVARCHAR(250) NULL;
IF COL_LENGTH('dbo.application_experience_snapshot','is_current') IS NOT NULL
  ALTER TABLE dbo.application_experience_snapshot ALTER COLUMN is_current BIT NULL;

IF COL_LENGTH('dbo.application_document','validation_status') IS NOT NULL
  ALTER TABLE dbo.application_document
    ALTER COLUMN validation_status VARCHAR(20) NULL;
IF COL_LENGTH('dbo.application_document','copied_at') IS NOT NULL
  ALTER TABLE dbo.application_document ALTER COLUMN copied_at DATETIME2(3) NULL;

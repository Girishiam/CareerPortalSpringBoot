IF COL_LENGTH('dbo.job_education_requirement','match_mode') IS NULL
  ALTER TABLE dbo.job_education_requirement
    ADD match_mode VARCHAR(30) NOT NULL
      CONSTRAINT df_job_education_match_mode_v47 DEFAULT 'EXACT';

IF OBJECT_ID('dbo.ck_job_education_match_mode_v47','C') IS NULL
  EXEC(N'
    ALTER TABLE dbo.job_education_requirement
      ADD CONSTRAINT ck_job_education_match_mode_v47 CHECK (
        match_mode IN (''EXACT'',''EQUIVALENT_LEVEL'',''MINIMUM_LEVEL'')
      );
  ');

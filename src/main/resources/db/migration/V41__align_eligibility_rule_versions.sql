IF COL_LENGTH('dbo.job_age_policy','rules_version') IS NULL
  ALTER TABLE dbo.job_age_policy
    ADD rules_version INT NOT NULL
      CONSTRAINT df_job_age_policy_rules_version DEFAULT 1;

IF COL_LENGTH('dbo.job_education_requirement','rules_version') IS NULL
  ALTER TABLE dbo.job_education_requirement
    ADD rules_version INT NOT NULL
      CONSTRAINT df_job_education_requirement_rules_version DEFAULT 1;

IF COL_LENGTH('dbo.job_experience_requirement','rules_version') IS NULL
  ALTER TABLE dbo.job_experience_requirement
    ADD rules_version INT NOT NULL
      CONSTRAINT df_job_experience_requirement_rules_version DEFAULT 1;

IF COL_LENGTH('dbo.job_document_requirement','rules_version') IS NULL
  ALTER TABLE dbo.job_document_requirement
    ADD rules_version INT NOT NULL
      CONSTRAINT df_job_document_requirement_rules_version DEFAULT 1;

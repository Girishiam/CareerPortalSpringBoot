/*
  Compatibility for databases created from the original schema, whose job
  eligibility tables predate per-rule versioning.
*/

IF COL_LENGTH('dbo.job_age_policy', 'rules_version') IS NULL
BEGIN
  ALTER TABLE dbo.job_age_policy
    ADD rules_version INT NOT NULL
      CONSTRAINT df_job_age_policy_rules_version_v16 DEFAULT 1;
END;

IF COL_LENGTH('dbo.job_education_requirement', 'rules_version') IS NULL
BEGIN
  ALTER TABLE dbo.job_education_requirement
    ADD rules_version INT NOT NULL
      CONSTRAINT df_job_education_rules_version_v16 DEFAULT 1;
END;

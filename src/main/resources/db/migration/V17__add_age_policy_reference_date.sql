IF COL_LENGTH('dbo.job_age_policy', 'age_reference_date') IS NULL
BEGIN
  ALTER TABLE dbo.job_age_policy ADD age_reference_date DATE NULL;
END;

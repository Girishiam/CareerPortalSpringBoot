IF COL_LENGTH('dbo.job_posting', 'is_archived') IS NULL
BEGIN
  ALTER TABLE dbo.job_posting
    ADD is_archived BIT NOT NULL
      CONSTRAINT df_job_posting_archived_v36 DEFAULT (0) WITH VALUES;
END;

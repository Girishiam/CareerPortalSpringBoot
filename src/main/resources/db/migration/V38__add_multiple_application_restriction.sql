IF COL_LENGTH('dbo.job_posting','multiple_application_restricted') IS NULL
BEGIN
  ALTER TABLE dbo.job_posting
    ADD multiple_application_restricted BIT NOT NULL
      CONSTRAINT df_job_posting_multiple_application_restricted DEFAULT 0;

  EXEC(N'
    UPDATE dbo.job_posting
       SET multiple_application_restricted =
         CASE WHEN allow_other_post_application=0 THEN 1 ELSE 0 END;
  ');
END;

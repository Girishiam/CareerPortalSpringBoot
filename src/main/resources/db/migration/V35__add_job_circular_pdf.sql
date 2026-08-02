IF OBJECT_ID('dbo.job_circular_pdf', 'U') IS NULL
BEGIN
  CREATE TABLE dbo.job_circular_pdf (
    job_id BIGINT PRIMARY KEY REFERENCES dbo.job_posting(job_id),
    original_name NVARCHAR(260) NOT NULL,
    media_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    file_content VARBINARY(MAX) NOT NULL,
    uploaded_at DATETIME2(3) NOT NULL
      CONSTRAINT df_job_circular_pdf_uploaded_v35 DEFAULT SYSUTCDATETIME()
  );
END;

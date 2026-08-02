IF OBJECT_ID('dbo.recruitment_circular', 'U') IS NULL
BEGIN
  CREATE TABLE dbo.recruitment_circular (
    circular_id BIGINT IDENTITY PRIMARY KEY,
    circular_code VARCHAR(40) NOT NULL UNIQUE,
    title NVARCHAR(200) NOT NULL
  );
END;

IF COL_LENGTH('dbo.job_posting', 'circular_id') IS NOT NULL
AND EXISTS (SELECT 1 FROM dbo.job_posting WHERE circular_id IS NOT NULL)
BEGIN
  IF COLUMNPROPERTY(
       OBJECT_ID('dbo.recruitment_circular'),
       'circular_id',
       'IsIdentity'
     ) = 1
  BEGIN
    SET IDENTITY_INSERT dbo.recruitment_circular ON;

    INSERT dbo.recruitment_circular(circular_id,circular_code,title)
    SELECT DISTINCT
           job.circular_id,
           CONCAT('LEGACY-', job.circular_id),
           CONCAT(N'Legacy circular ', job.circular_id)
      FROM dbo.job_posting job
     WHERE job.circular_id IS NOT NULL
       AND NOT EXISTS (
         SELECT 1
           FROM dbo.recruitment_circular circular
          WHERE circular.circular_id=job.circular_id
       );

    SET IDENTITY_INSERT dbo.recruitment_circular OFF;
  END
  ELSE
  BEGIN
    INSERT dbo.recruitment_circular(circular_id,circular_code,title)
    SELECT DISTINCT
           job.circular_id,
           CONCAT('LEGACY-', job.circular_id),
           CONCAT(N'Legacy circular ', job.circular_id)
      FROM dbo.job_posting job
     WHERE job.circular_id IS NOT NULL
       AND NOT EXISTS (
         SELECT 1
           FROM dbo.recruitment_circular circular
          WHERE circular.circular_id=job.circular_id
       );
  END;
END;

IF OBJECT_ID('dbo.circular_application_policy', 'U') IS NULL
BEGIN
  CREATE TABLE dbo.circular_application_policy (
    circular_id BIGINT PRIMARY KEY
      REFERENCES dbo.recruitment_circular(circular_id),
    max_applications_per_applicant INT NOT NULL,
    CONSTRAINT ck_circular_limit_v34
      CHECK (max_applications_per_applicant > 0)
  );
END;

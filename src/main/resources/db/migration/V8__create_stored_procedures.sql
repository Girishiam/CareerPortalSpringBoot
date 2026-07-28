CREATE OR ALTER PROCEDURE dbo.usp_SubmitJobApplication
  @ApplicationId BIGINT,
  @ApplicantId BIGINT
AS
BEGIN
  SET NOCOUNT ON;
  SET XACT_ABORT ON;

  DECLARE
    @jobId BIGINT,
    @status VARCHAR(30),
    @endAt DATETIME2(3),
    @jobCode VARCHAR(40),
    @eligible BIT;

  SELECT
    @jobId = application.job_id,
    @status = application.status,
    @endAt = job.application_end_at,
    @jobCode = job.job_code
  FROM dbo.job_application AS application WITH (UPDLOCK, HOLDLOCK)
  INNER JOIN dbo.job_posting AS job
    ON job.job_id = application.job_id
  WHERE
    application.application_id = @ApplicationId
    AND application.applicant_id = @ApplicantId;

  IF @jobId IS NULL
    THROW 50021, 'Draft application not found.', 1;

  IF @status <> 'DRAFT'
    THROW 50022, 'Application is not a draft.', 1;

  IF SYSUTCDATETIME() >= @endAt
    THROW 50024, 'The application deadline has passed.', 1;

  SELECT TOP (1)
    @eligible = eligible
  FROM dbo.eligibility_evaluation
  WHERE application_id = @ApplicationId
  ORDER BY evaluation_id DESC;

  IF ISNULL(@eligible, 0) = 0
    THROW 50025, 'Applicant is not eligible.', 1;

  IF NOT EXISTS (
    SELECT 1
    FROM dbo.application_profile_snapshot
    WHERE application_id = @ApplicationId
  )
    THROW 50026, 'Application snapshot is missing.', 1;

  DECLARE @tracking VARCHAR(60) = CONCAT(
    'UTB-',
    @jobCode,
    '-',
    FORMAT(NEXT VALUE FOR dbo.tracking_number_seq, '000000')
  );

  UPDATE dbo.job_application
  SET
    status = 'SUBMITTED',
    tracking_number = @tracking,
    eligibility_status = 'ELIGIBLE',
    submitted_at = SYSUTCDATETIME(),
    version = version + 1
  WHERE application_id = @ApplicationId;

  INSERT INTO dbo.application_status_history (
    application_id,
    from_status,
    to_status,
    changed_by
  )
  SELECT
    @ApplicationId,
    'DRAFT',
    'SUBMITTED',
    user_id
  FROM dbo.applicant_profile
  WHERE applicant_id = @ApplicantId;

  SELECT
    application_id,
    tracking_number,
    status,
    eligibility_status,
    submitted_at
  FROM dbo.job_application
  WHERE application_id = @ApplicationId;
END;

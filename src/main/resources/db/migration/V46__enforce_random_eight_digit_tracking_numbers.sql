DECLARE @applicationId BIGINT;
DECLARE application_cursor CURSOR LOCAL FAST_FORWARD FOR
  SELECT application_id
    FROM dbo.job_application
   WHERE tracking_number IS NOT NULL
     AND (
       LEN(tracking_number)<>8
       OR tracking_number LIKE '%[^0-9]%'
       OR tracking_number LIKE '0%'
     );

OPEN application_cursor;
FETCH NEXT FROM application_cursor INTO @applicationId;
WHILE @@FETCH_STATUS=0
BEGIN
  DECLARE @tracking VARCHAR(8);
  WHILE 1=1
  BEGIN
    SET @tracking=CONVERT(VARCHAR(8),10000000 + ABS(CHECKSUM(NEWID())) % 90000000);
    IF NOT EXISTS (
      SELECT 1 FROM dbo.job_application WHERE tracking_number=@tracking
    ) BREAK;
  END;

  UPDATE dbo.job_application
     SET tracking_number=@tracking
   WHERE application_id=@applicationId;

  FETCH NEXT FROM application_cursor INTO @applicationId;
END;
CLOSE application_cursor;
DEALLOCATE application_cursor;

IF OBJECT_ID('dbo.ck_job_application_tracking_v46','C') IS NULL
  ALTER TABLE dbo.job_application
    ADD CONSTRAINT ck_job_application_tracking_v46 CHECK (
      tracking_number IS NULL
      OR (
        LEN(tracking_number)=8
        AND tracking_number NOT LIKE '%[^0-9]%'
        AND tracking_number NOT LIKE '0%'
      )
    );

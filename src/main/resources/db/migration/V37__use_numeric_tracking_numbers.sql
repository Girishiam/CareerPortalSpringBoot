DECLARE @nextTracking BIGINT =
  ISNULL((
    SELECT MAX(TRY_CONVERT(BIGINT,tracking_number))
      FROM dbo.job_application
  ),0) + 1;
DECLARE @sequenceNext BIGINT =
  ISNULL((
    SELECT CONVERT(BIGINT,current_value) + CONVERT(BIGINT,increment)
      FROM sys.sequences
     WHERE object_id=OBJECT_ID('dbo.tracking_number_seq')
  ),1);
IF @sequenceNext > @nextTracking SET @nextTracking=@sequenceNext;
DECLARE @sequenceSql NVARCHAR(300);
IF OBJECT_ID('dbo.tracking_number_seq','SO') IS NULL
  SET @sequenceSql =
    N'CREATE SEQUENCE dbo.tracking_number_seq AS BIGINT START WITH '
    + CONVERT(NVARCHAR(30),@nextTracking)
    + N' INCREMENT BY 1 CACHE 100';
ELSE
  SET @sequenceSql =
    N'ALTER SEQUENCE dbo.tracking_number_seq RESTART WITH '
    + CONVERT(NVARCHAR(30),@nextTracking);
EXEC sys.sp_executesql @sequenceSql;

EXEC(N'
UPDATE dbo.job_application
   SET tracking_number =
       CONVERT(VARCHAR(60), NEXT VALUE FOR dbo.tracking_number_seq)
 WHERE tracking_number IS NOT NULL
   AND tracking_number LIKE ''%[^0-9]%'';
');

IF COL_LENGTH('dbo.eligibility_evaluation','eligible') IS NULL
  ALTER TABLE dbo.eligibility_evaluation
    ADD eligible BIT NOT NULL
      CONSTRAINT df_eligibility_evaluation_eligible DEFAULT 0;

IF COL_LENGTH('dbo.application_status_history','from_status') IS NULL
  ALTER TABLE dbo.application_status_history ADD from_status VARCHAR(30) NULL;
IF COL_LENGTH('dbo.application_status_history','to_status') IS NULL
  ALTER TABLE dbo.application_status_history
    ADD to_status VARCHAR(30) NOT NULL
      CONSTRAINT df_application_status_history_to_status DEFAULT 'SUBMITTED';
IF COL_LENGTH('dbo.application_status_history','changed_by') IS NULL
  ALTER TABLE dbo.application_status_history
    ADD changed_by BIGINT NOT NULL
      CONSTRAINT df_application_status_history_changed_by DEFAULT 0;

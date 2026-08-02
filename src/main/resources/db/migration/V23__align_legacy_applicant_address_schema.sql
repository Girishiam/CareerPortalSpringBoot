/*
 * Legacy databases store the primary address text in address_line_1, while
 * the current API uses address_line.
 */

IF OBJECT_ID('dbo.applicant_address', 'U') IS NOT NULL
BEGIN
  IF COL_LENGTH('dbo.applicant_address', 'address_line') IS NULL
    ALTER TABLE dbo.applicant_address ADD address_line NVARCHAR(300) NULL;

  IF COL_LENGTH('dbo.applicant_address', 'address_line_1') IS NOT NULL
  BEGIN
    IF COL_LENGTH('dbo.applicant_address', 'address_line_2') IS NOT NULL
      EXEC(N'
        UPDATE dbo.applicant_address
           SET address_line = COALESCE(
             address_line,
             NULLIF(LTRIM(RTRIM(address_line_1)), N''''),
             NULLIF(LTRIM(RTRIM(address_line_2)), N''''),
             N''Address not provided''
           );
      ');
    ELSE
      EXEC(N'
        UPDATE dbo.applicant_address
           SET address_line = COALESCE(
             address_line,
             NULLIF(LTRIM(RTRIM(address_line_1)), N''''),
             N''Address not provided''
           );
      ');
  END
  ELSE
    EXEC(N'
      UPDATE dbo.applicant_address
         SET address_line = N''Address not provided''
       WHERE address_line IS NULL;
    ');

  ALTER TABLE dbo.applicant_address
    ALTER COLUMN address_line NVARCHAR(300) NOT NULL;

  /*
   * The current service writes address_line. Retain legacy text but do not
   * require address_line_1 for newly inserted records.
   */
  IF COL_LENGTH('dbo.applicant_address', 'address_line_1') IS NOT NULL
    ALTER TABLE dbo.applicant_address
      ALTER COLUMN address_line_1 NVARCHAR(250) NULL;

  IF NOT EXISTS (
    SELECT 1
      FROM sys.indexes
     WHERE object_id = OBJECT_ID('dbo.applicant_address')
       AND is_unique = 1
       AND name = 'uq_applicant_address'
  )
  AND NOT EXISTS (
    SELECT applicant_id, address_type
      FROM dbo.applicant_address
     GROUP BY applicant_id, address_type
    HAVING COUNT(*) > 1
  )
    CREATE UNIQUE INDEX uq_applicant_address
      ON dbo.applicant_address (applicant_id, address_type);
END;

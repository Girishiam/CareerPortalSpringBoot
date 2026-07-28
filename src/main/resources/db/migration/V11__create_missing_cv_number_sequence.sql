IF NOT EXISTS (
  SELECT 1
  FROM sys.sequences
  WHERE object_id = OBJECT_ID(N'dbo.cv_number_seq')
)
BEGIN
  DECLARE @next_cv_number BIGINT =
    COALESCE(
      (
        SELECT MAX(
          TRY_CONVERT(
            BIGINT,
            RIGHT(cv_number, CHARINDEX('-', REVERSE(cv_number)) - 1)
          )
        )
        FROM dbo.applicant_profile
        WHERE cv_number LIKE '%-%'
      ),
      0
    ) + 1;

  DECLARE @create_sequence NVARCHAR(500) =
    N'CREATE SEQUENCE dbo.cv_number_seq AS BIGINT START WITH '
    + CONVERT(NVARCHAR(30), @next_cv_number)
    + N' INCREMENT BY 1';

  EXEC sys.sp_executesql @create_sequence;
END;

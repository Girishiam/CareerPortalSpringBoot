-- Older CareerPortal databases can contain selection_status as a required column.
-- The current shortlist workflow supersedes it with decision_status, so new rows do
-- not populate the legacy column. Keep any historical values, but allow new rows to
-- leave it NULL.
IF COL_LENGTH('dbo.stage_candidate', 'selection_status') IS NOT NULL
BEGIN
  DECLARE @selection_status_type NVARCHAR(500);

  SELECT @selection_status_type =
    CASE
      WHEN type.name IN ('varchar', 'char', 'varbinary', 'binary')
        THEN type.name + '(' + CASE WHEN column_info.max_length = -1 THEN 'MAX' ELSE CONVERT(VARCHAR(10), column_info.max_length) END + ')'
      WHEN type.name IN ('nvarchar', 'nchar')
        THEN type.name + '(' + CASE WHEN column_info.max_length = -1 THEN 'MAX' ELSE CONVERT(VARCHAR(10), column_info.max_length / 2) END + ')'
      WHEN type.name IN ('decimal', 'numeric')
        THEN type.name + '(' + CONVERT(VARCHAR(10), column_info.precision) + ',' + CONVERT(VARCHAR(10), column_info.scale) + ')'
      WHEN type.name IN ('datetime2', 'datetimeoffset', 'time')
        THEN type.name + '(' + CONVERT(VARCHAR(10), column_info.scale) + ')'
      ELSE type.name
    END
    + CASE
        WHEN column_info.collation_name IS NOT NULL
          THEN ' COLLATE ' + QUOTENAME(column_info.collation_name)
        ELSE ''
      END
  FROM sys.columns column_info
  JOIN sys.types type ON type.user_type_id = column_info.user_type_id
  WHERE column_info.object_id = OBJECT_ID('dbo.stage_candidate')
    AND column_info.name = 'selection_status';

  IF @selection_status_type IS NOT NULL
    EXEC(N'ALTER TABLE dbo.stage_candidate ALTER COLUMN selection_status ' + @selection_status_type + N' NULL');
END;

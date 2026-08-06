-- V59 existed in an earlier form on some development databases. Apply the
-- compatibility change under a new immutable version so those databases are
-- repaired as well as fresh installations.
IF EXISTS (
  SELECT 1
  FROM sys.columns
  WHERE object_id = OBJECT_ID('dbo.stage_candidate')
    AND name = 'selection_status'
    AND is_nullable = 0
)
BEGIN
  DECLARE @selection_status_type_v60 NVARCHAR(500);

  SELECT @selection_status_type_v60 =
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
          THEN ' COLLATE ' + column_info.collation_name
        ELSE ''
      END
  FROM sys.columns column_info
  JOIN sys.types type ON type.user_type_id = column_info.user_type_id
  WHERE column_info.object_id = OBJECT_ID('dbo.stage_candidate')
    AND column_info.name = 'selection_status';

  EXEC(N'ALTER TABLE dbo.stage_candidate ALTER COLUMN selection_status ' + @selection_status_type_v60 + N' NULL');
END;

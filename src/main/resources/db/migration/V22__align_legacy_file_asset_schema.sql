/*
 * Older CareerPortal databases used different file_asset column names.
 * Retain those columns for compatibility and add the names used by the
 * current document service.
 */

IF OBJECT_ID('dbo.file_asset', 'U') IS NOT NULL
BEGIN
  IF COL_LENGTH('dbo.file_asset', 'media_type') IS NULL
    ALTER TABLE dbo.file_asset ADD media_type VARCHAR(100) NULL;

  IF COL_LENGTH('dbo.file_asset', 'size_bytes') IS NULL
    ALTER TABLE dbo.file_asset ADD size_bytes BIGINT NULL;

  IF COL_LENGTH('dbo.file_asset', 'sha256') IS NULL
    ALTER TABLE dbo.file_asset ADD sha256 CHAR(64) NULL;

  IF COL_LENGTH('dbo.file_asset', 'width') IS NULL
    ALTER TABLE dbo.file_asset ADD width INT NULL;

  IF COL_LENGTH('dbo.file_asset', 'height') IS NULL
    ALTER TABLE dbo.file_asset ADD height INT NULL;

  IF COL_LENGTH('dbo.file_asset', 'created_at') IS NULL
    ALTER TABLE dbo.file_asset ADD created_at DATETIME2(3) NULL;

  EXEC(N'
    UPDATE dbo.file_asset
       SET media_type = COALESCE(media_type, mime_type, ''application/octet-stream''),
           size_bytes = COALESCE(size_bytes, file_size_bytes, 0),
           sha256 = COALESCE(
             sha256,
             sha256_hash,
             CONVERT(CHAR(64), HASHBYTES(''SHA2_256'', CONVERT(VARCHAR(30), file_id)), 2)
           ),
           width = COALESCE(width, image_width),
           height = COALESCE(height, image_height),
           created_at = COALESCE(created_at, uploaded_at, SYSUTCDATETIME());
  ');

  ALTER TABLE dbo.file_asset ALTER COLUMN media_type VARCHAR(100) NOT NULL;
  ALTER TABLE dbo.file_asset ALTER COLUMN size_bytes BIGINT NOT NULL;
  ALTER TABLE dbo.file_asset ALTER COLUMN sha256 CHAR(64) NOT NULL;
  ALTER TABLE dbo.file_asset ALTER COLUMN created_at DATETIME2(3) NOT NULL;

  IF NOT EXISTS (
    SELECT 1
      FROM sys.default_constraints dc
      JOIN sys.columns c
        ON c.object_id = dc.parent_object_id
       AND c.column_id = dc.parent_column_id
     WHERE dc.parent_object_id = OBJECT_ID('dbo.file_asset')
       AND c.name = 'created_at'
  )
    ALTER TABLE dbo.file_asset
      ADD CONSTRAINT df_file_asset_created_at_v22
      DEFAULT SYSUTCDATETIME() FOR created_at;

  /*
   * These required legacy fields are no longer populated by the current
   * service. Making them nullable keeps old values while permitting modern
   * inserts.
   */
  IF COL_LENGTH('dbo.file_asset', 'mime_type') IS NOT NULL
    ALTER TABLE dbo.file_asset ALTER COLUMN mime_type VARCHAR(100) NULL;

  IF COL_LENGTH('dbo.file_asset', 'file_size_bytes') IS NOT NULL
    ALTER TABLE dbo.file_asset ALTER COLUMN file_size_bytes BIGINT NULL;

  IF COL_LENGTH('dbo.file_asset', 'sha256_hash') IS NOT NULL
    ALTER TABLE dbo.file_asset ALTER COLUMN sha256_hash CHAR(64) NULL;

  IF COL_LENGTH('dbo.file_asset', 'uploaded_by') IS NOT NULL
    ALTER TABLE dbo.file_asset ALTER COLUMN uploaded_by BIGINT NULL;
END;

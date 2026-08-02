/*
  Qualifications offered by the applicant education form. Existing rows are
  updated by code, while missing rows receive IDs above the current maximum so
  this also works with legacy databases.
*/

IF COL_LENGTH('dbo.qualification', 'education_level_id') IS NOT NULL
AND EXISTS (
  SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA='dbo' AND TABLE_NAME='qualification'
    AND COLUMN_NAME='education_level_id' AND IS_NULLABLE='NO'
)
BEGIN
  ALTER TABLE dbo.qualification ALTER COLUMN education_level_id INT NULL;
END;

IF COL_LENGTH('dbo.qualification', 'qualification_name') IS NOT NULL
AND EXISTS (
  SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA='dbo' AND TABLE_NAME='qualification'
    AND COLUMN_NAME='qualification_name' AND IS_NULLABLE='NO'
)
BEGIN
  ALTER TABLE dbo.qualification ALTER COLUMN qualification_name NVARCHAR(150) NULL;
END;

IF COL_LENGTH('dbo.qualification', 'qualification_level') IS NOT NULL
AND EXISTS (
  SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA='dbo' AND TABLE_NAME='qualification'
    AND COLUMN_NAME='qualification_level' AND IS_NULLABLE='NO'
)
BEGIN
  ALTER TABLE dbo.qualification ALTER COLUMN qualification_level INT NULL;
END;
GO

IF COL_LENGTH('dbo.qualification', 'code') IS NULL
BEGIN
  EXEC('ALTER TABLE dbo.qualification ADD code VARCHAR(40) NULL');
  IF COL_LENGTH('dbo.qualification', 'qualification_code') IS NOT NULL
    EXEC('UPDATE dbo.qualification SET code=qualification_code');
  ELSE
    EXEC('
      UPDATE dbo.qualification
      SET code =
        CASE
          WHEN UPPER(name) IN (''SSC'', ''S.S.C.'', ''SECONDARY SCHOOL CERTIFICATE'') THEN ''SSC''
          WHEN UPPER(name) IN (''HSC'', ''H.S.C.'', ''HIGHER SECONDARY CERTIFICATE'') THEN ''HSC''
          WHEN UPPER(name) IN (''BACHELOR'', ''BACHELOR DEGREE'') THEN ''BACHELOR''
          ELSE CONCAT(''LEGACY_'', qualification_id)
        END
    ');
  EXEC('ALTER TABLE dbo.qualification ALTER COLUMN code VARCHAR(40) NOT NULL');
END;

IF COL_LENGTH('dbo.qualification', 'qualification_code') IS NULL
BEGIN
  ALTER TABLE dbo.qualification ADD qualification_code VARCHAR(40) NULL;
END;
GO

IF COL_LENGTH('dbo.qualification', 'display_order') IS NULL
BEGIN
  ALTER TABLE dbo.qualification ADD display_order INT NULL;
END;
GO

DECLARE @qualifications TABLE (
  code VARCHAR(40) NOT NULL,
  name NVARCHAR(150) NOT NULL,
  level_rank INT NOT NULL,
  display_order INT NOT NULL
);

INSERT INTO @qualifications (code, name, level_rank, display_order)
VALUES
  ('SSC',       'S.S.C.',  10,  1),
  ('DAKHIL',    'Dakhil',  10,  2),
  ('O_LEVEL',   'O Level', 10,  3),
  ('HSC',       'H.S.C.',  12,  4),
  ('ALIM',      'Alim',    12,  5),
  ('A_LEVEL',   'A Level', 12,  6),
  ('HONORS',    'Honors',  16,  7),
  ('BSC',       'B.Sc.',   16,  8),
  ('BA',        'B.A.',    16,  9),
  ('BSS',       'B.S.S.',  16, 10),
  ('FAZIL',     'Fazil',   16, 11),
  ('MASTERS',   'Masters', 18, 12),
  ('KAMIL',     'Kamil',   18, 13),
  ('OTHERS',    'Others',   0, 14),
  ('BCOM',      'B.Com',   16, 15),
  ('BBA',       'BBA',     16, 16),
  ('MBA',       'MBA',     18, 17),
  ('MBM',       'MBM',     18, 18);

UPDATE target
SET
  target.name = source.name,
  target.level_rank = source.level_rank,
  target.display_order = source.display_order,
  target.qualification_code = source.code
FROM dbo.qualification target
JOIN @qualifications source ON source.code = target.code;

DECLARE @maximum_id BIGINT =
  ISNULL((SELECT MAX(qualification_id) FROM dbo.qualification), 0);

IF COLUMNPROPERTY(OBJECT_ID('dbo.qualification'), 'qualification_id', 'IsIdentity') = 1
  SET IDENTITY_INSERT dbo.qualification ON;

INSERT INTO dbo.qualification (
  qualification_id,
  code,
  qualification_code,
  name,
  level_rank,
  display_order
)
SELECT
  @maximum_id + ROW_NUMBER() OVER (ORDER BY source.display_order),
  source.code,
  source.code,
  source.name,
  source.level_rank,
  source.display_order
FROM @qualifications source
WHERE NOT EXISTS (
  SELECT 1
  FROM dbo.qualification target
  WHERE target.code = source.code
);

IF COLUMNPROPERTY(OBJECT_ID('dbo.qualification'), 'qualification_id', 'IsIdentity') = 1
  SET IDENTITY_INSERT dbo.qualification OFF;

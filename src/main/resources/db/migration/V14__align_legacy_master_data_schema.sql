/*
  Normalize master-data display columns in databases that existed before
  Flyway. Legacy installations use names such as department_name while the
  application contract consistently exposes the display value as "name".
*/

IF OBJECT_ID('dbo.department', 'U') IS NOT NULL
AND COL_LENGTH('dbo.department', 'name') IS NULL
BEGIN
  ALTER TABLE dbo.department ADD name NVARCHAR(120) NULL;
  IF COL_LENGTH('dbo.department', 'department_name') IS NOT NULL
    EXEC('UPDATE dbo.department SET name=department_name WHERE name IS NULL');
  ELSE IF COL_LENGTH('dbo.department', 'code') IS NOT NULL
    EXEC('UPDATE dbo.department SET name=code WHERE name IS NULL');
  EXEC('UPDATE dbo.department SET name=''Unnamed department'' WHERE name IS NULL');
  ALTER TABLE dbo.department ALTER COLUMN name NVARCHAR(120) NOT NULL;
END;

IF OBJECT_ID('dbo.qualification', 'U') IS NOT NULL
AND COL_LENGTH('dbo.qualification', 'name') IS NULL
BEGIN
  ALTER TABLE dbo.qualification ADD name NVARCHAR(150) NULL;
  IF COL_LENGTH('dbo.qualification', 'qualification_name') IS NOT NULL
    EXEC('UPDATE dbo.qualification SET name=qualification_name WHERE name IS NULL');
  ELSE IF COL_LENGTH('dbo.qualification', 'code') IS NOT NULL
    EXEC('UPDATE dbo.qualification SET name=code WHERE name IS NULL');
  EXEC('UPDATE dbo.qualification SET name=''Unnamed qualification'' WHERE name IS NULL');
  ALTER TABLE dbo.qualification ALTER COLUMN name NVARCHAR(150) NOT NULL;
END;

IF OBJECT_ID('dbo.qualification', 'U') IS NOT NULL
AND COL_LENGTH('dbo.qualification', 'level_rank') IS NULL
BEGIN
  ALTER TABLE dbo.qualification ADD level_rank INT NULL;
  IF COL_LENGTH('dbo.qualification', 'qualification_level') IS NOT NULL
    EXEC('UPDATE dbo.qualification SET level_rank=qualification_level WHERE level_rank IS NULL');
  EXEC('UPDATE dbo.qualification SET level_rank=0 WHERE level_rank IS NULL');
  ALTER TABLE dbo.qualification ALTER COLUMN level_rank INT NOT NULL;
END;

IF OBJECT_ID('dbo.division', 'U') IS NOT NULL
AND COL_LENGTH('dbo.division', 'name') IS NULL
BEGIN
  ALTER TABLE dbo.division ADD name NVARCHAR(100) NULL;
  IF COL_LENGTH('dbo.division', 'division_name') IS NOT NULL
    EXEC('UPDATE dbo.division SET name=division_name WHERE name IS NULL');
  EXEC('UPDATE dbo.division SET name=''Unnamed division'' WHERE name IS NULL');
  ALTER TABLE dbo.division ALTER COLUMN name NVARCHAR(100) NOT NULL;
END;

IF OBJECT_ID('dbo.district', 'U') IS NOT NULL
AND COL_LENGTH('dbo.district', 'name') IS NULL
BEGIN
  ALTER TABLE dbo.district ADD name NVARCHAR(100) NULL;
  IF COL_LENGTH('dbo.district', 'district_name') IS NOT NULL
    EXEC('UPDATE dbo.district SET name=district_name WHERE name IS NULL');
  EXEC('UPDATE dbo.district SET name=''Unnamed district'' WHERE name IS NULL');
  ALTER TABLE dbo.district ALTER COLUMN name NVARCHAR(100) NOT NULL;
END;

IF OBJECT_ID('dbo.upazila', 'U') IS NOT NULL
AND COL_LENGTH('dbo.upazila', 'name') IS NULL
BEGIN
  ALTER TABLE dbo.upazila ADD name NVARCHAR(100) NULL;
  IF COL_LENGTH('dbo.upazila', 'upazila_name') IS NOT NULL
    EXEC('UPDATE dbo.upazila SET name=upazila_name WHERE name IS NULL');
  EXEC('UPDATE dbo.upazila SET name=''Unnamed upazila'' WHERE name IS NULL');
  ALTER TABLE dbo.upazila ALTER COLUMN name NVARCHAR(100) NOT NULL;
END;

IF OBJECT_ID('dbo.subject', 'U') IS NOT NULL
AND COL_LENGTH('dbo.subject', 'name') IS NULL
BEGIN
  ALTER TABLE dbo.subject ADD name NVARCHAR(150) NULL;
  IF COL_LENGTH('dbo.subject', 'subject_name') IS NOT NULL
    EXEC('UPDATE dbo.subject SET name=subject_name WHERE name IS NULL');
  EXEC('UPDATE dbo.subject SET name=''Unnamed subject'' WHERE name IS NULL');
  ALTER TABLE dbo.subject ALTER COLUMN name NVARCHAR(150) NOT NULL;
END;

IF OBJECT_ID('dbo.institution', 'U') IS NOT NULL
AND COL_LENGTH('dbo.institution', 'name') IS NULL
BEGIN
  ALTER TABLE dbo.institution ADD name NVARCHAR(200) NULL;
  IF COL_LENGTH('dbo.institution', 'institution_name') IS NOT NULL
    EXEC('UPDATE dbo.institution SET name=institution_name WHERE name IS NULL');
  EXEC('UPDATE dbo.institution SET name=''Unnamed institution'' WHERE name IS NULL');
  ALTER TABLE dbo.institution ALTER COLUMN name NVARCHAR(200) NOT NULL;
END;

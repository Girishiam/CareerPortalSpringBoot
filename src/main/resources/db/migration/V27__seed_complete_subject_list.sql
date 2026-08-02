/*
  Subject/major master data used by applicant education records. School-level
  group filtering is handled by the master-data API using these same rows.
*/

IF COL_LENGTH('dbo.subject', 'display_order') IS NULL
BEGIN
  ALTER TABLE dbo.subject ADD display_order INT NULL;
END;
GO

DECLARE @subjects TABLE (
  name NVARCHAR(150) NOT NULL,
  display_order INT NOT NULL
);

INSERT INTO @subjects (name, display_order)
VALUES
  ('Science', 1),
  ('Arts', 2),
  ('Commerce', 3),
  ('Humanities', 4),
  ('Business Studies', 5),
  ('Finance & Banking', 6),
  ('Economics', 7),
  ('Accounting', 8),
  ('Management', 9),
  ('Banking', 10),
  ('Finance', 11),
  ('Marketing', 12),
  ('English', 13),
  ('AIS', 14),
  ('Pass Course', 15),
  ('Computer Science', 16),
  ('Computer Engineering', 17),
  ('Architecture', 18),
  ('Agriculture', 19),
  ('Computer Science & Engineering', 20),
  ('Mathematics', 21),
  ('Statistics', 22),
  ('Law', 23),
  ('MBM', 24),
  ('Health Economics', 25),
  ('MBA', 26),
  ('Textile Engineering', 27),
  ('World Religions and Culture', 28),
  ('International Business', 29),
  ('Information Technology', 30),
  ('Information and Communication Technology', 31),
  ('Industrial and Production Engineering (IPE)', 32),
  ('Geography and Environment', 33),
  ('Genetic Engineering and Biotechnology', 34),
  ('Biotechnology & Genetic Engineering', 35),
  ('Arabic', 36),
  ('Archeology', 37),
  ('Civil Engineering', 38),
  ('Electrical and Electronic Engineering', 39),
  ('Mechanical Engineering', 40),
  ('Pharmacy', 41),
  ('Medicine', 42),
  ('Public Administration', 43),
  ('Sociology', 44),
  ('Political Science', 45),
  ('Others', 98),
  ('N/A', 99);

UPDATE target
SET target.display_order = source.display_order
FROM dbo.subject target
JOIN @subjects source ON LOWER(source.name) = LOWER(target.name);

DECLARE @maximum_id BIGINT =
  ISNULL((SELECT MAX(subject_id) FROM dbo.subject), 0);

IF COLUMNPROPERTY(OBJECT_ID('dbo.subject'), 'subject_id', 'IsIdentity') = 1
  SET IDENTITY_INSERT dbo.subject ON;

INSERT INTO dbo.subject (subject_id, name, display_order)
SELECT
  @maximum_id + ROW_NUMBER() OVER (ORDER BY source.display_order),
  source.name,
  source.display_order
FROM @subjects source
WHERE NOT EXISTS (
  SELECT 1
  FROM dbo.subject target
  WHERE LOWER(target.name) = LOWER(source.name)
);

IF COLUMNPROPERTY(OBJECT_ID('dbo.subject'), 'subject_id', 'IsIdentity') = 1
  SET IDENTITY_INSERT dbo.subject OFF;

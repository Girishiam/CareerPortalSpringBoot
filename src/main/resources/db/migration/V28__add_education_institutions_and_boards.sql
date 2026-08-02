IF COL_LENGTH('dbo.institution', 'display_order') IS NULL
BEGIN
  ALTER TABLE dbo.institution ADD display_order INT NULL;
END;

IF COL_LENGTH('dbo.institution', 'institution_name') IS NULL
BEGIN
  ALTER TABLE dbo.institution ADD institution_name NVARCHAR(200) NULL;
END;
GO

DECLARE @institutions TABLE (
  name NVARCHAR(200) NOT NULL,
  display_order INT NOT NULL
);

INSERT INTO @institutions (name, display_order)
VALUES
  ('Patuakhali Science and Technology University', 1),
  ('Premier University of Bangladesh', 2),
  ('Presidency University', 3),
  ('Prime University', 4),
  ('Primeasia University', 5),
  ('Rajshahi University', 6),
  ('Rajshahi University of Engineering & Technology', 7),
  ('Royal University of Dhaka', 8),
  ('Shahjalal University of Science & Technology', 9),
  ('Shanto-Mariam University of Creative Technology', 10),
  ('Sher-e-Bangla Agricultural University', 11),
  ('South East University of Bangladesh', 12),
  ('Southern University of Bangladesh', 13),
  ('Stamford University Bangladesh', 14),
  ('State University of Bangladesh', 15),
  ('Sylhet Agricultural University', 16),
  ('Sylhet International University', 17),
  ('The Millennium University', 18),
  ('The People''s University of Bangladesh', 19),
  ('United International University', 20),
  ('University of Asia Pacific', 21),
  ('University of Chittagong', 22),
  ('University of Development Alternative', 23),
  ('University of Information Technology & Sciences', 24),
  ('University of Liberal Arts Bangladesh', 25),
  ('University of Science & Technology Chittagong', 26),
  ('University of South Asia', 27),
  ('Uttara University', 28),
  ('Victoria University of Bangladesh', 29),
  ('World University of Bangladesh', 30),
  ('National University', 31),
  ('Bangladesh University of Professionals', 32),
  ('Bangladesh University of Textiles', 33),
  ('Barisal University', 34),
  ('Begum Rokeya University', 35),
  ('Comilla University', 36),
  ('Foreign University', 37),
  ('Bangabandhu Sheikh Mujibur Rahman Science & Technology University', 38),
  ('Chittagong Veterinary and Animal Sciences University', 39),
  ('Jessore University of Science & Technology', 40),
  ('Pabna University of Science and Technology', 41),
  ('Islamic University of Technology, Gazipur', 42),
  ('Asian University for Women', 43),
  ('ASA University Bangladesh', 44),
  ('Atish Dipankar University of Science & Technology', 45),
  ('Ahsanullah University of Science and Technology', 46),
  ('American International University-Bangladesh', 47),
  ('Asian University of Bangladesh', 48),
  ('Bangabandhu Sheikh Mujibur Rahman Maritime University', 49),
  ('BGC Trust University Bangladesh', 50),
  ('Bangabandhu Sheikh Mujib Medical University', 51),
  ('Bangabandhu Sheikh Mujibur Rahman Agricultural University', 52),
  ('Bangladesh Agricultural University, Mymensingh', 53),
  ('Bangladesh Islami University', 54),
  ('Bangladesh Open University', 55),
  ('Bangladesh University', 56),
  ('Bangladesh University of Business & Technology', 57),
  ('Bangladesh University of Engineering & Technology', 58),
  ('BRAC University', 59),
  ('Chittagong University of Engineering & Technology', 60),
  ('City University', 61),
  ('Daffodil International University', 62),
  ('Darul Ihsan University', 63),
  ('Dhaka International University', 64),
  ('Dhaka University', 65),
  ('Dhaka University of Engineering & Technology', 66),
  ('East Delta University, Chittagong', 67),
  ('East West University', 68),
  ('Eastern University of Bangladesh', 69),
  ('Gono Bishwabidyalay', 70),
  ('Green University of Bangladesh', 71),
  ('Hajee Mohammad Danesh Science & Technology University', 72),
  ('IBAIS University', 73),
  ('Independent University, Bangladesh', 74),
  ('International Islamic University Chittagong', 75),
  ('International University of Business Agriculture & Technology', 76),
  ('Islamic University', 77),
  ('Jagannath University', 78),
  ('Jahangirnagar University', 79),
  ('Jatiya Kabi Kazi Nazrul Islam University', 80),
  ('Khulna University', 81),
  ('Khulna University of Engineering and Technology', 82),
  ('Leading University', 83),
  ('Manarat International University', 84),
  ('Mawlana Bhashani Science & Technology University', 85),
  ('Metropolitan University', 86),
  ('Noakhali Science & Technology University', 87),
  ('North South University', 88),
  ('Northern University of Bangladesh', 89),
  ('Barisal Board', 90),
  ('Chittagong Board', 91),
  ('Comilla Board', 92),
  ('Dhaka Board', 93),
  ('Dinajpur Board', 94),
  ('Jessore Board', 95),
  ('Rajshahi Board', 96),
  ('Sylhet Board', 97),
  ('Madrasah Board, Dhaka', 98),
  ('Technical Board, Dhaka', 99),
  ('Others', 100);

UPDATE target
SET
  target.display_order = source.display_order,
  target.institution_name = source.name
FROM dbo.institution target
JOIN @institutions source ON LOWER(source.name) = LOWER(target.name);

DECLARE @maximum_institution_id BIGINT =
  ISNULL((SELECT MAX(institution_id) FROM dbo.institution), 0);

IF COLUMNPROPERTY(OBJECT_ID('dbo.institution'), 'institution_id', 'IsIdentity') = 1
  SET IDENTITY_INSERT dbo.institution ON;

INSERT INTO dbo.institution (
  institution_id,
  name,
  institution_name,
  display_order
)
SELECT
  @maximum_institution_id + ROW_NUMBER() OVER (ORDER BY source.display_order),
  source.name,
  source.name,
  source.display_order
FROM @institutions source
WHERE NOT EXISTS (
  SELECT 1 FROM dbo.institution target
  WHERE LOWER(target.name) = LOWER(source.name)
);

IF COLUMNPROPERTY(OBJECT_ID('dbo.institution'), 'institution_id', 'IsIdentity') = 1
  SET IDENTITY_INSERT dbo.institution OFF;

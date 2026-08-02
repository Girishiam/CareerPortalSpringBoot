IF EXISTS (
  SELECT 1 FROM sys.indexes
  WHERE object_id=OBJECT_ID('dbo.applicant_profile')
    AND name='uq_applicant_normalized_nid_v48'
)
  DROP INDEX uq_applicant_normalized_nid_v48 ON dbo.applicant_profile;

IF EXISTS (
  SELECT 1 FROM sys.indexes
  WHERE object_id=OBJECT_ID('dbo.applicant_profile')
    AND name='uq_applicant_normalized_passport_v48'
)
  DROP INDEX uq_applicant_normalized_passport_v48 ON dbo.applicant_profile;

IF COL_LENGTH('dbo.applicant_profile','normalized_nid') IS NOT NULL
  ALTER TABLE dbo.applicant_profile DROP COLUMN normalized_nid;
IF COL_LENGTH('dbo.applicant_profile','normalized_passport') IS NOT NULL
  ALTER TABLE dbo.applicant_profile DROP COLUMN normalized_passport;

ALTER TABLE dbo.applicant_profile ADD normalized_nid AS
  CONVERT(VARCHAR(40),
    CASE
      WHEN NULLIF(REPLACE(REPLACE(REPLACE(LTRIM(RTRIM(nid_number)),'-',''),' ',''),'.',''),'') IS NULL
        THEN CONCAT('#',CONVERT(VARCHAR(30),applicant_id))
      ELSE UPPER(REPLACE(REPLACE(REPLACE(LTRIM(RTRIM(nid_number)),'-',''),' ',''),'.',''))
    END)
  PERSISTED;

ALTER TABLE dbo.applicant_profile ADD normalized_passport AS
  CONVERT(VARCHAR(40),
    CASE
      WHEN NULLIF(REPLACE(REPLACE(LTRIM(RTRIM(passport_number)),'-',''),' ',''),'') IS NULL
        THEN CONCAT('#',CONVERT(VARCHAR(30),applicant_id))
      ELSE UPPER(REPLACE(REPLACE(LTRIM(RTRIM(passport_number)),'-',''),' ',''))
    END)
  PERSISTED;

CREATE UNIQUE INDEX uq_applicant_normalized_nid_v49
  ON dbo.applicant_profile(normalized_nid);
CREATE UNIQUE INDEX uq_applicant_normalized_passport_v49
  ON dbo.applicant_profile(normalized_passport);

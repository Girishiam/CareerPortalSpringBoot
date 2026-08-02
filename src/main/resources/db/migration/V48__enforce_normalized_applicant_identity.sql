IF COL_LENGTH('dbo.applicant_profile','normalized_nid') IS NULL
  ALTER TABLE dbo.applicant_profile ADD normalized_nid AS
    CASE
      WHEN NULLIF(REPLACE(REPLACE(REPLACE(LTRIM(RTRIM(nid_number)),'-',''),' ',''),'.',''),'') IS NULL
        THEN CONCAT('#',CONVERT(VARCHAR(30),applicant_id))
      ELSE UPPER(REPLACE(REPLACE(REPLACE(LTRIM(RTRIM(nid_number)),'-',''),' ',''),'.',''))
    END
    PERSISTED;

IF COL_LENGTH('dbo.applicant_profile','normalized_passport') IS NULL
  ALTER TABLE dbo.applicant_profile ADD normalized_passport AS
    CASE
      WHEN NULLIF(REPLACE(REPLACE(LTRIM(RTRIM(passport_number)),'-',''),' ',''),'') IS NULL
        THEN CONCAT('#',CONVERT(VARCHAR(30),applicant_id))
      ELSE UPPER(REPLACE(REPLACE(LTRIM(RTRIM(passport_number)),'-',''),' ',''))
    END
    PERSISTED;

IF NOT EXISTS (
  SELECT 1 FROM sys.indexes
  WHERE object_id=OBJECT_ID('dbo.applicant_profile')
    AND name='uq_applicant_normalized_nid_v48'
)
  EXEC(N'
    CREATE UNIQUE INDEX uq_applicant_normalized_nid_v48
      ON dbo.applicant_profile(normalized_nid);
  ');

IF NOT EXISTS (
  SELECT 1 FROM sys.indexes
  WHERE object_id=OBJECT_ID('dbo.applicant_profile')
    AND name='uq_applicant_normalized_passport_v48'
)
  EXEC(N'
    CREATE UNIQUE INDEX uq_applicant_normalized_passport_v48
      ON dbo.applicant_profile(normalized_passport);
  ');

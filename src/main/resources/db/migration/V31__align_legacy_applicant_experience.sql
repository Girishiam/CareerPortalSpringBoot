/*
  Legacy databases call the employer column "organization". The current API
  consistently uses employer_name.
*/

IF COL_LENGTH('dbo.applicant_experience', 'employer_name') IS NULL
BEGIN
  ALTER TABLE dbo.applicant_experience ADD employer_name NVARCHAR(200) NULL;
END;
GO

IF COL_LENGTH('dbo.applicant_experience', 'organization') IS NOT NULL
BEGIN
  EXEC('
    UPDATE dbo.applicant_experience
    SET employer_name=organization
    WHERE employer_name IS NULL
  ');

  IF EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA='dbo' AND TABLE_NAME='applicant_experience'
      AND COLUMN_NAME='organization' AND IS_NULLABLE='NO'
  )
    ALTER TABLE dbo.applicant_experience
      ALTER COLUMN organization NVARCHAR(200) NULL;
END;

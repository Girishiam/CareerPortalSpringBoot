IF COL_LENGTH('dbo.applicant_profile', 'email') IS NULL
BEGIN
  ALTER TABLE dbo.applicant_profile ADD email NVARCHAR(254) NULL;
END;

IF COL_LENGTH('dbo.applicant_profile', 'mobile') IS NULL
BEGIN
  ALTER TABLE dbo.applicant_profile ADD mobile VARCHAR(20) NULL;
END;

IF COL_LENGTH('dbo.applicant_profile', 'passport_number') IS NULL
BEGIN
  ALTER TABLE dbo.applicant_profile ADD passport_number VARCHAR(30) NULL;
END;

UPDATE profile
SET
  email = COALESCE(profile.email, account.email),
  mobile = COALESCE(profile.mobile, account.mobile)
FROM dbo.applicant_profile profile
JOIN dbo.user_account account ON account.user_id = profile.user_id
WHERE profile.email IS NULL OR profile.mobile IS NULL;

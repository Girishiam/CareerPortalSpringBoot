UPDATE profile
   SET profile.email=COALESCE(profile.email,account.email),
       profile.mobile=COALESCE(profile.mobile,account.mobile)
  FROM dbo.applicant_profile profile
  JOIN dbo.user_account account ON account.user_id=profile.user_id
 WHERE profile.email IS NULL
    OR profile.mobile IS NULL;

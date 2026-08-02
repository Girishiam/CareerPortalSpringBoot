/*
  Five upazilas approved by NICAR on 7 May 2026, as reported by the official
  Bangladesh Sangbad Sangstha (BSS).
*/

MERGE dbo.upazila AS target
USING (VALUES
  (4001, N'Rajshahi',   N'Bogra',        N'Mokamtala'),
  (4002, N'Chattogram', N'Cox''s Bazar', N'Matamuhuri'),
  (4003, N'Rangpur',    N'Thakurgaon',   N'Ruhea'),
  (4004, N'Rangpur',    N'Thakurgaon',   N'Bhully'),
  (4005, N'Chattogram', N'Lakshmipur',   N'Chandrogonj')
) AS source(upazila_id, division_name, district_name, name)
ON target.name = source.name
 AND target.district_id = (
   SELECT d.district_id
   FROM dbo.district d
   JOIN dbo.division v ON v.division_id=d.division_id
   WHERE d.name=source.district_name AND v.name=source.division_name
 )
WHEN NOT MATCHED THEN
  INSERT (upazila_id, district_id, name)
  VALUES (source.upazila_id,
          (SELECT d.district_id
           FROM dbo.district d
           JOIN dbo.division v ON v.division_id=d.division_id
           WHERE d.name=source.district_name AND v.name=source.division_name),
          source.name);

/*
 * Legacy applicant_address foreign keys referenced the generic geo_location
 * table. Current address selectors use the normalized division, district and
 * upazila tables.
 */

IF OBJECT_ID('dbo.applicant_address', 'U') IS NOT NULL
BEGIN
  DECLARE @dropForeignKeys NVARCHAR(MAX) = N'';

  SELECT @dropForeignKeys = @dropForeignKeys
      + N'ALTER TABLE dbo.applicant_address DROP CONSTRAINT '
      + QUOTENAME(fk.name) + N';'
    FROM sys.foreign_keys fk
   WHERE fk.parent_object_id = OBJECT_ID('dbo.applicant_address')
     AND fk.referenced_object_id = OBJECT_ID('dbo.geo_location');

  IF @dropForeignKeys <> N''
    EXEC sp_executesql @dropForeignKeys;

  /*
   * Translate any existing legacy IDs by their full hierarchy. Unmatched IDs
   * become null instead of blocking migration; users can then select a current
   * location and save the address again.
   */
  IF OBJECT_ID('dbo.geo_location', 'U') IS NOT NULL
  BEGIN
    EXEC(N'
      UPDATE a
         SET upazila_id = u.upazila_id
        FROM dbo.applicant_address a
        LEFT JOIN dbo.geo_location gu ON gu.location_id = a.upazila_id
        LEFT JOIN dbo.geo_location gd ON gd.location_id = gu.parent_location_id
        LEFT JOIN dbo.geo_location gv ON gv.location_id = gd.parent_location_id
        LEFT JOIN dbo.division v ON v.name = gv.location_name
        LEFT JOIN dbo.district d
          ON d.division_id = v.division_id
         AND d.name = gd.location_name
        LEFT JOIN dbo.upazila u
          ON u.district_id = d.district_id
         AND u.name = gu.location_name;

      UPDATE a
         SET district_id = d.district_id
        FROM dbo.applicant_address a
        LEFT JOIN dbo.geo_location gd ON gd.location_id = a.district_id
        LEFT JOIN dbo.geo_location gv ON gv.location_id = gd.parent_location_id
        LEFT JOIN dbo.division v ON v.name = gv.location_name
        LEFT JOIN dbo.district d
          ON d.division_id = v.division_id
         AND d.name = gd.location_name;

      UPDATE a
         SET division_id = v.division_id
        FROM dbo.applicant_address a
        LEFT JOIN dbo.geo_location gv ON gv.location_id = a.division_id
        LEFT JOIN dbo.division v ON v.name = gv.location_name;
    ');
  END;

  ALTER TABLE dbo.applicant_address ALTER COLUMN division_id BIGINT NULL;
  ALTER TABLE dbo.applicant_address ALTER COLUMN district_id BIGINT NULL;
  ALTER TABLE dbo.applicant_address ALTER COLUMN upazila_id BIGINT NULL;

  IF NOT EXISTS (
    SELECT 1
      FROM sys.foreign_key_columns fkc
     WHERE fkc.parent_object_id = OBJECT_ID('dbo.applicant_address')
       AND COL_NAME(fkc.parent_object_id, fkc.parent_column_id) = 'division_id'
  )
    ALTER TABLE dbo.applicant_address WITH CHECK
      ADD CONSTRAINT fk_applicant_address_division_v24
      FOREIGN KEY (division_id) REFERENCES dbo.division (division_id);

  IF NOT EXISTS (
    SELECT 1
      FROM sys.foreign_key_columns fkc
     WHERE fkc.parent_object_id = OBJECT_ID('dbo.applicant_address')
       AND COL_NAME(fkc.parent_object_id, fkc.parent_column_id) = 'district_id'
  )
    ALTER TABLE dbo.applicant_address WITH CHECK
      ADD CONSTRAINT fk_applicant_address_district_v24
      FOREIGN KEY (district_id) REFERENCES dbo.district (district_id);

  IF NOT EXISTS (
    SELECT 1
      FROM sys.foreign_key_columns fkc
     WHERE fkc.parent_object_id = OBJECT_ID('dbo.applicant_address')
       AND COL_NAME(fkc.parent_object_id, fkc.parent_column_id) = 'upazila_id'
  )
    ALTER TABLE dbo.applicant_address WITH CHECK
      ADD CONSTRAINT fk_applicant_address_upazila_v24
      FOREIGN KEY (upazila_id) REFERENCES dbo.upazila (upazila_id);

  IF EXISTS (
    SELECT 1
      FROM sys.indexes
     WHERE object_id = OBJECT_ID('dbo.applicant_address')
       AND name = 'uq_applicant_address'
  )
  AND EXISTS (
    SELECT 1
      FROM sys.indexes
     WHERE object_id = OBJECT_ID('dbo.applicant_address')
       AND name <> 'uq_applicant_address'
       AND is_unique = 1
       AND is_primary_key = 0
  )
    DROP INDEX uq_applicant_address ON dbo.applicant_address;
END;

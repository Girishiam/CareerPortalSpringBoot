/*
 * Repair installations that predate the Flyway baseline and therefore have
 * only part of the applicant/master-data schema.
 */

IF OBJECT_ID('dbo.subject', 'U') IS NULL
BEGIN
  CREATE TABLE dbo.subject (
    subject_id BIGINT NOT NULL PRIMARY KEY,
    name NVARCHAR(150) NOT NULL
  );
END;

IF NOT EXISTS (SELECT 1 FROM dbo.subject WHERE subject_id = 21)
AND NOT EXISTS (SELECT 1 FROM dbo.subject WHERE name = N'General')
BEGIN
  INSERT dbo.subject (subject_id, name) VALUES (21, N'General');
END;

IF OBJECT_ID('dbo.applicant_document', 'U') IS NOT NULL
AND COL_LENGTH('dbo.applicant_document', 'active') IS NULL
BEGIN
  ALTER TABLE dbo.applicant_document
    ADD active BIT NOT NULL
      CONSTRAINT df_applicant_document_active_v21 DEFAULT (1) WITH VALUES;
END;

/*
 * A legacy table may contain several documents of the same type. Keep the
 * newest row active before restoring the invariant expected by the service.
 * Dynamic SQL avoids SQL Server compiling references to a newly added column
 * before the ALTER TABLE above has run.
 */
IF OBJECT_ID('dbo.applicant_document', 'U') IS NOT NULL
AND COL_LENGTH('dbo.applicant_document', 'active') IS NOT NULL
BEGIN
  EXEC(N'
    ;WITH ranked_documents AS (
      SELECT
        applicant_document_id,
        ROW_NUMBER() OVER (
          PARTITION BY applicant_id, document_type
          ORDER BY applicant_document_id DESC
        ) AS row_number
      FROM dbo.applicant_document
      WHERE active = 1
    )
    UPDATE d
       SET active = 0
      FROM dbo.applicant_document d
      JOIN ranked_documents r
        ON r.applicant_document_id = d.applicant_document_id
     WHERE r.row_number > 1;
  ');

  IF NOT EXISTS (
    SELECT 1
      FROM sys.indexes
     WHERE object_id = OBJECT_ID('dbo.applicant_document')
       AND name = 'uq_active_applicant_document'
  )
  BEGIN
    EXEC(N'
      CREATE UNIQUE INDEX uq_active_applicant_document
        ON dbo.applicant_document (applicant_id, document_type)
        WHERE active = 1;
    ');
  END;
END;

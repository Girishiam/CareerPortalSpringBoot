/*
  The current subject API reads dbo.subject. Legacy databases linked applicant
  education to dbo.academic_subject instead, causing every newly selected
  subject ID to fail its foreign-key check.
*/

IF EXISTS (
  SELECT 1
  FROM sys.foreign_keys
  WHERE parent_object_id = OBJECT_ID('dbo.applicant_education')
    AND name = 'FK_applicant_education_subject'
)
BEGIN
  ALTER TABLE dbo.applicant_education
    DROP CONSTRAINT FK_applicant_education_subject;
END;

ALTER TABLE dbo.applicant_education
  ALTER COLUMN subject_id BIGINT NULL;

/*
  Preserve legacy references before adding the normalized constraint. Rows
  whose IDs only existed in academic_subject are retained as unlinked subjects
  rather than preventing migration.
*/
UPDATE education
SET subject_id = NULL
FROM dbo.applicant_education education
LEFT JOIN dbo.subject subject ON subject.subject_id = education.subject_id
WHERE education.subject_id IS NOT NULL
  AND subject.subject_id IS NULL;

ALTER TABLE dbo.applicant_education WITH CHECK
  ADD CONSTRAINT FK_applicant_education_subject
  FOREIGN KEY (subject_id) REFERENCES dbo.subject (subject_id);

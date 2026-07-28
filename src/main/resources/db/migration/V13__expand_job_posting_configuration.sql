ALTER TABLE dbo.job_posting ADD
  designation NVARCHAR(150) NULL,
  experience_type VARCHAR(40) NULL,
  job_location NVARCHAR(200) NULL,
  salary_details NVARCHAR(200) NULL,
  publication_channel VARCHAR(40) NULL,
  job_context NVARCHAR(MAX) NULL,
  additional_requirements NVARCHAR(MAX) NULL,
  compensation_benefits NVARCHAR(MAX) NULL,
  apply_page_header NVARCHAR(300) NULL,
  specific_education_required BIT NOT NULL DEFAULT 0,
  existing_employee_eligible BIT NOT NULL DEFAULT 0,
  external_applicant_eligible BIT NOT NULL DEFAULT 1,
  maximum_designation NVARCHAR(150) NULL,
  spouse_data_required BIT NOT NULL DEFAULT 0,
  mobile_required BIT NOT NULL DEFAULT 1,
  email_required BIT NOT NULL DEFAULT 1,
  relative_declaration_required BIT NOT NULL DEFAULT 0,
  allow_other_post_application BIT NOT NULL DEFAULT 1,
  cover_letter_cv_required BIT NOT NULL DEFAULT 0,
  circular_letter_name NVARCHAR(260) NULL;

IF COL_LENGTH('dbo.job_education_requirement', 'result_type') IS NULL
  ALTER TABLE dbo.job_education_requirement ADD result_type VARCHAR(20) NULL;

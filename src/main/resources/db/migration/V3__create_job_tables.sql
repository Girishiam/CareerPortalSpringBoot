CREATE TABLE dbo.department (
  department_id BIGINT PRIMARY KEY,
  code VARCHAR(30) NOT NULL UNIQUE,
  name NVARCHAR(120) NOT NULL
);

CREATE TABLE dbo.recruitment_circular (
  circular_id BIGINT IDENTITY PRIMARY KEY,
  circular_code VARCHAR(40) NOT NULL UNIQUE,
  title NVARCHAR(200) NOT NULL
);

CREATE TABLE dbo.circular_application_policy (
  circular_id BIGINT PRIMARY KEY REFERENCES dbo.recruitment_circular (circular_id),
  max_applications_per_applicant INT NOT NULL,
  CONSTRAINT ck_circular_limit CHECK (max_applications_per_applicant > 0)
);

CREATE TABLE dbo.job_posting (
  job_id BIGINT IDENTITY PRIMARY KEY,
  circular_id BIGINT NULL REFERENCES dbo.recruitment_circular (circular_id),
  job_code VARCHAR(40) NOT NULL UNIQUE,
  job_title NVARCHAR(200) NOT NULL,
  department_id BIGINT NOT NULL REFERENCES dbo.department (department_id),
  job_description NVARCHAR(MAX) NOT NULL,
  responsibilities NVARCHAR(MAX) NULL,
  vacancy_count INT NOT NULL,
  employment_type VARCHAR(40) NOT NULL,
  application_start_at DATETIME2(3) NOT NULL,
  application_end_at DATETIME2(3) NOT NULL,
  age_reference_date DATE NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
  rules_version INT NOT NULL DEFAULT 1,
  created_by BIGINT NOT NULL REFERENCES dbo.user_account (user_id),
  published_by BIGINT NULL REFERENCES dbo.user_account (user_id),
  published_at DATETIME2(3) NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ck_job_window CHECK (application_end_at > application_start_at)
);

CREATE TABLE dbo.job_age_policy (
  age_policy_id BIGINT IDENTITY PRIMARY KEY,
  job_id BIGINT NOT NULL REFERENCES dbo.job_posting (job_id),
  applicant_category VARCHAR(50) NOT NULL,
  minimum_age INT NULL,
  maximum_age INT NOT NULL,
  rules_version INT NOT NULL
);

CREATE TABLE dbo.job_education_requirement (
  requirement_id BIGINT IDENTITY PRIMARY KEY,
  job_id BIGINT NOT NULL REFERENCES dbo.job_posting (job_id),
  qualification_id BIGINT NOT NULL REFERENCES dbo.qualification (qualification_id),
  minimum_result DECIMAL(5, 2) NULL,
  rules_version INT NOT NULL
);

CREATE TABLE dbo.job_experience_requirement (
  requirement_id BIGINT IDENTITY PRIMARY KEY,
  job_id BIGINT NOT NULL REFERENCES dbo.job_posting (job_id),
  minimum_months INT NOT NULL,
  rules_version INT NOT NULL
);

CREATE TABLE dbo.job_document_requirement (
  requirement_id BIGINT IDENTITY PRIMARY KEY,
  job_id BIGINT NOT NULL REFERENCES dbo.job_posting (job_id),
  document_type VARCHAR(40) NOT NULL,
  mandatory BIT NOT NULL,
  rules_version INT NOT NULL
);

CREATE SEQUENCE dbo.tracking_number_seq
  AS BIGINT
  START WITH 1
  INCREMENT BY 1
  CACHE 100;

CREATE TABLE dbo.job_application (
  application_id BIGINT IDENTITY PRIMARY KEY,
  job_id BIGINT NOT NULL REFERENCES dbo.job_posting (job_id),
  applicant_id BIGINT NOT NULL REFERENCES dbo.applicant_profile (applicant_id),
  status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
  rules_version INT NOT NULL,
  tracking_number VARCHAR(60) NULL,
  eligibility_status VARCHAR(30) NULL,
  submitted_at DATETIME2(3) NULL,
  created_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uq_job_applicant UNIQUE (job_id, applicant_id)
);

CREATE UNIQUE INDEX uq_tracking_number ON dbo.job_application (tracking_number)
WHERE
  tracking_number IS NOT NULL;

CREATE TABLE dbo.eligibility_evaluation (
  evaluation_id BIGINT IDENTITY PRIMARY KEY,
  application_id BIGINT NOT NULL REFERENCES dbo.job_application (application_id),
  eligible BIT NOT NULL,
  failures_json NVARCHAR(MAX) NULL,
  evaluated_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME()
);

CREATE TABLE dbo.application_profile_snapshot (
  application_id BIGINT PRIMARY KEY REFERENCES dbo.job_application (application_id),
  cv_number VARCHAR(30) NOT NULL,
  full_name NVARCHAR(150) NOT NULL,
  father_name NVARCHAR(150),
  mother_name NVARCHAR(150),
  date_of_birth DATE,
  gender VARCHAR(20),
  marital_status VARCHAR(30),
  nationality VARCHAR(50),
  nid_number VARCHAR(30)
);

CREATE TABLE dbo.application_education_snapshot (
  snapshot_id BIGINT IDENTITY PRIMARY KEY,
  application_id BIGINT NOT NULL REFERENCES dbo.job_application (application_id),
  qualification_id BIGINT NOT NULL,
  subject_id BIGINT NULL,
  institution_name NVARCHAR(200),
  result_type VARCHAR(20) NOT NULL,
  result_value DECIMAL(5, 2),
  result_scale DECIMAL(5, 2),
  result_grade VARCHAR(30),
  passing_year SMALLINT NOT NULL
);

CREATE TABLE dbo.application_experience_snapshot (
  snapshot_id BIGINT IDENTITY PRIMARY KEY,
  application_id BIGINT NOT NULL REFERENCES dbo.job_application (application_id),
  employer_name NVARCHAR(200) NOT NULL,
  designation NVARCHAR(150) NOT NULL,
  start_date DATE NOT NULL,
  end_date DATE NULL
);

CREATE TABLE dbo.application_document (
  application_document_id BIGINT IDENTITY PRIMARY KEY,
  application_id BIGINT NOT NULL REFERENCES dbo.job_application (application_id),
  document_type VARCHAR(40) NOT NULL,
  file_id BIGINT NOT NULL REFERENCES dbo.file_asset (file_id)
);

CREATE TABLE dbo.application_status_history (
  history_id BIGINT IDENTITY PRIMARY KEY,
  application_id BIGINT NOT NULL REFERENCES dbo.job_application (application_id),
  from_status VARCHAR(30),
  to_status VARCHAR(30) NOT NULL,
  changed_by BIGINT NOT NULL,
  changed_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME()
);

CREATE TABLE dbo.notification_outbox (
  outbox_id BIGINT IDENTITY PRIMARY KEY,
  user_id BIGINT NOT NULL,
  event_type VARCHAR(60) NOT NULL,
  payload NVARCHAR(MAX) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  created_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME()
);

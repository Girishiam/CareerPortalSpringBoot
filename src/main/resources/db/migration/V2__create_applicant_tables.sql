CREATE SEQUENCE dbo.cv_number_seq
  AS BIGINT
  START WITH 1
  INCREMENT BY 1;

CREATE TABLE dbo.division (
  division_id BIGINT PRIMARY KEY,
  name NVARCHAR(100) NOT NULL
);

CREATE TABLE dbo.district (
  district_id BIGINT PRIMARY KEY,
  division_id BIGINT NOT NULL REFERENCES dbo.division (division_id),
  name NVARCHAR(100) NOT NULL
);

CREATE TABLE dbo.upazila (
  upazila_id BIGINT PRIMARY KEY,
  district_id BIGINT NOT NULL REFERENCES dbo.district (district_id),
  name NVARCHAR(100) NOT NULL
);

CREATE TABLE dbo.qualification (
  qualification_id BIGINT PRIMARY KEY,
  code VARCHAR(40) NOT NULL UNIQUE,
  name NVARCHAR(120) NOT NULL,
  level_rank INT NOT NULL
);

CREATE TABLE dbo.subject (
  subject_id BIGINT PRIMARY KEY,
  name NVARCHAR(150) NOT NULL
);

CREATE TABLE dbo.institution (
  institution_id BIGINT PRIMARY KEY,
  name NVARCHAR(200) NOT NULL
);

CREATE TABLE dbo.applicant_profile (
  applicant_id BIGINT IDENTITY PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES dbo.user_account (user_id) CONSTRAINT uq_applicant_user UNIQUE,
  cv_number VARCHAR(30) NOT NULL CONSTRAINT uq_cv_number UNIQUE,
  full_name NVARCHAR(150) NOT NULL,
  father_name NVARCHAR(150) NULL,
  mother_name NVARCHAR(150) NULL,
  date_of_birth DATE NULL,
  gender VARCHAR(20) NULL,
  marital_status VARCHAR(30) NULL,
  nationality VARCHAR(50) NULL,
  nid_number VARCHAR(30) NULL,
  created_at DATETIME2(3) NOT NULL CONSTRAINT df_applicant_created DEFAULT SYSUTCDATETIME(),
  updated_at DATETIME2(3) NOT NULL CONSTRAINT df_applicant_updated DEFAULT SYSUTCDATETIME(),
  version BIGINT NOT NULL CONSTRAINT df_applicant_version DEFAULT 0
);

CREATE TABLE dbo.applicant_address (
  address_id BIGINT IDENTITY PRIMARY KEY,
  applicant_id BIGINT NOT NULL REFERENCES dbo.applicant_profile (applicant_id),
  address_type VARCHAR(20) NOT NULL,
  address_line NVARCHAR(300) NOT NULL,
  division_id BIGINT NOT NULL REFERENCES dbo.division (division_id),
  district_id BIGINT NOT NULL REFERENCES dbo.district (district_id),
  upazila_id BIGINT NOT NULL REFERENCES dbo.upazila (upazila_id),
  postcode VARCHAR(10) NULL,
  CONSTRAINT uq_applicant_address UNIQUE (applicant_id, address_type)
);

CREATE TABLE dbo.applicant_education (
  education_id BIGINT IDENTITY PRIMARY KEY,
  applicant_id BIGINT NOT NULL REFERENCES dbo.applicant_profile (applicant_id),
  qualification_id BIGINT NOT NULL REFERENCES dbo.qualification (qualification_id),
  subject_id BIGINT NULL REFERENCES dbo.subject (subject_id),
  institution_id BIGINT NULL REFERENCES dbo.institution (institution_id),
  institution_name NVARCHAR(200) NULL,
  result_type VARCHAR(20) NOT NULL,
  result_value DECIMAL(5, 2) NULL,
  result_scale DECIMAL(5, 2) NULL,
  result_grade VARCHAR(30) NULL,
  passing_year SMALLINT NOT NULL,
  is_highest_degree BIT NOT NULL DEFAULT 0,
  CONSTRAINT ck_education_result CHECK (
    (
      result_type IN ('GPA', 'CGPA')
      AND result_value IS NOT NULL
      AND result_scale IS NOT NULL
      AND result_value <= result_scale
    )
    OR (
      result_type IN ('DIVISION', 'CLASS')
      AND result_grade IS NOT NULL
    )
  )
);

CREATE TABLE dbo.applicant_experience (
  experience_id BIGINT IDENTITY PRIMARY KEY,
  applicant_id BIGINT NOT NULL REFERENCES dbo.applicant_profile (applicant_id),
  employer_name NVARCHAR(200) NOT NULL,
  designation NVARCHAR(150) NOT NULL,
  start_date DATE NOT NULL,
  end_date DATE NULL,
  is_current BIT NOT NULL DEFAULT 0,
  CONSTRAINT ck_experience_dates CHECK (
    end_date IS NULL
    OR end_date >= start_date
  )
);

CREATE TABLE dbo.file_asset (
  file_id BIGINT IDENTITY PRIMARY KEY,
  storage_key VARCHAR(500) NOT NULL UNIQUE,
  original_name NVARCHAR(255) NOT NULL,
  media_type VARCHAR(100) NOT NULL,
  size_bytes BIGINT NOT NULL,
  sha256 CHAR(64) NOT NULL,
  width INT NULL,
  height INT NULL,
  validation_status VARCHAR(30) NOT NULL,
  created_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME()
);

CREATE TABLE dbo.applicant_document (
  applicant_document_id BIGINT IDENTITY PRIMARY KEY,
  applicant_id BIGINT NOT NULL REFERENCES dbo.applicant_profile (applicant_id),
  document_type VARCHAR(40) NOT NULL,
  file_id BIGINT NOT NULL REFERENCES dbo.file_asset (file_id),
  active BIT NOT NULL DEFAULT 1
);

CREATE UNIQUE INDEX uq_active_applicant_document ON dbo.applicant_document (applicant_id, document_type)
WHERE
  active = 1;

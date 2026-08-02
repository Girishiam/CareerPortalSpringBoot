CREATE TABLE dbo.recruitment_exam (
  exam_event_id BIGINT IDENTITY PRIMARY KEY,
  job_id BIGINT NOT NULL REFERENCES dbo.job_posting(job_id),
  exam_type VARCHAR(20) NOT NULL,
  title NVARCHAR(200) NOT NULL,
  exam_start_at DATETIME2(3) NOT NULL,
  exam_end_at DATETIME2(3) NOT NULL,
  reporting_at DATETIME2(3) NULL,
  instructions NVARCHAR(MAX) NULL,
  status VARCHAR(20) NOT NULL CONSTRAINT df_exam_event_status DEFAULT 'DRAFT',
  generated_at DATETIME2(3) NULL,
  published_at DATETIME2(3) NULL,
  created_by BIGINT NOT NULL REFERENCES dbo.user_account(user_id),
  created_at DATETIME2(3) NOT NULL CONSTRAINT df_exam_event_created DEFAULT SYSUTCDATETIME(),
  version INT NOT NULL CONSTRAINT df_exam_event_version DEFAULT 0,
  CONSTRAINT ck_recruitment_exam_type_v52 CHECK (exam_type IN ('MCQ','WRITTEN','COMBINED')),
  CONSTRAINT ck_recruitment_exam_status_v52 CHECK (status IN ('DRAFT','GENERATED','PUBLISHED','CANCELLED')),
  CONSTRAINT ck_recruitment_exam_window_v52 CHECK (exam_end_at > exam_start_at)
);

CREATE TABLE dbo.recruitment_exam_center (
  center_id BIGINT IDENTITY PRIMARY KEY,
  exam_event_id BIGINT NOT NULL REFERENCES dbo.recruitment_exam(exam_event_id) ON DELETE CASCADE,
  center_code VARCHAR(20) NOT NULL,
  center_name NVARCHAR(200) NOT NULL,
  address NVARCHAR(500) NOT NULL,
  contact_phone VARCHAR(30) NULL,
  CONSTRAINT uq_recruitment_exam_center_code_v52 UNIQUE(exam_event_id,center_code)
);

CREATE TABLE dbo.recruitment_exam_room (
  room_id BIGINT IDENTITY PRIMARY KEY,
  center_id BIGINT NOT NULL REFERENCES dbo.recruitment_exam_center(center_id) ON DELETE CASCADE,
  room_number NVARCHAR(50) NOT NULL,
  floor_name NVARCHAR(80) NULL,
  capacity INT NOT NULL,
  CONSTRAINT ck_recruitment_exam_room_capacity_v52 CHECK (capacity > 0),
  CONSTRAINT uq_recruitment_exam_room_number_v52 UNIQUE(center_id,room_number)
);

CREATE TABLE dbo.recruitment_exam_candidate (
  exam_candidate_id BIGINT IDENTITY PRIMARY KEY,
  exam_event_id BIGINT NOT NULL REFERENCES dbo.recruitment_exam(exam_event_id) ON DELETE CASCADE,
  application_id BIGINT NOT NULL REFERENCES dbo.job_application(application_id),
  roll_number CHAR(6) NULL,
  room_id BIGINT NULL REFERENCES dbo.recruitment_exam_room(room_id),
  seat_number INT NULL,
  result_status VARCHAR(20) NOT NULL CONSTRAINT df_exam_candidate_result DEFAULT 'PENDING',
  admit_card_generated_at DATETIME2(3) NULL,
  admit_card_published_at DATETIME2(3) NULL,
  notification_queued_at DATETIME2(3) NULL,
  created_at DATETIME2(3) NOT NULL CONSTRAINT df_exam_candidate_created DEFAULT SYSUTCDATETIME(),
  CONSTRAINT uq_recruitment_exam_candidate_v52 UNIQUE(exam_event_id,application_id),
  CONSTRAINT ck_recruitment_exam_candidate_roll_v52 CHECK (roll_number IS NULL OR roll_number LIKE '[1-9][0-9][0-9][0-9][0-9][0-9]'),
  CONSTRAINT ck_recruitment_exam_candidate_result_v52 CHECK (result_status IN ('PENDING','PASSED','FAILED','ABSENT')),
  CONSTRAINT ck_recruitment_exam_candidate_seat_v52 CHECK (seat_number IS NULL OR seat_number > 0)
);

CREATE UNIQUE INDEX uq_recruitment_exam_candidate_roll_v52
  ON dbo.recruitment_exam_candidate(roll_number)
  WHERE roll_number IS NOT NULL;

CREATE UNIQUE INDEX uq_recruitment_exam_room_seat_v52
  ON dbo.recruitment_exam_candidate(room_id,seat_number)
  WHERE room_id IS NOT NULL AND seat_number IS NOT NULL;

CREATE INDEX ix_recruitment_exam_job ON dbo.recruitment_exam(job_id,status,exam_start_at);
CREATE INDEX ix_recruitment_exam_candidate_application ON dbo.recruitment_exam_candidate(application_id);

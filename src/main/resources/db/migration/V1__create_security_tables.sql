CREATE TABLE dbo.role (
  role_id BIGINT IDENTITY PRIMARY KEY,
  code VARCHAR(40) NOT NULL CONSTRAINT uq_role_code UNIQUE,
  name NVARCHAR(100) NOT NULL
);

CREATE TABLE dbo.user_account (
  user_id BIGINT IDENTITY PRIMARY KEY,
  email VARCHAR(254) NULL,
  mobile VARCHAR(20) NULL,
  username VARCHAR(80) NULL,
  employee_id VARCHAR(40) NULL,
  password_hash VARCHAR(100) NOT NULL,
  status VARCHAR(30) NOT NULL CONSTRAINT df_user_status DEFAULT 'PENDING_VERIFICATION',
  email_verified BIT NOT NULL CONSTRAINT df_email_verified DEFAULT 0,
  mobile_verified BIT NOT NULL CONSTRAINT df_mobile_verified DEFAULT 0,
  created_at DATETIME2(3) NOT NULL CONSTRAINT df_user_created DEFAULT SYSUTCDATETIME(),
  version BIGINT NOT NULL CONSTRAINT df_user_version DEFAULT 0,
  CONSTRAINT uq_user_email UNIQUE (email),
  CONSTRAINT uq_user_mobile UNIQUE (mobile),
  CONSTRAINT ck_user_login CHECK (
    email IS NOT NULL
    OR mobile IS NOT NULL
    OR username IS NOT NULL
    OR employee_id IS NOT NULL
  )
);

CREATE TABLE dbo.user_role (
  user_id BIGINT NOT NULL REFERENCES dbo.user_account (user_id),
  role_id BIGINT NOT NULL REFERENCES dbo.role (role_id),
  CONSTRAINT pk_user_role PRIMARY KEY (user_id, role_id)
);

CREATE TABLE dbo.otp_challenge (
  otp_id BIGINT IDENTITY PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES dbo.user_account (user_id),
  purpose VARCHAR(30) NOT NULL,
  code_hash VARCHAR(100) NOT NULL,
  expires_at DATETIME2(3) NOT NULL,
  consumed_at DATETIME2(3) NULL,
  attempt_count INT NOT NULL CONSTRAINT df_otp_attempt DEFAULT 0,
  created_at DATETIME2(3) NOT NULL CONSTRAINT df_otp_created DEFAULT SYSUTCDATETIME()
);

CREATE TABLE dbo.refresh_token (
  refresh_token_id BIGINT IDENTITY PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES dbo.user_account (user_id),
  token_hash CHAR(64) NOT NULL CONSTRAINT uq_refresh_hash UNIQUE,
  expires_at DATETIME2(3) NOT NULL,
  revoked_at DATETIME2(3) NULL,
  created_at DATETIME2(3) NOT NULL CONSTRAINT df_refresh_created DEFAULT SYSUTCDATETIME()
);

CREATE TABLE dbo.audit_log (
  audit_id BIGINT IDENTITY PRIMARY KEY,
  actor_user_id BIGINT NULL,
  action VARCHAR(80) NOT NULL,
  entity_type VARCHAR(80) NOT NULL,
  entity_id VARCHAR(80) NULL,
  correlation_id UNIQUEIDENTIFIER NOT NULL,
  details NVARCHAR(MAX) NULL,
  created_at DATETIME2(3) NOT NULL CONSTRAINT df_audit_created DEFAULT SYSUTCDATETIME()
);

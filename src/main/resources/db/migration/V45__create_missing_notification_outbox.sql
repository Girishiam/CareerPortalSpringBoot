IF OBJECT_ID('dbo.notification_outbox','U') IS NULL
BEGIN
  CREATE TABLE dbo.notification_outbox (
    outbox_id BIGINT IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL,
    event_type VARCHAR(60) NOT NULL,
    payload NVARCHAR(MAX) NOT NULL,
    status VARCHAR(20) NOT NULL
      CONSTRAINT df_notification_outbox_status_v45 DEFAULT 'PENDING',
    created_at DATETIME2(3) NOT NULL
      CONSTRAINT df_notification_outbox_created_at_v45 DEFAULT SYSUTCDATETIME()
  );

  CREATE INDEX ix_outbox_pending
    ON dbo.notification_outbox (status,created_at);
END;

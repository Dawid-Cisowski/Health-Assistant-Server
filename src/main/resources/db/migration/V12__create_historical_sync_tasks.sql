CREATE TABLE historical_sync_tasks (
    id              BIGSERIAL PRIMARY KEY,
    sync_date       DATE NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count     INT NOT NULL DEFAULT 0,
    events_synced   INT,
    error_message   TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_status CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED'))
);

CREATE INDEX idx_historical_sync_tasks_status ON historical_sync_tasks(status);
CREATE INDEX idx_historical_sync_tasks_date ON historical_sync_tasks(sync_date);

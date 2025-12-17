-- Drop google_fit_sync_state table
-- No longer needed after moving from PULL (Google Fit API polling) to PUSH (Health Connect events)
DROP TABLE IF EXISTS google_fit_sync_state;

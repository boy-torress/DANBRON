-- Danbron production hardening helpers.
-- Run after schema.sql in Supabase SQL Editor.

-- Prevent duplicate active pair rows in either direction.
CREATE UNIQUE INDEX IF NOT EXISTS idx_device_pairs_unique_forward
ON device_pairs(user_id, device_1_id, device_2_id);

CREATE UNIQUE INDEX IF NOT EXISTS idx_device_pairs_unique_reverse
ON device_pairs(user_id, LEAST(device_1_id, device_2_id), GREATEST(device_1_id, device_2_id));

-- Fast cleanup and command polling.
CREATE INDEX IF NOT EXISTS idx_device_events_type_time
ON device_events(user_id, device_id, event_type, timestamp DESC);

-- Optional retention job query for manual/scheduled cleanup.
-- DELETE FROM device_events WHERE timestamp < NOW() - INTERVAL '30 days';

-- If you expose Supabase anon keys directly to clients, enable RLS and create
-- Supabase-auth-aware policies. The current Danbron architecture should keep
-- all table access behind the backend service role instead.

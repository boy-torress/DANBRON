-- Danbron Database Schema
-- Run this in Supabase SQL Editor

-- Users table
CREATE TABLE IF NOT EXISTS users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email TEXT UNIQUE NOT NULL,
  name TEXT,
  created_at TIMESTAMP DEFAULT NOW()
);

-- Devices table
CREATE TABLE IF NOT EXISTS devices (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  device_type TEXT NOT NULL, -- 'android' or 'windows'
  device_name TEXT NOT NULL,
  pairing_code TEXT UNIQUE,
  paired_at TIMESTAMP,
  last_sync TIMESTAMP DEFAULT NOW(),
  created_at TIMESTAMP DEFAULT NOW()
);

-- Device pairs (1-to-1 relationships)
CREATE TABLE IF NOT EXISTS device_pairs (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  device_1_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
  device_2_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
  paired_at TIMESTAMP DEFAULT NOW(),
  unpaired_at TIMESTAMP,
  created_at TIMESTAMP DEFAULT NOW(),
  UNIQUE(device_1_id, device_2_id)
);

-- Sync data (real-time state)
CREATE TABLE IF NOT EXISTS sync_data (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
  data JSONB,
  updated_at TIMESTAMP DEFAULT NOW(),
  UNIQUE(user_id, device_id)
);

-- Device events (system data: apps, emails, etc.)
CREATE TABLE IF NOT EXISTS device_events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
  event_type TEXT NOT NULL, -- 'app_opened', 'email_received', 'notification', etc.
  event_data JSONB,
  timestamp TIMESTAMP DEFAULT NOW()
);

-- Indexes for performance
CREATE INDEX idx_devices_user_id ON devices(user_id);
CREATE INDEX idx_device_pairs_user_id ON device_pairs(user_id);
CREATE INDEX idx_device_pairs_device_1 ON device_pairs(device_1_id);
CREATE INDEX idx_device_pairs_device_2 ON device_pairs(device_2_id);
CREATE INDEX idx_sync_data_user_device ON sync_data(user_id, device_id);
CREATE INDEX idx_device_events_user_device ON device_events(user_id, device_id);
CREATE INDEX idx_device_events_timestamp ON device_events(timestamp DESC);

-- Realtime subscriptions
ALTER TABLE device_pairs REPLICA IDENTITY FULL;
ALTER TABLE sync_data REPLICA IDENTITY FULL;
ALTER TABLE device_events REPLICA IDENTITY FULL;

const { supabase } = require('../db');

// Store user data (synced from device)
const syncUserData = async (userId, deviceId, userData) => {
  const { data, error } = await supabase
    .from('sync_data')
    .upsert({
      user_id: userId,
      device_id: deviceId,
      data: userData,
      updated_at: new Date().toISOString()
    }, { onConflict: 'user_id,device_id' })
    .select()
    .single();

  if (error) throw error;
  return data;
};

// Get synced user data for a device
const getUserData = async (userId, deviceId) => {
  const { data, error } = await supabase
    .from('sync_data')
    .select('*')
    .eq('user_id', userId)
    .eq('device_id', deviceId)
    .single();

  if (error && error.code !== 'PGRST116') throw error; // PGRST116 = no rows found
  return data || null;
};

// Store system events (apps open, emails, etc.)
const recordSystemEvent = async (userId, deviceId, eventType, eventData) => {
  const { data, error } = await supabase
    .from('device_events')
    .insert({
      user_id: userId,
      device_id: deviceId,
      event_type: eventType, // 'app_opened', 'email_received', 'notification', etc.
      event_data: eventData,
      timestamp: new Date().toISOString()
    })
    .select()
    .single();

  if (error) throw error;
  return data;
};

// Get recent system events for a device
const getRecentEvents = async (userId, deviceId, limit = 50) => {
  const { data, error } = await supabase
    .from('device_events')
    .select('*')
    .eq('user_id', userId)
    .eq('device_id', deviceId)
    .order('timestamp', { ascending: false })
    .limit(limit);

  if (error) throw error;
  return data || [];
};

// Get system events since last sync
const getEventsSinceLastSync = async (userId, deviceId, lastSyncTime) => {
  const { data, error } = await supabase
    .from('device_events')
    .select('*')
    .eq('user_id', userId)
    .eq('device_id', deviceId)
    .gt('timestamp', lastSyncTime)
    .order('timestamp', { ascending: true });

  if (error) throw error;
  return data || [];
};

module.exports = {
  syncUserData,
  getUserData,
  recordSystemEvent,
  getRecentEvents,
  getEventsSinceLastSync
};

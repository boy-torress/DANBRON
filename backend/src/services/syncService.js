const { supabase } = require('../db');

const MAX_NOTES = 500;
const MAX_HABITS = 200;
const MAX_HABIT_LOGS = 1000;
const MAX_MESSAGES = 120;

const nowIso = () => new Date().toISOString();

const asObject = (value) => {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return {};
  return value;
};

const asArray = (value) => Array.isArray(value) ? value : [];

const toMillis = (value, fallback = Date.now()) => {
  if (typeof value === 'number' && Number.isFinite(value)) return value;
  if (typeof value === 'string' && value.trim()) {
    const numeric = Number(value);
    if (Number.isFinite(numeric)) return numeric;
    const parsed = Date.parse(value);
    if (!Number.isNaN(parsed)) return parsed;
  }
  return fallback;
};

const stableNumericId = (value) => {
  const raw = String(value || `${Date.now()}_${Math.random()}`);
  const asNumber = Number(raw);
  if (Number.isSafeInteger(asNumber) && asNumber > 0) return asNumber;

  let hash = 2166136261;
  for (let i = 0; i < raw.length; i += 1) {
    hash ^= raw.charCodeAt(i);
    hash = Math.imul(hash, 16777619);
  }
  return Math.abs(hash) + 1;
};

const List7False = () => [false, false, false, false, false, false, false];

const unwrapSharedState = (value) => {
  const data = asObject(value);
  return asObject(data.sharedState || data.state || data);
};

const normalizeProfile = (snapshot, rowUpdatedAt) => {
  const rawProfile = snapshot.profile || snapshot.userProfile || (
    snapshot.name || snapshot.income || snapshot.expenses || snapshot.debt ? snapshot : null
  );
  const profile = asObject(rawProfile);
  if (!Object.keys(profile).length) return null;

  const updatedAt = toMillis(
    snapshot.profileUpdatedAt || profile._syncUpdatedAt || snapshot.updatedAt,
    toMillis(rowUpdatedAt)
  );
  return {
    profile: {
      ...profile,
      _syncUpdatedAt: updatedAt
    },
    updatedAt
  };
};

const normalizeNote = (note, rowUpdatedAt) => {
  const raw = asObject(note);
  const fallbackTime = toMillis(rowUpdatedAt);
  const updatedAt = toMillis(raw.updatedAt || raw.createdAt, fallbackTime);
  const createdAt = toMillis(raw.createdAt, updatedAt);
  const identity = raw.id || `${raw.title || ''}|${raw.content || ''}|${createdAt}`;

  return {
    id: stableNumericId(identity),
    title: String(raw.title || 'Nota rapida').slice(0, 160),
    content: String(raw.content || '').slice(0, 5000),
    tag: String(raw.tag || 'general').slice(0, 40),
    createdAt,
    updatedAt
  };
};

const normalizeHabit = (habit, rowUpdatedAt) => {
  const raw = asObject(habit);
  const fallbackTime = toMillis(rowUpdatedAt);
  const updatedAt = toMillis(raw.updatedAt, fallbackTime);
  const identity = raw.id || `${raw.name || ''}|${updatedAt}`;
  return {
    id: stableNumericId(identity),
    name: String(raw.name || 'Progreso personal').slice(0, 160),
    streak: Math.max(0, Number(raw.streak || 0) || 0),
    week: Array.isArray(raw.week) ? raw.week.map(Boolean).slice(0, 7) : List7False(),
    updatedAt
  };
};

const normalizeHabitLog = (log, rowUpdatedAt) => {
  const raw = asObject(log);
  const habitId = stableNumericId(raw.habitId || raw.habit_id || raw.id || `${raw.date || ''}|${rowUpdatedAt || ''}`);
  const date = String(raw.date || '').slice(0, 10);
  if (!date) return null;
  return {
    habitId,
    date,
    completed: raw.completed !== false,
    updatedAt: toMillis(raw.updatedAt, toMillis(rowUpdatedAt))
  };
};

const normalizeMessage = (message, rowUpdatedAt) => {
  const raw = asObject(message);
  const content = String(raw.content || '').trim();
  if (!content) return null;
  const timestamp = toMillis(raw.timestamp || raw.time, toMillis(rowUpdatedAt));
  return {
    role: raw.role === 'assistant' ? 'assistant' : 'user',
    content: content.slice(0, 8000),
    timestamp
  };
};

const preferNewestBy = (items, keyFn, timeFn) => {
  const byKey = new Map();
  for (const item of items.filter(Boolean)) {
    const key = String(keyFn(item));
    const current = byKey.get(key);
    if (!current || timeFn(item) >= timeFn(current)) byKey.set(key, item);
  }
  return Array.from(byKey.values());
};

const mergeSharedStates = (entries = []) => {
  let newestProfile = null;
  const notes = [];
  const habits = [];
  const habitLogs = [];
  const messages = [];

  for (const entry of entries) {
    const rowUpdatedAt = entry?.updatedAt || entry?.updated_at || nowIso();
    const snapshot = unwrapSharedState(entry?.data || entry);

    const candidate = normalizeProfile(snapshot, rowUpdatedAt);
    if (candidate && (!newestProfile || candidate.updatedAt >= newestProfile.updatedAt)) {
      newestProfile = candidate;
    }

    asArray(snapshot.notes).forEach((note) => notes.push(normalizeNote(note, rowUpdatedAt)));
    asArray(snapshot.habits).forEach((habit) => habits.push(normalizeHabit(habit, rowUpdatedAt)));
    asArray(snapshot.habitLogs || snapshot.habit_logs).forEach((log) => habitLogs.push(normalizeHabitLog(log, rowUpdatedAt)));
    asArray(snapshot.chatMessages || snapshot.messages).forEach((message) => messages.push(normalizeMessage(message, rowUpdatedAt)));
  }

  const mergedNotes = preferNewestBy(notes, (note) => note.id, (note) => note.updatedAt)
    .sort((a, b) => b.updatedAt - a.updatedAt)
    .slice(0, MAX_NOTES);
  const mergedHabits = preferNewestBy(habits, (habit) => habit.id, (habit) => habit.updatedAt)
    .sort((a, b) => b.updatedAt - a.updatedAt)
    .slice(0, MAX_HABITS);
  const mergedHabitLogs = preferNewestBy(habitLogs, (log) => `${log.habitId}:${log.date}`, (log) => log.updatedAt)
    .sort((a, b) => b.updatedAt - a.updatedAt)
    .slice(0, MAX_HABIT_LOGS);
  const mergedMessages = preferNewestBy(messages, (message) => `${message.role}:${message.timestamp}:${message.content}`, (message) => message.timestamp)
    .sort((a, b) => a.timestamp - b.timestamp)
    .slice(-MAX_MESSAGES);

  const updatedAt = Date.now();
  return {
    schemaVersion: 2,
    updatedAt,
    profileUpdatedAt: newestProfile?.updatedAt || 0,
    profile: newestProfile?.profile || null,
    userProfile: newestProfile?.profile || null,
    notes: mergedNotes,
    habits: mergedHabits,
    habitLogs: mergedHabitLogs,
    chatMessages: mergedMessages,
    messages: mergedMessages
  };
};

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

const syncUserDataForDevices = async (userId, deviceIds, userData) => {
  const rows = deviceIds.map((deviceId) => ({
    user_id: userId,
    device_id: deviceId,
    data: userData,
    updated_at: nowIso()
  }));

  const { data, error } = await supabase
    .from('sync_data')
    .upsert(rows, { onConflict: 'user_id,device_id' })
    .select();

  if (error) throw error;
  return data || [];
};

// Get synced user data for a device
const getUserData = async (userId, deviceId) => {
  const { data, error } = await supabase
    .from('sync_data')
    .select('*')
    .eq('device_id', deviceId)
    .order('updated_at', { ascending: false })
    .limit(1);

  if (error) throw error;
  return data?.[0] || null;
};

const getUserDataForDevices = async (userId, deviceIds) => {
  if (!deviceIds.length) return [];
  const { data, error } = await supabase
    .from('sync_data')
    .select('*')
    .in('device_id', deviceIds);

  if (error) throw error;
  return data || [];
};

const syncSharedState = async (userId, deviceIds, incomingState) => {
  const existingRows = await getUserDataForDevices(userId, deviceIds);
  const entries = [
    ...existingRows.map((row) => ({ data: row.data, updatedAt: row.updated_at })),
    { data: incomingState, updatedAt: nowIso() }
  ];
  const merged = mergeSharedStates(entries);
  await syncUserDataForDevices(userId, deviceIds, merged);
  return merged;
};

const getSharedState = async (userId, deviceIds) => {
  const existingRows = await getUserDataForDevices(userId, deviceIds);
  return mergeSharedStates(existingRows.map((row) => ({ data: row.data, updatedAt: row.updated_at })));
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
    .eq('device_id', deviceId)
    .gt('timestamp', lastSyncTime)
    .order('timestamp', { ascending: true });

  if (error) throw error;
  return data || [];
};

// ── Clipboard Sync ──
const setClipboard = async (userId, deviceId, content, contentType = 'text') => {
  const { error } = await supabase
    .from('clipboard_sync')
    .upsert({
      user_id: userId,
      device_id: deviceId,
      content,
      content_type: contentType,
      updated_at: nowIso()
    }, { onConflict: 'user_id,device_id' });

  if (error) {
    // Table might not exist yet, use events as fallback
    await recordSystemEvent(userId, [deviceId], 'clipboard_update', {
      content,
      contentType,
      ts: Date.now()
    });
  }
};

const getClipboard = async (userId, deviceId) => {
  const { data, error } = await supabase
    .from('clipboard_sync')
    .select('content, content_type, updated_at')
    .eq('user_id', userId)
    .eq('device_id', deviceId)
    .single();

  if (error || !data) {
    // Fallback: check events
    const events = await getRecentEvents(userId, [deviceId], 5);
    const clipEvent = events.find(e => e.event_type === 'clipboard_update');
    if (clipEvent?.event_data) {
      return { content: clipEvent.event_data.content, contentType: clipEvent.event_data.contentType, updatedAt: clipEvent.timestamp };
    }
    return null;
  }
  return { content: data.content, contentType: data.content_type, updatedAt: data.updated_at };
};

module.exports = {
  syncUserData,
  getUserData,
  syncSharedState,
  getSharedState,
  recordSystemEvent,
  getRecentEvents,
  getEventsSinceLastSync,
  setClipboard,
  getClipboard
};

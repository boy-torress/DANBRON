const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

const DEVICE_TYPES = new Set(['android', 'windows', 'web', 'desktop', 'phone']);
const EVENT_TYPES = new Set([
  'app_opened',
  'email_received',
  'notification',
  'pending_task',
  'battery_status',
  'screen_time',
  'screen_snapshot',
  'remote_command',
  'remote_command_result'
]);
const REMOTE_TARGETS = new Set(['android', 'phone', 'telefono', 'celular', 'windows', 'pc', 'desktop', 'paired']);
const REMOTE_ACTIONS = new Set([
  'open_app',
  'open_url',
  'compose_whatsapp',
  'compose_email',
  'send_email',
  'create_calendar_event',
  'play_music',
  'open_maps',
  'sequence',
  'agent_test'
]);
const APP_NAMES = new Set([
  'whatsapp',
  'wsp',
  'opera',
  'chrome',
  'gmail',
  'calendar',
  'calendario',
  'drive',
  'docs',
  'documentos',
  'sheets',
  'hojas',
  'contacts',
  'contactos',
  'maps',
  'mapa',
  'spotify',
  'music',
  'musica',
  'youtube',
  'settings',
  'configuracion'
]);

function assertUuid(value, field = 'id') {
  if (!UUID_RE.test(String(value || ''))) {
    const error = new Error(`${field} invalid`);
    error.statusCode = 400;
    throw error;
  }
}

function assertEmail(value) {
  const email = String(value || '').trim().toLowerCase();
  if (!EMAIL_RE.test(email) || email.length > 254) {
    const error = new Error('email invalid');
    error.statusCode = 400;
    throw error;
  }
  return email;
}

function sanitizeName(value, fallback = '') {
  return String(value || fallback).trim().slice(0, 80);
}

function assertPlainObject(value, field = 'payload') {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    const error = new Error(`${field} must be an object`);
    error.statusCode = 400;
    throw error;
  }
}

function assertJsonSize(value, maxBytes, field = 'payload') {
  const bytes = Buffer.byteLength(JSON.stringify(value ?? null), 'utf8');
  if (bytes > maxBytes) {
    const error = new Error(`${field} too large`);
    error.statusCode = 413;
    throw error;
  }
}

function sanitizeDeviceType(value) {
  const type = String(value || '').trim().toLowerCase();
  if (!DEVICE_TYPES.has(type)) {
    const error = new Error('deviceType invalid');
    error.statusCode = 400;
    throw error;
  }
  return type === 'desktop' ? 'windows' : type === 'phone' ? 'android' : type;
}

function sanitizeEventType(value) {
  const type = String(value || '').trim();
  if (!EVENT_TYPES.has(type)) {
    const error = new Error('eventType invalid');
    error.statusCode = 400;
    throw error;
  }
  return type;
}

function assertSafeUrl(url) {
  let parsed;
  try {
    parsed = new URL(String(url || ''));
  } catch (_) {
    const error = new Error('url invalid');
    error.statusCode = 400;
    throw error;
  }

  if (!['http:', 'https:', 'mailto:', 'whatsapp:', 'spotify:'].includes(parsed.protocol)) {
    const error = new Error('url protocol not allowed');
    error.statusCode = 400;
    throw error;
  }
}

function sanitizeRemoteCommand(command) {
  assertPlainObject(command, 'remote command');
  assertJsonSize(command, 8192, 'remote command');

  const action = String(command.action || '').trim();
  if (!REMOTE_ACTIONS.has(action)) {
    const error = new Error('remote command action invalid');
    error.statusCode = 400;
    throw error;
  }

  const target = String(command.target || 'paired').trim().toLowerCase();
  if (!REMOTE_TARGETS.has(target)) {
    const error = new Error('remote command target invalid');
    error.statusCode = 400;
    throw error;
  }

  const args = command.args || {};
  assertPlainObject(args, 'remote command args');

  if (action === 'open_url') {
    assertSafeUrl(args.url);
  }

  if (action === 'open_app' && !APP_NAMES.has(String(args.app || '').toLowerCase())) {
    const error = new Error('app not allowed');
    error.statusCode = 400;
    throw error;
  }

  return {
    ...command,
    target,
    action,
    args
  };
}

module.exports = {
  assertUuid,
  assertEmail,
  sanitizeName,
  assertPlainObject,
  assertJsonSize,
  sanitizeDeviceType,
  sanitizeEventType,
  sanitizeRemoteCommand
};

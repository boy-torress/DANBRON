const { execFile } = require('child_process');

const GOG_BIN = process.env.GOG_BIN || 'gog';
const DEFAULT_ACCOUNT = process.env.GOG_ACCOUNT || '';

function runGog(args, timeoutMs = 30000) {
  const fullArgs = DEFAULT_ACCOUNT ? ['--account', DEFAULT_ACCOUNT, ...args] : args;

  return new Promise((resolve, reject) => {
    execFile(GOG_BIN, fullArgs, {
      timeout: timeoutMs,
      maxBuffer: 1024 * 1024,
      windowsHide: true
    }, (error, stdout, stderr) => {
      if (error) {
        const message = String(stderr || error.message || 'gog command failed').trim();
        const err = new Error(message);
        err.statusCode = /not enabled|not authorized|No tokens|credentials/i.test(message) ? 424 : 500;
        reject(err);
        return;
      }
      resolve(String(stdout || '').trim());
    });
  });
}

async function searchGmail(query, max = 5) {
  return runGog(['gmail', 'search', query, '--max', String(Math.min(max, 20))]);
}

async function readGmail(messageId) {
  return runGog(['gmail', 'read', messageId]);
}

async function sendGmail({ to, subject, body }) {
  return runGog([
    'gmail',
    'send',
    '--to',
    to,
    '--subject',
    subject,
    '--body',
    body,
    '--force',
    '--no-input'
  ]);
}

async function listCalendar(calendarId = 'primary') {
  return runGog(['calendar', 'events', calendarId]);
}

async function createCalendarEvent({ calendarId = 'primary', summary, from, to, description = '' }) {
  return runGog([
    'calendar',
    'create',
    calendarId,
    '--summary',
    summary,
    '--from',
    from,
    '--to',
    to,
    '--start-timezone',
    'America/Santiago',
    '--end-timezone',
    'America/Santiago',
    '--description',
    description
  ]);
}

async function listDrive(max = 10) {
  return runGog(['drive', 'ls', '--max', String(Math.min(max, 50))]);
}

module.exports = {
  searchGmail,
  readGmail,
  sendGmail,
  listCalendar,
  createCalendarEvent,
  listDrive
};

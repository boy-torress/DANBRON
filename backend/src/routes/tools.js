const express = require('express');
const { authMiddleware } = require('../middleware/auth');
const googleTools = require('../services/googleToolsService');

const router = express.Router();

function sendText(res, text) {
  res.json({ ok: true, text: text || '' });
}

router.get('/tools/google/status', authMiddleware, async (req, res) => {
  res.json({
    ok: true,
    provider: 'gog',
    account: process.env.GOG_ACCOUNT || 'default'
  });
});

router.get('/tools/google/gmail/search', authMiddleware, async (req, res, next) => {
  try {
    const query = String(req.query.q || 'is:unread').slice(0, 200);
    const max = parseInt(req.query.max || '5', 10);
    sendText(res, await googleTools.searchGmail(query, max));
  } catch (error) {
    next(error);
  }
});

router.get('/tools/google/gmail/read/:id', authMiddleware, async (req, res, next) => {
  try {
    const id = String(req.params.id || '').trim();
    if (!/^[a-f0-9]+$/i.test(id)) {
      return res.status(400).json({ error: 'message id invalid' });
    }
    sendText(res, await googleTools.readGmail(id));
  } catch (error) {
    next(error);
  }
});

router.post('/tools/google/gmail/send', authMiddleware, async (req, res, next) => {
  try {
    const { to, subject, body } = req.body || {};
    if (!to || !subject || !body) {
      return res.status(400).json({ error: 'to, subject and body required' });
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(String(to))) {
      return res.status(400).json({ error: 'to invalid' });
    }
    sendText(res, await googleTools.sendGmail({
      to: String(to).slice(0, 254),
      subject: String(subject).slice(0, 200),
      body: String(body).slice(0, 8000)
    }));
  } catch (error) {
    next(error);
  }
});

router.get('/tools/google/calendar/events', authMiddleware, async (req, res, next) => {
  try {
    const calendarId = String(req.query.calendarId || 'primary').slice(0, 120);
    sendText(res, await googleTools.listCalendar(calendarId));
  } catch (error) {
    next(error);
  }
});

router.post('/tools/google/calendar/create', authMiddleware, async (req, res, next) => {
  try {
    const { calendarId = 'primary', summary, from, to, description = '' } = req.body || {};
    if (!summary || !from || !to) {
      return res.status(400).json({ error: 'summary, from and to required' });
    }
    sendText(res, await googleTools.createCalendarEvent({
      calendarId: String(calendarId).slice(0, 120),
      summary: String(summary).slice(0, 160),
      from: String(from).slice(0, 80),
      to: String(to).slice(0, 80),
      description: String(description).slice(0, 2000)
    }));
  } catch (error) {
    next(error);
  }
});

router.get('/tools/google/drive/ls', authMiddleware, async (req, res, next) => {
  try {
    const max = parseInt(req.query.max || '10', 10);
    sendText(res, await googleTools.listDrive(max));
  } catch (error) {
    next(error);
  }
});

module.exports = router;

const express = require('express');
const { authMiddleware } = require('../middleware/auth');
const syncService = require('../services/syncService');

const router = express.Router();

// Sync user data from device
router.post('/sync/data', authMiddleware, async (req, res) => {
  try {
    const { deviceId, userData } = req.body;
    const userId = req.user.userId;

    if (!deviceId || !userData) {
      return res.status(400).json({ error: 'deviceId and userData required' });
    }

    const synced = await syncService.syncUserData(userId, deviceId, userData);

    res.json({
      synced: true,
      data: synced
    });
  } catch (error) {
    console.error('Sync error:', error);
    res.status(500).json({ error: error.message });
  }
});

// Get synced user data
router.get('/sync/data', authMiddleware, async (req, res) => {
  try {
    const { deviceId } = req.query;
    const userId = req.user.userId;

    if (!deviceId) {
      return res.status(400).json({ error: 'deviceId required' });
    }

    const data = await syncService.getUserData(userId, deviceId);

    res.json({
      data: data || null
    });
  } catch (error) {
    console.error('Get sync error:', error);
    res.status(500).json({ error: error.message });
  }
});

// Record system event
router.post('/sync/event', authMiddleware, async (req, res) => {
  try {
    const { deviceId, eventType, eventData } = req.body;
    const userId = req.user.userId;

    if (!deviceId || !eventType) {
      return res.status(400).json({ error: 'deviceId and eventType required' });
    }

    const event = await syncService.recordSystemEvent(userId, deviceId, eventType, eventData);

    res.json({
      recorded: true,
      event: event
    });
  } catch (error) {
    console.error('Event record error:', error);
    res.status(500).json({ error: error.message });
  }
});

// Get recent events
router.get('/sync/events', authMiddleware, async (req, res) => {
  try {
    const { deviceId, limit = 50 } = req.query;
    const userId = req.user.userId;

    if (!deviceId) {
      return res.status(400).json({ error: 'deviceId required' });
    }

    const events = await syncService.getRecentEvents(userId, deviceId, parseInt(limit));

    res.json({
      events
    });
  } catch (error) {
    console.error('Get events error:', error);
    res.status(500).json({ error: error.message });
  }
});

// Get events since last sync
router.get('/sync/events/since', authMiddleware, async (req, res) => {
  try {
    const { deviceId, since } = req.query;
    const userId = req.user.userId;

    if (!deviceId || !since) {
      return res.status(400).json({ error: 'deviceId and since required' });
    }

    const events = await syncService.getEventsSinceLastSync(userId, deviceId, since);

    res.json({
      events
    });
  } catch (error) {
    console.error('Get events since error:', error);
    res.status(500).json({ error: error.message });
  }
});

module.exports = router;

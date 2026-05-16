const express = require('express');
const { authMiddleware } = require('../middleware/auth');
const syncService = require('../services/syncService');
const deviceService = require('../services/deviceService');
const {
  assertUuid,
  assertPlainObject,
  assertJsonSize,
  sanitizeEventType,
  sanitizeRemoteCommand
} = require('../utils/validation');

const router = express.Router();

const getSharedDeviceIds = async (userId, deviceId) => {
  const device = await deviceService.getDevice(userId, deviceId);
  if (!device) {
    const error = new Error('Device not found');
    error.statusCode = 404;
    throw error;
  }

  const pairedDevice = await deviceService.getPairedDevice(userId, deviceId);
  return pairedDevice?.id ? [deviceId, pairedDevice.id] : [deviceId];
};

// Sync shared app state across paired devices.
router.post('/sync/state', authMiddleware, async (req, res) => {
  try {
    const { deviceId, state } = req.body;
    const userId = req.user.userId;

    if (!deviceId || !state) {
      return res.status(400).json({ error: 'deviceId and state required' });
    }
    assertUuid(deviceId, 'deviceId');
    assertPlainObject(state, 'state');
    assertJsonSize(state, parseInt(process.env.SYNC_STATE_MAX_BYTES || '196608', 10), 'state');

    const deviceIds = await getSharedDeviceIds(userId, deviceId);
    const mergedState = await syncService.syncSharedState(userId, deviceIds, state);

    res.json({
      synced: true,
      state: mergedState
    });
  } catch (error) {
    console.error('Shared state sync error:', error);
    res.status(error.statusCode || 500).json({ error: error.statusCode ? error.message : 'Shared state sync failed' });
  }
});

// Get merged shared app state from this device and its pair.
router.get('/sync/state', authMiddleware, async (req, res) => {
  try {
    const { deviceId } = req.query;
    const userId = req.user.userId;

    if (!deviceId) {
      return res.status(400).json({ error: 'deviceId required' });
    }
    assertUuid(deviceId, 'deviceId');

    const deviceIds = await getSharedDeviceIds(userId, deviceId);
    const sharedState = await syncService.getSharedState(userId, deviceIds);

    res.json({
      state: sharedState
    });
  } catch (error) {
    console.error('Get shared state error:', error);
    res.status(error.statusCode || 500).json({ error: error.statusCode ? error.message : 'Get shared state failed' });
  }
});

// Sync user data from device
router.post('/sync/data', authMiddleware, async (req, res) => {
  try {
    const { deviceId, userData } = req.body;
    const userId = req.user.userId;

    if (!deviceId || !userData) {
      return res.status(400).json({ error: 'deviceId and userData required' });
    }
    assertUuid(deviceId, 'deviceId');
    assertPlainObject(userData, 'userData');
    assertJsonSize(userData, parseInt(process.env.SYNC_DATA_MAX_BYTES || '65536', 10), 'userData');
    const device = await deviceService.getDevice(userId, deviceId);
    if (!device) {
      return res.status(404).json({ error: 'Device not found' });
    }

    const synced = await syncService.syncUserData(userId, deviceId, userData);

    res.json({
      synced: true,
      data: synced
    });
  } catch (error) {
    console.error('Sync error:', error);
    res.status(error.statusCode || 500).json({ error: error.statusCode ? error.message : 'Sync failed' });
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
    assertUuid(deviceId, 'deviceId');
    const device = await deviceService.getDevice(userId, deviceId);
    if (!device) {
      return res.status(404).json({ error: 'Device not found' });
    }

    const pairedDevice = await deviceService.getPairedDevice(userId, deviceId);
    const sourceDeviceId = pairedDevice?.id || deviceId;
    const data = await syncService.getUserData(userId, sourceDeviceId);

    res.json({
      data: data?.data || null
    });
  } catch (error) {
    console.error('Get sync error:', error);
    res.status(error.statusCode || 500).json({ error: error.statusCode ? error.message : 'Get sync failed' });
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
    assertUuid(deviceId, 'deviceId');
    const cleanEventType = sanitizeEventType(eventType);
    const device = await deviceService.getDevice(userId, deviceId);
    if (!device) {
      return res.status(404).json({ error: 'Device not found' });
    }
    let cleanEventData = eventData || null;
    if (cleanEventData !== null) {
      assertPlainObject(cleanEventData, 'eventData');
      assertJsonSize(cleanEventData, parseInt(process.env.EVENT_DATA_MAX_BYTES || '16384', 10), 'eventData');
      if (cleanEventType === 'remote_command') {
        cleanEventData = sanitizeRemoteCommand(cleanEventData);
      }
    }

    const event = await syncService.recordSystemEvent(userId, deviceId, cleanEventType, cleanEventData);

    res.json({
      recorded: true,
      event: event
    });
  } catch (error) {
    console.error('Event record error:', error);
    res.status(error.statusCode || 500).json({ error: error.statusCode ? error.message : 'Event record failed' });
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
    assertUuid(deviceId, 'deviceId');
    const safeLimit = Math.max(1, Math.min(parseInt(limit, 10) || 50, 100));
    const device = await deviceService.getDevice(userId, deviceId);
    if (!device) {
      return res.status(404).json({ error: 'Device not found' });
    }

    const pairedDevice = await deviceService.getPairedDevice(userId, deviceId);
    const sourceDeviceId = pairedDevice?.id || deviceId;
    const events = await syncService.getRecentEvents(userId, sourceDeviceId, safeLimit);

    res.json({
      events
    });
  } catch (error) {
    console.error('Get events error:', error);
    res.status(error.statusCode || 500).json({ error: error.statusCode ? error.message : 'Get events failed' });
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
    assertUuid(deviceId, 'deviceId');
    if (Number.isNaN(Date.parse(since))) {
      return res.status(400).json({ error: 'since invalid' });
    }
    const device = await deviceService.getDevice(userId, deviceId);
    if (!device) {
      return res.status(404).json({ error: 'Device not found' });
    }

    const pairedDevice = await deviceService.getPairedDevice(userId, deviceId);
    const sourceDeviceId = pairedDevice?.id || deviceId;
    const events = await syncService.getEventsSinceLastSync(userId, sourceDeviceId, since);

    res.json({
      events
    });
  } catch (error) {
    console.error('Get events since error:', error);
    res.status(error.statusCode || 500).json({ error: error.statusCode ? error.message : 'Get events since failed' });
  }
});

module.exports = router;

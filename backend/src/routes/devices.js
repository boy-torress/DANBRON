const express = require('express');
const { authMiddleware } = require('../middleware/auth');
const deviceService = require('../services/deviceService');

const router = express.Router();

// Register a new device
router.post('/devices/register', authMiddleware, async (req, res) => {
  try {
    const { deviceType, deviceName } = req.body;
    const userId = req.user.userId;

    if (!deviceType || !deviceName) {
      return res.status(400).json({ error: 'deviceType and deviceName required' });
    }

    const device = await deviceService.createDevice(userId, deviceType, deviceName);

    res.json({
      device: {
        id: device.id,
        pairingCode: device.pairing_code,
        deviceType: device.device_type,
        deviceName: device.device_name
      }
    });
  } catch (error) {
    console.error('Device registration error:', error);
    res.status(500).json({ error: error.message });
  }
});

// Pair two devices with pairing code
router.post('/devices/pair', authMiddleware, async (req, res) => {
  try {
    const { pairingCode, confirmedDeviceId } = req.body;
    const userId = req.user.userId;

    if (!pairingCode || !confirmedDeviceId) {
      return res.status(400).json({ error: 'pairingCode and confirmedDeviceId required' });
    }

    const pairing = await deviceService.pairDevices(userId, pairingCode, confirmedDeviceId);

    res.json({
      paired: true,
      pairing: {
        id: pairing.id,
        device_1: pairing.device_1_id,
        device_2: pairing.device_2_id
      }
    });
  } catch (error) {
    console.error('Pairing error:', error);
    res.status(400).json({ error: error.message });
  }
});

// Get paired device
router.get('/devices/paired', authMiddleware, async (req, res) => {
  try {
    const { deviceId } = req.query;
    const userId = req.user.userId;

    if (!deviceId) {
      return res.status(400).json({ error: 'deviceId required' });
    }

    const pairedDevice = await deviceService.getPairedDevice(userId, deviceId);

    if (!pairedDevice) {
      return res.json({ paired: false });
    }

    res.json({
      paired: true,
      device: {
        id: pairedDevice.id,
        deviceType: pairedDevice.device_type,
        deviceName: pairedDevice.device_name,
        lastSync: pairedDevice.last_sync
      }
    });
  } catch (error) {
    console.error('Get paired device error:', error);
    res.status(500).json({ error: error.message });
  }
});

// Unpair devices
router.post('/devices/unpair', authMiddleware, async (req, res) => {
  try {
    const { deviceId } = req.body;
    const userId = req.user.userId;

    if (!deviceId) {
      return res.status(400).json({ error: 'deviceId required' });
    }

    await deviceService.unpairDevices(userId, deviceId);

    res.json({ unpaired: true });
  } catch (error) {
    console.error('Unpair error:', error);
    res.status(500).json({ error: error.message });
  }
});

module.exports = router;

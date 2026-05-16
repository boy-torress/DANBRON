const { supabase } = require('../db');
const { v4: uuidv4 } = require('uuid');

// Generate random 6-digit code for device pairing
const generatePairingCode = () => {
  return Math.floor(100000 + Math.random() * 900000).toString();
};

const getDevice = async (userId, deviceId) => {
  const { data, error } = await supabase
    .from('devices')
    .select('*')
    .eq('id', deviceId)
    .eq('user_id', userId)
    .single();

  if (error) return null;
  return data;
};

const getDeviceByPairingCode = async (pairingCode) => {
  const { data, error } = await supabase
    .from('devices')
    .select('*')
    .eq('pairing_code', pairingCode)
    .single();

  if (error) return null;
  return data;
};

const refreshPairingCode = async (userId, deviceId) => {
  const device = await getDevice(userId, deviceId);
  if (!device) {
    const error = new Error('Device not found');
    error.statusCode = 404;
    throw error;
  }

  const pairingCode = generatePairingCode();
  const { data, error } = await supabase
    .from('devices')
    .update({
      pairing_code: pairingCode,
      last_sync: new Date().toISOString()
    })
    .eq('id', deviceId)
    .eq('user_id', userId)
    .select()
    .single();

  if (error) throw error;
  return data;
};

// Create a device record
const createDevice = async (userId, deviceType, deviceName) => {
  const deviceId = uuidv4();
  const pairingCode = generatePairingCode();
  
  const { data, error } = await supabase
    .from('devices')
    .insert({
      id: deviceId,
      user_id: userId,
      device_type: deviceType, // 'android' or 'windows'
      device_name: deviceName,
      pairing_code: pairingCode,
      paired_at: null,
      last_sync: new Date().toISOString()
    })
    .select()
    .single();

  if (error) throw error;
  return data;
};

// Pair two devices
const pairDevices = async (userId, pairingCode, confirmedDeviceId) => {
  const confirmedDevice = await getDevice(userId, confirmedDeviceId);
  if (!confirmedDevice) {
    throw new Error('Confirmed device not found');
  }

  // Pairing codes are a short-lived proof that the user is holding the other
  // device, so the code may belong to a device created under a different login.
  const device = await getDeviceByPairingCode(pairingCode);
  if (!device) {
    throw new Error('Invalid pairing code');
  }

  if (device.id === confirmedDeviceId) {
    throw new Error('Cannot pair device with itself');
  }

  const pairingCodeCreatedAt = device.last_sync || device.created_at;
  if (pairingCodeCreatedAt) {
    const ageMs = Date.now() - new Date(pairingCodeCreatedAt).getTime();
    const maxAgeMs = parseInt(process.env.PAIRING_CODE_TTL_MS || String(10 * 60 * 1000), 10);
    if (ageMs > maxAgeMs) {
      throw new Error('Pairing code expired');
    }
  }

  const pairedAt = new Date().toISOString();

  // Keep pairing 1-to-1. If either side was linked before, remove that link.
  const { error: deleteError } = await supabase
    .from('device_pairs')
    .delete()
    .or(`device_1_id.eq.${device.id},device_2_id.eq.${device.id},device_1_id.eq.${confirmedDeviceId},device_2_id.eq.${confirmedDeviceId}`);

  if (deleteError) throw deleteError;

  const pairRows = [{
    device_1_id: device.id,
    device_2_id: confirmedDeviceId,
    user_id: device.user_id,
    paired_at: pairedAt
  }];

  if (confirmedDevice.user_id !== device.user_id) {
    pairRows.push({
      device_1_id: confirmedDeviceId,
      device_2_id: device.id,
      user_id: confirmedDevice.user_id,
      paired_at: pairedAt
    });
  }

  const { data, error } = await supabase
    .from('device_pairs')
    .insert(pairRows)
    .select();

  if (error) throw error;

  // Clear pairing code and mark both devices as paired.
  await supabase
    .from('devices')
    .update({ paired_at: pairedAt })
    .in('id', [device.id, confirmedDeviceId]);

  await supabase
    .from('devices')
    .update({ pairing_code: null })
    .eq('id', device.id);

  return {
    id: data[0].id,
    device_1_id: device.id,
    device_2_id: confirmedDeviceId,
    paired_at: pairedAt
  };
};

// Pair devices when a legacy mobile code was used through the ntfy bridge.
const createPairForKnownDevices = async (userId, deviceId, pairedDeviceId) => {
  const device = await getDevice(userId, deviceId);
  if (!device) {
    throw new Error('Device not found');
  }

  const { data: pairedDevice, error: pairedDeviceError } = await supabase
    .from('devices')
    .select('*')
    .eq('id', pairedDeviceId)
    .single();

  if (pairedDeviceError || !pairedDevice) {
    throw new Error('Paired device not found');
  }

  const pairedAt = new Date().toISOString();
  const { error: deleteError } = await supabase
    .from('device_pairs')
    .delete()
    .or(`device_1_id.eq.${deviceId},device_2_id.eq.${deviceId},device_1_id.eq.${pairedDeviceId},device_2_id.eq.${pairedDeviceId}`);

  if (deleteError) throw deleteError;

  const rows = [{
      device_1_id: device.id,
      device_2_id: pairedDeviceId,
      user_id: userId,
      paired_at: pairedAt
  }];

  if (pairedDevice.user_id !== userId) {
    rows.push({
      device_1_id: pairedDeviceId,
      device_2_id: device.id,
      user_id: pairedDevice.user_id,
      paired_at: pairedAt
    });
  }

  const { data, error } = await supabase
    .from('device_pairs')
    .insert(rows)
    .select();

  if (error) throw error;

  await supabase
    .from('devices')
    .update({ paired_at: pairedAt })
    .in('id', [deviceId, pairedDeviceId]);

  return data[0];
};

// Get paired device for current device
const getPairedDevice = async (userId, deviceId) => {
  const { data, error } = await supabase
    .from('device_pairs')
    .select('device_1_id, device_2_id')
    .eq('user_id', userId)
    .or(`device_1_id.eq.${deviceId},device_2_id.eq.${deviceId}`)
    .single();

  if (error) return null;

  // Get the paired device ID
  const pairedDeviceId = data.device_1_id === deviceId ? data.device_2_id : data.device_1_id;

  // Get paired device details
  const { data: pairedDevice, error: deviceError } = await supabase
    .from('devices')
    .select('*')
    .eq('id', pairedDeviceId)
    .single();

  if (deviceError) throw deviceError;
  return pairedDevice;
};

// Unpair devices
const unpairDevices = async (userId, deviceId) => {
  const device = await getDevice(userId, deviceId);
  if (!device) {
    const error = new Error('Device not found');
    error.statusCode = 404;
    throw error;
  }

  const { error } = await supabase
    .from('device_pairs')
    .delete()
    .or(`device_1_id.eq.${deviceId},device_2_id.eq.${deviceId}`);

  if (error) throw error;

  // Clear pairing code from device
  await supabase
    .from('devices')
    .update({ paired_at: null })
    .eq('id', deviceId);
};

module.exports = {
  generatePairingCode,
  getDevice,
  refreshPairingCode,
  createDevice,
  pairDevices,
  createPairForKnownDevices,
  getPairedDevice,
  unpairDevices
};

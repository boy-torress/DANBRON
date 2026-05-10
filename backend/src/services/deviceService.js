const { supabase } = require('../db');
const { v4: uuidv4 } = require('uuid');

// Generate random 6-digit code for device pairing
const generatePairingCode = () => {
  return Math.floor(100000 + Math.random() * 900000).toString();
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
  // Verify pairing code exists
  const { data: device, error: deviceError } = await supabase
    .from('devices')
    .select('*')
    .eq('pairing_code', pairingCode)
    .eq('user_id', userId)
    .single();

  if (deviceError) throw new Error('Invalid pairing code');

  if (!device) {
    throw new Error('Device not found');
  }

  // Create pairing relationship (1-a-1)
  const { data, error } = await supabase
    .from('device_pairs')
    .insert({
      device_1_id: device.id,
      device_2_id: confirmedDeviceId,
      user_id: userId,
      paired_at: new Date().toISOString()
    })
    .select()
    .single();

  if (error) throw error;

  // Clear pairing code
  await supabase
    .from('devices')
    .update({ pairing_code: null, paired_at: new Date().toISOString() })
    .eq('id', device.id);

  return data;
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
  const { error } = await supabase
    .from('device_pairs')
    .delete()
    .eq('user_id', userId)
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
  createDevice,
  pairDevices,
  getPairedDevice,
  unpairDevices
};

/**
 * Danbron Sync Client for Desktop (Tauri)
 * Handles device pairing, data sync, and system monitoring
 */

const DEFAULT_BACKEND_URL = "https://danbron-production.up.railway.app/api"; // Danbron Backend (Railway)
const API_TIMEOUT = 30000; // 30 seconds

function normalizeBackendUrl(url) {
  const cleaned = String(url || DEFAULT_BACKEND_URL).trim().replace(/\/+$/, '');
  return cleaned.endsWith('/api') ? cleaned : `${cleaned}/api`;
}

// Storage keys
const STORAGE_KEYS = {
  AUTH_TOKEN: 'danbron_auth_token',
  USER_ID: 'danbron_user_id',
  DEVICE_ID: 'danbron_device_id',
  PAIRED_DEVICE_ID: 'danbron_paired_device_id',
  LAST_SYNC: 'danbron_last_sync'
};

class DanbronSyncClient {
  constructor() {
    this.baseUrl = normalizeBackendUrl(localStorage.getItem('danbron_backend_url') || DEFAULT_BACKEND_URL);
    this.token = localStorage.getItem(STORAGE_KEYS.AUTH_TOKEN);
    this.userId = localStorage.getItem(STORAGE_KEYS.USER_ID);
    this.deviceId = localStorage.getItem(STORAGE_KEYS.DEVICE_ID);
    this.pairedDeviceId = localStorage.getItem(STORAGE_KEYS.PAIRED_DEVICE_ID);
    this.listeners = new Map();
  }

  configureBaseUrl(url) {
    this.baseUrl = normalizeBackendUrl(url);
    localStorage.setItem('danbron_backend_url', this.baseUrl);
  }

  /**
   * Authenticate with backend
   */
  async authenticate(email, name = null) {
    try {
      const response = await this.post('/auth', {
        email,
        name: name || email.split('@')[0]
      });

      this.token = response.token;
      this.userId = response.user.id;

      localStorage.setItem(STORAGE_KEYS.AUTH_TOKEN, this.token);
      localStorage.setItem(STORAGE_KEYS.USER_ID, this.userId);

      this.emit('authenticated', { user: response.user });
      return response;
    } catch (error) {
      console.error('Authentication failed:', error);
      throw error;
    }
  }

  /**
   * Register current device
   */
  async registerDevice(deviceName = null) {
    if (!this.token) throw new Error('Not authenticated');

    try {
      const name = deviceName || `${this.getSystemInfo().os} Desktop`;
      const response = await this.post('/devices/register', {
        deviceType: 'windows',
        deviceName: name
      }, this.token);

      this.deviceId = response.device.id;
      localStorage.setItem(STORAGE_KEYS.DEVICE_ID, this.deviceId);

      this.emit('device_registered', response.device);
      return response.device;
    } catch (error) {
      console.error('Device registration failed:', error);
      throw error;
    }
  }

  /**
   * Get pairing code for current device
   */
  async getPairingCode() {
    if (!this.token) throw new Error('Not authenticated');
    if (!this.deviceId) {
      const device = await this.registerDevice();
      return device.pairingCode;
    }

    const response = await this.post('/devices/pairing-code', {
      deviceId: this.deviceId
    }, this.token);
    return response.device.pairingCode;
  }

  /**
   * Pair with another device using pairing code
   */
  async pairDevice(pairingCode, confirmedDeviceId) {
    if (!this.token) throw new Error('Not authenticated');
    if (!this.deviceId) throw new Error('Device not registered');

    try {
      const response = await this.post(
        '/devices/pair',
        {
          pairingCode,
          confirmedDeviceId: confirmedDeviceId || this.deviceId
        },
        this.token
      );

      this.pairedDeviceId = response.pairing.device_2 === this.deviceId 
        ? response.pairing.device_1 
        : response.pairing.device_2;

      localStorage.setItem(STORAGE_KEYS.PAIRED_DEVICE_ID, this.pairedDeviceId);

      this.emit('device_paired', { pairedDeviceId: this.pairedDeviceId });
      return response;
    } catch (error) {
      console.error('Device pairing failed:', error);
      throw error;
    }
  }

  /**
   * Auto-pair with another device under the same account
   */
  async autoPair() {
    if (!this.token || !this.deviceId) throw new Error('Not authenticated or no device');

    const response = await this.post('/devices/auto-pair', {
      deviceId: this.deviceId
    }, this.token);

    if (response.paired) {
      this.pairedDeviceId = response.otherDevice.id;
      localStorage.setItem(STORAGE_KEYS.PAIRED_DEVICE_ID, this.pairedDeviceId);
      this.emit('device_paired', { pairedDeviceId: this.pairedDeviceId });
      return response;
    }
    return null;
  }

  /**
   * Check if another device is paired
   */
  async checkPairedDevice() {
    if (!this.token || !this.deviceId) return null;

    try {
      const response = await this.get('/devices/paired', { deviceId: this.deviceId }, this.token);
      
      if (response.paired) {
        this.pairedDeviceId = response.device.id;
        localStorage.setItem(STORAGE_KEYS.PAIRED_DEVICE_ID, this.pairedDeviceId);
        return response.device;
      }

      this.pairedDeviceId = null;
      localStorage.removeItem(STORAGE_KEYS.PAIRED_DEVICE_ID);
      return null;
    } catch (error) {
      console.error('Failed to check paired device:', error);
      return null;
    }
  }

  /**
   * Unpair current device
   */
  async unpairDevice() {
    if (!this.token || !this.deviceId) throw new Error('Device not configured');

    try {
      await this.post('/devices/unpair', { deviceId: this.deviceId }, this.token);
      
      this.pairedDeviceId = null;
      localStorage.removeItem(STORAGE_KEYS.PAIRED_DEVICE_ID);

      this.emit('device_unpaired', {});
    } catch (error) {
      console.error('Device unpairing failed:', error);
      throw error;
    }
  }

  /**
   * Sync user data to backend
   */
  async syncUserData(userData) {
    if (!this.token || !this.deviceId) throw new Error('Device not configured');

    try {
      const response = await this.post(
        '/sync/data',
        {
          deviceId: this.deviceId,
          userData
        },
        this.token
      );

      localStorage.setItem(STORAGE_KEYS.LAST_SYNC, new Date().toISOString());
      this.emit('data_synced', { synced: true });
      return response;
    } catch (error) {
      console.error('Sync failed:', error);
      throw error;
    }
  }

  /**
   * Get synced data from paired device
   */
  async getSyncedData() {
    if (!this.token || !this.deviceId) return null;

    try {
      const response = await this.get(
        '/sync/data',
        { deviceId: this.deviceId },
        this.token
      );

      this.emit('data_received', { data: response.data });
      return response.data;
    } catch (error) {
      console.error('Failed to get synced data:', error);
      return null;
    }
  }

  async syncSharedState(sharedState) {
    if (!this.token || !this.deviceId) throw new Error('Device not configured');

    const response = await this.post(
      '/sync/state',
      {
        deviceId: this.deviceId,
        state: sharedState
      },
      this.token
    );

    localStorage.setItem(STORAGE_KEYS.LAST_SYNC, new Date().toISOString());
    this.emit('shared_state_synced', { synced: true, state: response.state });
    return response.state;
  }

  async getSharedState() {
    if (!this.token || !this.deviceId) return null;

    try {
      const response = await this.get('/sync/state', { deviceId: this.deviceId }, this.token);
      this.emit('shared_state_received', { state: response.state });
      return response.state;
    } catch (error) {
      console.error('Failed to get shared state:', error);
      return null;
    }
  }

  /**
   * Record a system event
   */
  async recordSystemEvent(eventType, eventData = null) {
    if (!this.token || !this.deviceId) return;

    try {
      await this.post(
        '/sync/event',
        {
          deviceId: this.deviceId,
          eventType,
          eventData
        },
        this.token
      );

      this.emit('event_recorded', { eventType });
    } catch (error) {
      // Silently fail for events
      console.debug('Event recording failed:', error);
    }
  }

  async sendRemoteCommand(command) {
    if (!this.token || !this.deviceId) throw new Error('Device not configured');

    const commandEvent = {
      id: command.id || `cmd_${Date.now()}_${Math.random().toString(16).slice(2)}`,
      sourceDeviceId: this.deviceId,
      target: command.target || 'paired',
      action: command.action,
      args: command.args || {},
      status: 'queued',
      requiresConfirmation: !!command.requiresConfirmation,
      createdAt: new Date().toISOString()
    };

    await this.recordSystemEvent('remote_command', commandEvent);
    return commandEvent;
  }

  async recordCommandResult(commandId, status, result = {}) {
    if (!commandId) return;
    await this.recordSystemEvent('remote_command_result', {
      commandId,
      status,
      result,
      completedAt: new Date().toISOString()
    });
  }

  async getRemoteCommands(limit = 25) {
    const events = await this.getRecentEvents(limit);
    return events
      .filter(event => event.event_type === 'remote_command' && event.event_data)
      .map(event => ({
        ...event.event_data,
        eventId: event.id,
        timestamp: event.timestamp
      }));
  }

  /**
   * Get recent events
   */
  async getRecentEvents(limit = 50) {
    if (!this.token || !this.deviceId) return [];

    try {
      const response = await this.get(
        '/sync/events',
        { deviceId: this.deviceId, limit },
        this.token
      );

      return response.events || [];
    } catch (error) {
      console.error('Failed to get events:', error);
      return [];
    }
  }

  /**
   * AI chat proxy (backend)
   */
  async aiChat(payload = {}) {
    if (!this.token) throw new Error('Not authenticated');
    return await this.post('/ai/chat', payload, this.token);
  }

  /**
   * Subscribe to real-time events
   */
  on(eventType, callback) {
    if (!this.listeners.has(eventType)) {
      this.listeners.set(eventType, []);
    }
    this.listeners.get(eventType).push(callback);
  }

  /**
   * Emit events
   */
  emit(eventType, data) {
    if (this.listeners.has(eventType)) {
      this.listeners.get(eventType).forEach(callback => callback(data));
    }
  }

  /**
   * HTTP Methods
   */
  async post(endpoint, body, token = null) {
    const url = `${this.baseUrl}${endpoint}`;
    const headers = {
      'Content-Type': 'application/json',
      ...(token && { 'Authorization': `Bearer ${token}` })
    };

    // Try fetch first, fallback to PowerShell in Tauri
    try {
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), API_TIMEOUT);
      try {
        const response = await fetch(url, {
          method: 'POST',
          headers,
          body: JSON.stringify(body),
          signal: controller.signal
        });
        if (!response.ok) {
          const error = await response.json().catch(() => ({ error: `HTTP ${response.status}` }));
          throw new Error(error.error || `HTTP ${response.status}`);
        }
        return await response.json();
      } finally {
        clearTimeout(timeout);
      }
    } catch (fetchError) {
      // Fallback: use PowerShell in Tauri to bypass CSP/CORS
      if (typeof window !== 'undefined' && window.__TAURI__) {
        return await this._tauriRequest('POST', url, headers, body);
      }
      throw fetchError;
    }
  }

  async get(endpoint, params = {}, token = null) {
    const query = new URLSearchParams(params).toString();
    const url = `${this.baseUrl}${endpoint}${query ? '?' + query : ''}`;
    const headers = {
      ...(token && { 'Authorization': `Bearer ${token}` })
    };

    try {
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), API_TIMEOUT);
      try {
        const response = await fetch(url, {
          method: 'GET',
          headers,
          signal: controller.signal
        });
        if (!response.ok) {
          const error = await response.json().catch(() => ({ error: `HTTP ${response.status}` }));
          throw new Error(error.error || `HTTP ${response.status}`);
        }
        return await response.json();
      } finally {
        clearTimeout(timeout);
      }
    } catch (fetchError) {
      if (typeof window !== 'undefined' && window.__TAURI__) {
        return await this._tauriRequest('GET', url, headers, null);
      }
      throw fetchError;
    }
  }

  async _tauriRequest(method, url, headers, body) {
    const invoke = window.__TAURI__.core?.invoke || window.__TAURI__.invoke;
    if (!invoke) throw new Error('Tauri invoke not available');

    const result = await invoke('http_request', {
      method,
      url,
      headers: headers || {},
      body: body ? JSON.stringify(body) : null
    });

    const text = String(result || '').trim();
    if (!text) throw new Error('Empty response from backend');
    return JSON.parse(text);
  }

  /**
   * Utilities
   */
  getSystemInfo() {
    return {
      os: navigator.userAgent.includes('Win') ? 'Windows' :
          navigator.userAgent.includes('Mac') ? 'macOS' : 'Linux',
      userAgent: navigator.userAgent
    };
  }

  isAuthenticated() {
    return !!this.token && !!this.deviceId;
  }

  isPaired() {
    return !!this.pairedDeviceId;
  }
}

// Global instance
const danbronSync = new DanbronSyncClient();

// Export for module usage
if (typeof module !== 'undefined' && module.exports) {
  module.exports = DanbronSyncClient;
}

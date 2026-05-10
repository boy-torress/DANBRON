# Danbron Sync System - Setup Guide

## Overview

This is a complete device synchronization system for Danbron that enables:
- **1-to-1 device pairing** (Android phone ↔ Windows desktop)
- **Real-time data sync** (user profiles, tasks, habits)
- **System event tracking** (apps opened, emails, battery, idle time)
- **Scalable to 1M+ users** (using Supabase free tier)
- **Completely free** (no cost for hosting or APIs)

## Architecture

```
┌─────────────────┐         ┌──────────────────────┐         ┌─────────────────┐
│   Android App   │◄───────►│  Supabase Backend    │◄───────►│  Windows App    │
│   (Kotlin)      │ HTTPS   │  (PostgreSQL+Auth)   │  HTTPS  │  (Tauri)        │
└─────────────────┘         └──────────────────────┘         └─────────────────┘
        │                             │                              │
        ├─ Device registration        ├─ Device pairing codes        ├─ Register device
        ├─ Pairing code exchange      ├─ Real-time sync              ├─ Exchange pairing code
        ├─ Data sync                  ├─ Event storage               ├─ Monitor system
        └─ Event tracking             └─ Auth tokens                 └─ Record events
```

## Step 1: Setup Supabase Backend

### 1.1 Create Supabase Project
1. Go to [supabase.com](https://supabase.com)
2. Sign up and create a new project
3. Note your:
   - **Project URL**: `https://your-project.supabase.co`
   - **Anon Key**: In Settings → API → anon key
   - **Service Role Key**: For backend only

### 1.2 Create Database Schema
1. Go to SQL Editor in Supabase
2. Open a new query
3. Copy and paste contents of `backend/schema.sql`
4. Run the query
5. Tables created: `users`, `devices`, `device_pairs`, `sync_data`, `device_events`

### 1.3 Enable Realtime
1. Go to Realtime in Supabase settings
2. Enable replication for tables:
   - `device_pairs`
   - `sync_data`
   - `device_events`

## Step 2: Deploy Backend

### 2.1 Prepare Environment
```bash
cd backend
cp .env.example .env
```

Edit `.env`:
```env
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_KEY=your-anon-key
SUPABASE_JWT_SECRET=your-jwt-secret
PORT=3000
JWT_SECRET=your-secret-key
CORS_ORIGIN=*
```

### 2.2 Deploy (Recommended: Railway or Render)

**Option A: Railway**
```bash
npm install -g @railway/cli
railway up
```

**Option B: Render**
1. Push `backend/` to GitHub
2. Connect to Render
3. Create Web Service
4. Set environment variables
5. Deploy

**Option C: Local Testing**
```bash
cd backend
npm install
npm start
# Server runs on http://localhost:3000
```

### 2.3 Update BACKEND_URL
Once deployed, get your backend URL and update:
- `desktop/src/sync-client.js` line 3: `const BACKEND_URL = "your-backend-url/api"`
- `app/src/main/java/com/danbron/app/build.gradle.kts` (see Step 3.1)

## Step 3: Android App Integration

### 3.1 Update build.gradle.kts
In `app/build.gradle.kts`, add to `buildTypes`:
```kotlin
buildTypes {
    debug {
        buildConfigField "String", "BACKEND_URL", "\"https://your-backend.com/api/\""
    }
    release {
        buildConfigField "String", "BACKEND_URL", "\"https://your-backend.com/api/\""
    }
}
```

### 3.2 Add Permissions to AndroidManifest.xml
```xml
<!-- Existing permissions -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<!-- New permissions for system tracking -->
<uses-permission android:name="android.permission.BIND_ACCESSIBILITY_SERVICE" />
<uses-permission android:name="android.permission.READ_CONTACTS" />
<uses-permission android:name="android.permission.READ_CALENDAR" />
<uses-permission android:name="android.permission.READ_PHONE_STATE" />

<!-- Accessibility Service -->
<service android:name=".system.AppTrackerAccessibilityService"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
    android:exported="true">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/accessibility_service_config" />
</service>
```

### 3.3 Create Accessibility Service Config
Create `app/src/main/res/xml/accessibility_service_config.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeWindowStateChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagIncludeNotImportantViews"
    android:settingsActivity=".MainActivity"
    android:canRetrieveWindowContent="true" />
```

### 3.4 Integrate in MainActivity
```kotlin
import com.danbron.app.sync.DanbronSyncManager
import com.danbron.app.system.SystemEventsTracker

class MainActivity : ComponentActivity() {
    private lateinit var syncManager: DanbronSyncManager
    private lateinit var eventTracker: SystemEventsTracker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        syncManager = DanbronSyncManager(this)
        eventTracker = SystemEventsTracker(this)

        // Authenticate on first run
        if (userEmail != null) {
            viewModelScope.launch {
                syncManager.authenticate(userEmail, userName)
                syncManager.registerDevice("android", "My Phone")
                
                // Start tracking events
                // (handled by WorkManager in background)
            }
        }

        // Listen for paired device changes
        viewModelScope.launch {
            val pairedDevice = syncManager.getPairedDevice()
            if (pairedDevice != null) {
                // Sync data with paired device
                syncManager.syncUserData(getCurrentUserData())
            }
        }
    }
}
```

## Step 4: Desktop App Integration

### 4.1 Update Cargo.toml
Add Tauri shell plugin:
```toml
[dependencies.tauri-plugin-shell]
git = "https://github.com/tauri-apps/plugins-workspace"
branch = "v2"
features = ["open"]
```

### 4.2 Update tauri.conf.json
```json
{
  "app": {
    "security": {
      "csp": "default-src 'self'; script-src 'self' 'wasm-unsafe-eval' 'inline-require'; connect-src 'self' https: wss:;"
    }
  },
  "tauri": {
    "cli": {
      "description": "Danbron Desktop App"
    },
    "bundle": {
      "windows": [
        "nsis",
        "msi"
      ]
    },
    "allowlist": {
      "shell": {
        "execute": true,
        "open": true,
        "sidecar": true,
        "all": false
      }
    }
  }
}
```

### 4.3 Integrate in app.js
```javascript
import DanbronSyncClient from './sync-client.js';
import WindowsSystemTracker from './windows-tracker.js';

// Initialize sync
const syncClient = new DanbronSyncClient();
const systemTracker = new WindowsSystemTracker(syncClient);

// Authenticate
async function login(email) {
    try {
        await syncClient.authenticate(email);
        await syncClient.registerDevice('Danbron Desktop');
        
        // Start system monitoring
        await systemTracker.startMonitoring();
        
        // Listen for paired device
        const pairedDevice = await syncClient.checkPairedDevice();
        if (pairedDevice) {
            console.log('Connected to:', pairedDevice.deviceName);
        }
    } catch (error) {
        console.error('Login failed:', error);
    }
}

// Show pairing code
async function showPairingCode() {
    const code = await syncClient.getPairingCode();
    console.log('Pairing code:', code);
    return code;
}

// Sync data to paired device
async function syncToPairedDevice() {
    const userData = getCurrentUserProfile();
    await syncClient.syncUserData(userData);
}

// Listen for events
syncClient.on('authenticated', (data) => {
    console.log('Authenticated as:', data.user.email);
});

syncClient.on('device_paired', (data) => {
    console.log('Device paired!');
    showPairingMessage('Connected to your phone!');
});

syncClient.on('data_synced', () => {
    console.log('Data synced to cloud');
});

syncClient.on('event_recorded', (data) => {
    console.log('Event recorded:', data.eventType);
});
```

## Step 5: Device Pairing Flow

### On Android:
1. User opens app
2. App shows pairing code (e.g., "123456")
3. User enters code on desktop or desktop shows code and user enters on Android

### On Desktop:
1. User logs in
2. Shows pairing code
3. Enters code from Android (or vice versa)

### Backend:
1. Exchanges pairing code for device link
2. Creates 1-to-1 relationship in `device_pairs` table
3. Enables real-time sync

### After Pairing:
- Both devices can read/write to shared `sync_data` table
- System events are recorded in `device_events` table
- Real-time notifications via Supabase Realtime

## Step 6: Data Sync Flow

### User Profile Sync
```
Android (saves task)
  ↓
syncUserData() → Backend
  ↓
Supabase stores in sync_data
  ↓
Realtime event emitted
  ↓
Desktop receives via subscription
  ↓
Desktop updates UI
```

### System Events
```
Desktop (app opened)
  ↓
recordSystemEvent('app_opened')
  ↓
Backend stores in device_events
  ↓
Bron AI can query recent events
  ↓
Provides contextual recommendations
  ↓
"I see you're gaming, but you have emails to review"
```

## Step 7: AI Context (Bron's Recommendations)

Bron now has access to:
- **User data**: Name, debt, income, goals
- **Device activity**: What app is open, idle time
- **Communications**: Pending emails, messages
- **Context**: Time, battery level, calendar events

This enables:
```
"Hey amigo, I see you're playing, but you have 3 unread emails. 
Want to check them quick? I'll help you prioritize."
```

## Testing

### 1. Test Backend
```bash
curl http://localhost:3000/health
# Response: {"status":"ok"}
```

### 2. Test Authentication
```bash
curl -X POST http://localhost:3000/api/auth \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","name":"Test User"}'
```

### 3. Test Device Registration
```bash
curl -X POST http://localhost:3000/api/devices/register \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"deviceType":"windows","deviceName":"My Desktop"}'
```

## Scaling to 1M Users

**Current Architecture Cost**:
- Supabase: ~$25/month for up to 500k rows
- Backend: ~$5/month (Railway free tier) or ~$0/month (free tier)
- Total: **~$30/month** for 1M users

**Optimizations**:
1. Archive old events (keep only 30 days)
2. Batch sync requests
3. Use Supabase connection pooling
4. Cache paired device info

## Troubleshooting

### "Invalid token"
- Check JWT_SECRET in .env matches backend
- Token may have expired (30-day expiry)

### "Device not paired"
- Ensure both devices are registered first
- Check pairing code hasn't expired (5 minutes)
- Verify both devices use same user email

### "No events recorded"
- Ensure system permissions are granted
- Check `device_events` table in Supabase
- Verify accessibility service is enabled (Android)

### Real-time not working
- Enable Realtime in Supabase settings
- Ensure tables have REPLICA IDENTITY FULL
- Check browser WebSocket connection

## Security Notes

⚠️ **For Production**:
1. Use environment variables for all secrets
2. Enable row-level security (RLS) in Supabase
3. Rotate JWT secrets regularly
4. Use OAuth for authentication (not email-only)
5. Encrypt sensitive data in transit and at rest
6. Rate limit API endpoints

## Next Steps

1. ✅ Setup Supabase
2. ✅ Deploy backend
3. ✅ Configure Android app
4. ✅ Configure Desktop app
5. 📱 Test pairing on real devices
6. 🎯 Enhance Bron's recommendations with event data
7. 📊 Add analytics dashboard
8. 🔐 Implement OAuth

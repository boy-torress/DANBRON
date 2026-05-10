# Danbron - Multi-Platform AI Assistant

## Overview

**Danbron** is a personal AI assistant that runs on Android, Windows Desktop, and Web. It features **Bron**, a golden fox mascot, that provides personalized life coaching in financial management, habit tracking, productivity, and wellness.

### Key Features

✨ **Multi-Platform**
- Android (Kotlin + Jetpack Compose)
- Windows Desktop (Tauri + WebView2)
- Web (Static landing page)

🔗 **Device Sync System** (NEW)
- 1-to-1 device pairing (phone ↔ desktop)
- Real-time bidirectional sync
- System event tracking (apps, emails, notifications)
- Scalable to 1M+ users with free tier

🤖 **AI-Powered**
- Uses Groq (Llama 3.3) or Anthropic (Claude) APIs
- Context-aware recommendations
- Real-time chat with streaming
- Offline-capable

💼 **Adaptive System**
- Learns user profile (income, debt, goals, habits)
- Provides specific, actionable advice
- Tracks debt, savings, income, health, productivity
- 13 dashboard modules

---

## Architecture

### Components

```
danbron/
├── app/                    # Android app (Kotlin + Compose)
│   ├── src/main/java/com/danbron/app/
│   │   ├── sync/          # Device sync client
│   │   ├── system/        # System event tracking
│   │   ├── api/           # Groq/Anthropic clients
│   │   ├── data/          # Data models, persistence
│   │   ├── ui/screens/    # Compose screens
│   │   ├── viewmodel/     # State management
│   │   └── notifications/ # Background tasks
│   └── build.gradle.kts
│
├── desktop/               # Windows Desktop app (Tauri)
│   ├── src/
│   │   ├── app.js         # Main app logic
│   │   ├── sync-client.js # Sync client for Windows
│   │   ├── windows-tracker.js # System event tracking
│   │   ├── index.html     # UI
│   │   └── style.css      # Styling
│   ├── src-tauri/
│   │   ├── main.rs        # Tauri backend
│   │   ├── Cargo.toml
│   │   └── tauri.conf.json
│   └── package.json
│
├── backend/               # Node.js + Express + Supabase
│   ├── src/
│   │   ├── index.js       # Express server
│   │   ├── db.js          # Supabase client
│   │   ├── middleware/    # Auth, logging
│   │   ├── services/      # Device, sync services
│   │   └── routes/        # API endpoints
│   ├── schema.sql         # Database schema
│   ├── package.json
│   └── .env.example
│
├── web/                   # Landing page
│   ├── index.html
│   ├── privacy.html
│   └── style.css
│
└── SYNC_SETUP.md         # Detailed setup guide
```

### Database Schema (Supabase)

```sql
users              -- User profiles
├── id, email, name, created_at

devices            -- Registered devices
├── id, user_id, device_type, device_name
├── pairing_code, paired_at, last_sync

device_pairs       -- 1-to-1 pairing
├── id, user_id, device_1_id, device_2_id
├── paired_at, unpaired_at

sync_data          -- Real-time state
├── id, user_id, device_id, data (JSON)
├── updated_at

device_events      -- System events
├── id, user_id, device_id, event_type
├── event_data (JSON), timestamp
```

---

## Quick Start

### 1. Setup Backend

```bash
cd backend
npm install
cp .env.example .env
# Edit .env with Supabase credentials
npm start
```

### 2. Setup Android

1. Update `app/build.gradle.kts` with backend URL
2. Build and run on device
3. Grant permissions when prompted
4. Login with email

### 3. Setup Desktop

1. Update `desktop/src/sync-client.js` with backend URL
2. Install dependencies: `npm install` (in desktop/)
3. Build Tauri app: `cargo tauri build`
4. Launch app

### 4. Pair Devices

1. Open app on both devices
2. Click "Emparejar" on one device
3. Enter 6-digit code from other device
4. Confirm pairing

---

## Features

### Device Sync

**Bidirectional real-time sync**
- User profile (name, income, debt, goals)
- Tasks and habits
- Chat history
- Settings

**System Event Tracking**
- Android: Apps opened, emails, calendar events
- Windows: Active apps, battery status, unread emails

**Smart Recommendations**

Bron uses all this data to give specific advice:

```
"Vi que estás jugando, pero tienes 3 emails sin leer.
¿Quieres que te ayude a priorizar?"
```

vs

```
"Descansaste bien anoche (7 horas) y sin emails urgentes.
Perfecto para trabajar en ese proyecto que dejaste."
```

---

## API Endpoints

### Authentication
```
POST /api/auth
  body: { email, name? }
  response: { token, user }
```

### Device Management
```
POST /api/devices/register
  header: Authorization: Bearer token
  body: { deviceType, deviceName }
  response: { device: { id, pairingCode, ... } }

POST /api/devices/pair
  body: { pairingCode, confirmedDeviceId }
  response: { paired: true, pairing: {...} }

GET /api/devices/paired?deviceId=xxx
  response: { paired: true, device: {...} }

POST /api/devices/unpair
  body: { deviceId }
  response: { unpaired: true }
```

### Data Sync
```
POST /api/sync/data
  body: { deviceId, userData }
  response: { synced: true }

GET /api/sync/data?deviceId=xxx
  response: { data: {...} }

POST /api/sync/event
  body: { deviceId, eventType, eventData? }
  response: { recorded: true }

GET /api/sync/events?deviceId=xxx&limit=50
  response: { events: [...] }

GET /api/sync/events/since?deviceId=xxx&since=2025-01-01T00:00:00Z
  response: { events: [...] }
```

---

## Scaling to 1M+ Users

**Cost Analysis** (monthly)
- Supabase: ~$25 (500k rows included)
- Backend: ~$5 (Railway) or FREE (Render)
- **Total: ~$30/month**

**Optimizations**
- Archive events > 30 days
- Batch sync requests
- Connection pooling
- CDN for static assets

**Performance**
- Realtime updates via WebSockets
- Indexed queries on user_id, device_id, timestamp
- Pagination for large result sets

---

## Development

### Technologies

**Android**
- Kotlin
- Jetpack Compose
- Retrofit + Coroutines
- DataStore (encrypted)
- WorkManager

**Desktop**
- Tauri (Rust + WebView2)
- Vanilla JavaScript
- Shell plugin for system access
- LocalStorage for persistence

**Backend**
- Node.js + Express
- Supabase (PostgreSQL)
- JWT authentication
- Realtime PostgreSQL

**AI**
- Groq (Llama 3.3 70B)
- Anthropic (Claude Sonnet)

### Building

**Android**
```bash
cd app
./gradlew assemble
```

**Desktop**
```bash
cd desktop
cargo tauri build
```

**Backend**
```bash
cd backend
npm install
npm start
```

---

## Security

⚠️ **Production Checklist**

- [ ] Move API keys to environment variables
- [ ] Enable Supabase Row-Level Security (RLS)
- [ ] Use OAuth instead of email-only auth
- [ ] Implement rate limiting on API
- [ ] Add request signing (HMAC)
- [ ] Encrypt PII in database
- [ ] Enable HTTPS everywhere
- [ ] Add audit logging
- [ ] Regular security audits
- [ ] GDPR compliance (data export/deletion)

---

## Contributing

1. Fork repository
2. Create feature branch
3. Make changes
4. Submit pull request

---

## License

Proprietary - All rights reserved

---

## Support

- 📖 [Sync Setup Guide](./SYNC_SETUP.md)
- 🐛 Report bugs on GitHub Issues
- 💬 Join Discord community

---

## Roadmap

- [ ] Web app (responsive React)
- [ ] Apple iOS app
- [ ] Cloud backup/restore
- [ ] Multi-device sync (more than 2)
- [ ] Advanced analytics
- [ ] Integrations (Stripe, Gmail, Outlook)
- [ ] AI voice companion
- [ ] AR reminders

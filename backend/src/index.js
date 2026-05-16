require('dotenv').config();
const express = require('express');
const cors = require('cors');
const rateLimit = require('express-rate-limit');
const authRoutes = require('./routes/auth');
const deviceRoutes = require('./routes/devices');
const syncRoutes = require('./routes/sync');
const aiRoutes = require('./routes/ai');
const toolsRoutes = require('./routes/tools');

const app = express();
const PORT = process.env.PORT || 3000;
const HOST = '0.0.0.0';
const IS_PROD = process.env.NODE_ENV === 'production';

if (IS_PROD) {
  const required = ['SUPABASE_URL', 'SUPABASE_SERVICE_ROLE', 'JWT_SECRET'];
  const missing = required.filter((key) => {
    const value = process.env[key] || '';
    return !value || value.includes('your-') || value.includes('replace-');
  });
  if (missing.length) {
    throw new Error(`Missing production env vars: ${missing.join(', ')}`);
  }
}

// Middleware
app.set('trust proxy', 1);
app.disable('x-powered-by');
app.use((req, res, next) => {
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('Referrer-Policy', 'no-referrer');
  res.setHeader('Permissions-Policy', 'geolocation=(), microphone=(), camera=()');
  next();
});
const allowedOrigins = (process.env.CORS_ORIGIN || (IS_PROD ? '' : '*'))
  .split(',')
  .map((origin) => origin.trim())
  .filter(Boolean);
app.use(cors({
  origin(origin, callback) {
    if (!origin || allowedOrigins.includes('*') || allowedOrigins.includes(origin)) {
      return callback(null, true);
    }
    return callback(new Error('CORS origin denied'));
  }
}));
app.use(express.json({ limit: process.env.JSON_BODY_LIMIT || '256kb' }));

// Rate limits
const generalLimiter = rateLimit({
  windowMs: 60 * 1000,
  max: 120,
  standardHeaders: true,
  legacyHeaders: false
});

const authLimiter = rateLimit({
  windowMs: 60 * 1000,
  max: 20,
  standardHeaders: true,
  legacyHeaders: false
});

const aiLimiter = rateLimit({
  windowMs: 60 * 1000,
  max: 30,
  standardHeaders: true,
  legacyHeaders: false
});

// Routes
app.use('/api/auth', authLimiter);
app.use('/api', authRoutes);
app.use('/api/ai', aiLimiter);
app.use('/api', aiRoutes);
app.use('/api', generalLimiter);
app.use('/api', toolsRoutes);
app.use('/api', deviceRoutes);
app.use('/api', syncRoutes);

// Health check
app.get('/health', (req, res) => {
  res.json({ status: 'ok' });
});

// Error handling
app.use((err, req, res, next) => {
  console.error(err);
  const status = err.statusCode || err.status || 500;
  const message = status >= 500 && IS_PROD ? 'Internal server error' : (err.message || 'Internal server error');
  res.status(status).json({ error: message });
});

app.listen(PORT, HOST, () => {
  console.log(`Danbron backend listening on ${HOST}:${PORT}`);
});

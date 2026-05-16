const express = require('express');
const fetch = require('node-fetch');
const FormData = require('form-data');
const multer = require('multer');
const { authMiddleware } = require('../middleware/auth');

const router = express.Router();

const upload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: 12 * 1024 * 1024 }
});

const MAX_TOKENS_DEFAULT = parseInt(process.env.AI_MAX_TOKENS || '800', 10);
const TEMP_DEFAULT = parseFloat(process.env.AI_TEMPERATURE || '0.3');
const AI_GROQ_MODEL = process.env.AI_MODEL_GROQ || 'openai/gpt-oss-120b';
const AI_OPENROUTER_MODEL = process.env.AI_MODEL_OPENROUTER || 'openai/gpt-oss-120b:free';
const AI_ANTHROPIC_MODEL = process.env.AI_MODEL_ANTHROPIC || 'claude-sonnet-4-20250514';
const TRANSCRIBE_MODEL = process.env.AI_TRANSCRIBE_MODEL || 'whisper-large-v3';

function pickProvider() {
  const configured = (process.env.AI_PROVIDER || '').toLowerCase();
  if (configured) return configured;
  if (process.env.OPENROUTER_API_KEY) return 'openrouter';
  if (process.env.GROQ_API_KEY) return 'groq';
  if (process.env.ANTHROPIC_API_KEY) return 'anthropic';
  return '';
}

function clampNumber(value, min, max, fallback) {
  const n = Number(value);
  if (Number.isNaN(n)) return fallback;
  return Math.max(min, Math.min(max, n));
}

function sanitizeMessages(messages) {
  return messages
    .filter((m) => m && m.role && m.content)
    .map((m) => ({
      role: String(m.role),
      content: String(m.content)
    }));
}

router.post('/ai/chat', authMiddleware, async (req, res) => {
  try {
    const { messages, systemPrompt, maxTokens, temperature } = req.body || {};

    if (!Array.isArray(messages) || messages.length === 0) {
      return res.status(400).json({ error: 'messages required' });
    }

    const provider = pickProvider();
    if (!provider) {
      return res.status(500).json({ error: 'AI provider not configured' });
    }

    const safeMaxTokens = clampNumber(maxTokens, 64, 1200, MAX_TOKENS_DEFAULT);
    const safeTemp = clampNumber(temperature, 0, 1.2, TEMP_DEFAULT);
    const cleanedMessages = sanitizeMessages(messages);

    if (cleanedMessages.length === 0) {
      return res.status(400).json({ error: 'messages invalid' });
    }

    if (provider === 'groq') {
      const apiKey = process.env.GROQ_API_KEY;
      if (!apiKey) return res.status(500).json({ error: 'GROQ_API_KEY missing' });

      const groqMessages = systemPrompt
        ? [{ role: 'system', content: String(systemPrompt) }, ...cleanedMessages]
        : cleanedMessages;

      const payload = {
        model: AI_GROQ_MODEL,
        max_tokens: safeMaxTokens,
        temperature: safeTemp,
        messages: groqMessages
      };

      const response = await fetch('https://api.groq.com/openai/v1/chat/completions', {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${apiKey}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(payload)
      });

      if (!response.ok) {
        const detail = await response.text();
        return res.status(response.status).json({
          error: `Groq error ${response.status}`,
          detail: detail.slice(0, 500)
        });
      }

      const data = await response.json();
      const text = data?.choices?.[0]?.message?.content || '';
      return res.json({ text, provider: 'groq' });
    }

    if (provider === 'openrouter') {
      const apiKey = process.env.OPENROUTER_API_KEY;
      if (!apiKey) return res.status(500).json({ error: 'OPENROUTER_API_KEY missing' });

      const openRouterMessages = systemPrompt
        ? [{ role: 'system', content: String(systemPrompt) }, ...cleanedMessages]
        : cleanedMessages;

      const payload = {
        model: AI_OPENROUTER_MODEL,
        max_tokens: safeMaxTokens,
        temperature: safeTemp,
        messages: openRouterMessages
      };

      const response = await fetch('https://openrouter.ai/api/v1/chat/completions', {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${apiKey}`,
          'Content-Type': 'application/json',
          'HTTP-Referer': process.env.OPENROUTER_SITE_URL || 'https://danbron.local',
          'X-Title': process.env.OPENROUTER_APP_NAME || 'Danbron'
        },
        body: JSON.stringify(payload)
      });

      if (!response.ok) {
        const detail = await response.text();
        return res.status(response.status).json({
          error: `OpenRouter error ${response.status}`,
          detail: detail.slice(0, 500)
        });
      }

      const data = await response.json();
      const text = data?.choices?.[0]?.message?.content || '';
      return res.json({ text, provider: 'openrouter', model: AI_OPENROUTER_MODEL });
    }

    if (provider === 'anthropic') {
      const apiKey = process.env.ANTHROPIC_API_KEY;
      if (!apiKey) return res.status(500).json({ error: 'ANTHROPIC_API_KEY missing' });

      const payload = {
        model: AI_ANTHROPIC_MODEL,
        max_tokens: safeMaxTokens,
        system: systemPrompt ? String(systemPrompt) : '',
        messages: cleanedMessages
      };

      const response = await fetch('https://api.anthropic.com/v1/messages', {
        method: 'POST',
        headers: {
          'x-api-key': apiKey,
          'anthropic-version': '2023-06-01',
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(payload)
      });

      if (!response.ok) {
        const detail = await response.text();
        return res.status(response.status).json({
          error: `Anthropic error ${response.status}`,
          detail: detail.slice(0, 500)
        });
      }

      const data = await response.json();
      const text = data?.content?.[0]?.text || '';
      return res.json({ text, provider: 'anthropic' });
    }

    return res.status(400).json({ error: 'Unsupported AI provider' });
  } catch (error) {
    console.error('AI chat error:', error);
    return res.status(500).json({ error: 'AI chat failed' });
  }
});

router.post('/ai/transcribe', authMiddleware, upload.single('file'), async (req, res) => {
  try {
    const provider = pickProvider();
    if (provider !== 'groq') {
      return res.status(400).json({ error: 'Transcription only supported on Groq' });
    }

    const apiKey = process.env.GROQ_API_KEY;
    if (!apiKey) return res.status(500).json({ error: 'GROQ_API_KEY missing' });

    if (!req.file) {
      return res.status(400).json({ error: 'file required' });
    }

    const form = new FormData();
    form.append('file', req.file.buffer, {
      filename: req.file.originalname || 'audio.mp4',
      contentType: req.file.mimetype || 'audio/mp4'
    });
    form.append('model', TRANSCRIBE_MODEL);

    const response = await fetch('https://api.groq.com/openai/v1/audio/transcriptions', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${apiKey}`,
        ...form.getHeaders()
      },
      body: form
    });

    if (!response.ok) {
      const detail = await response.text();
      return res.status(response.status).json({
        error: `Groq error ${response.status}`,
        detail: detail.slice(0, 500)
      });
    }

    const data = await response.json();
    const text = data?.text || '';
    return res.json({ text, provider: 'groq' });
  } catch (error) {
    console.error('AI transcribe error:', error);
    return res.status(500).json({ error: 'AI transcribe failed' });
  }
});

module.exports = router;

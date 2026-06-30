'use strict';

/**
 * Telegram TV Cast — pairing + media-proxy backend.
 *
 * One public bot serves every user. The TV app never sees the bot token:
 *  - The TV registers and shows a short pairing code.
 *  - The user opens the public bot in Telegram and sends that code (or taps a
 *    t.me deep link), which pairs their chat to that TV.
 *  - Any video URL or video file the user then sends to the bot is pushed to
 *    the paired TV. Telegram-hosted files are streamed through /media/:id so
 *    the bot token stays server-side.
 */

const express = require('express');
const crypto = require('crypto');
const path = require('path');
const { Readable } = require('stream');

const BOT_TOKEN = process.env.BOT_TOKEN || '';
const BOT_USERNAME = process.env.BOT_USERNAME || '';
const WEBHOOK_SECRET = process.env.WEBHOOK_SECRET || 'changeme';
const RAILWAY_URL = process.env.RAILWAY_PUBLIC_DOMAIN ? `https://${process.env.RAILWAY_PUBLIC_DOMAIN}` : '';
const PUBLIC_URL = (process.env.PUBLIC_URL || process.env.RENDER_EXTERNAL_URL || RAILWAY_URL || '').replace(/\/+$/, '');
const PORT = parseInt(process.env.PORT || '8080', 10);
const PAIR_TTL_MS = 15 * 60 * 1000; // unpaired codes expire after 15 min
const POLL_TIMEOUT_MS = 25 * 1000;

const TG = `https://api.telegram.org/bot${BOT_TOKEN}`;

// ---- in-memory state (swap for Redis/DB to scale horizontally) ----
const devices = new Map(); // deviceId -> { code, chatId, queue:[], waiters:[], createdAt }
const codeToDevice = new Map(); // CODE -> deviceId
const chatToDevice = new Map(); // chatId -> deviceId
const media = new Map(); // opaqueId -> { filePath, createdAt }

const app = express();
app.use(express.json({ limit: '1mb' }));

function genId() {
  return crypto.randomBytes(16).toString('hex');
}

// Unambiguous code alphabet (no 0/O/1/I).
const CODE_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
function genCode() {
  let code = '';
  for (let i = 0; i < 6; i++) {
    code += CODE_ALPHABET[crypto.randomInt(CODE_ALPHABET.length)];
  }
  return codeToDevice.has(code) ? genCode() : code;
}

async function tg(method, params) {
  const resp = await fetch(`${TG}/${method}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(params),
  });
  return resp.json();
}

function send(chatId, text) {
  return tg('sendMessage', { chat_id: chatId, text }).catch(() => {});
}

function pushCommand(deviceId, command) {
  const device = devices.get(deviceId);
  if (!device) return;
  const waiter = device.waiters.shift();
  if (waiter) {
    clearTimeout(waiter.timer);
    waiter.resolve({ paired: !!device.chatId, command });
  } else {
    device.queue.push(command);
  }
}

function notifyPaired(deviceId) {
  const device = devices.get(deviceId);
  if (!device) return;
  const waiter = device.waiters.shift();
  if (waiter) {
    clearTimeout(waiter.timer);
    waiter.resolve({ paired: true, command: null });
  }
}

// ---- health ----
app.get('/', (_req, res) => res.json({ ok: true, service: 'telegram-tv-cast' }));

// ---- APK download (for sideloading on a TV via the Downloader app) ----
app.get(['/app', '/app.apk'], (_req, res) => {
  res.setHeader('Content-Disposition', 'attachment; filename="telegram-tv-cast.apk"');
  res.sendFile(path.join(__dirname, 'public', 'telegram-tv-cast.apk'), (err) => {
    if (err && !res.headersSent) res.status(404).send('APK not found');
  });
});

// ---- TV: register a device, get a pairing code ----
app.post('/api/register', (_req, res) => {
  const deviceId = genId();
  const code = genCode();
  devices.set(deviceId, { code, chatId: null, queue: [], waiters: [], createdAt: Date.now() });
  codeToDevice.set(code, deviceId);
  // premium entitlement is a stub for now (freemium scaffold); wire to billing later.
  res.json({ deviceId, code, botUsername: BOT_USERNAME, premium: false });
});

// ---- TV: long-poll for the next command / pairing status ----
app.get('/api/poll', (req, res) => {
  const deviceId = String(req.query.deviceId || '');
  const device = devices.get(deviceId);
  if (!device) {
    return res.status(404).json({ error: 'unknown device, re-register' });
  }
  if (device.queue.length > 0) {
    return res.json({ paired: !!device.chatId, command: device.queue.shift() });
  }
  const waiter = {
    resolve: (payload) => res.json(payload),
    timer: setTimeout(() => {
      const idx = device.waiters.indexOf(waiter);
      if (idx >= 0) device.waiters.splice(idx, 1);
      res.json({ paired: !!device.chatId, command: null });
    }, POLL_TIMEOUT_MS),
  };
  device.waiters.push(waiter);
  req.on('close', () => {
    clearTimeout(waiter.timer);
    const idx = device.waiters.indexOf(waiter);
    if (idx >= 0) device.waiters.splice(idx, 1);
  });
});

// ---- Telegram webhook ----
function extractFileId(message) {
  if (message.video) return message.video.file_id;
  if (message.document && String(message.document.mime_type || '').startsWith('video/')) {
    return message.document.file_id;
  }
  if (message.animation) return message.animation.file_id;
  return null;
}

async function resolveMediaUrl(fileId) {
  const result = await tg('getFile', { file_id: fileId });
  if (!result.ok) throw new Error(result.description || 'getFile failed');
  const opaqueId = genId();
  media.set(opaqueId, { filePath: result.result.file_path, createdAt: Date.now() });
  return `${PUBLIC_URL}/media/${opaqueId}`;
}

app.post('/telegram/webhook/:secret', async (req, res) => {
  const headerSecret = req.get('X-Telegram-Bot-Api-Secret-Token');
  if (req.params.secret !== WEBHOOK_SECRET && headerSecret !== WEBHOOK_SECRET) {
    return res.sendStatus(403);
  }
  res.sendStatus(200); // ack immediately; process async
  try {
    const message = (req.body && (req.body.message || req.body.channel_post)) || null;
    if (!message || !message.chat) return;
    const chatId = message.chat.id;
    const text = String(message.text || message.caption || '').trim();

    // pairing: "/start CODE" or a bare code
    let code = null;
    if (text.toLowerCase().startsWith('/start')) {
      code = text.split(/\s+/)[1] || null;
    } else if (/^[A-Za-z0-9]{6}$/.test(text) && codeToDevice.has(text.toUpperCase())) {
      code = text;
    }
    if (code) {
      const deviceId = codeToDevice.get(code.toUpperCase());
      if (!deviceId || !devices.has(deviceId)) {
        return void send(chatId, 'That pairing code is invalid or expired. Open the TV app for a fresh code.');
      }
      const device = devices.get(deviceId);
      device.chatId = chatId;
      chatToDevice.set(chatId, deviceId);
      notifyPaired(deviceId);
      return void send(chatId, '✅ Paired! Now send me a video link or a video file and it will play on your TV.');
    }

    // content: must come from a paired chat
    const deviceId = chatToDevice.get(chatId);
    if (!deviceId || !devices.has(deviceId)) {
      return void send(chatId, 'Send me the 6-character code shown on your TV to pair first.');
    }

    const lower = text.toLowerCase();
    if (lower.startsWith('/pause')) return void (pushCommand(deviceId, { type: 'pause' }), send(chatId, '⏸ Paused.'));
    if (lower.startsWith('/resume') || lower === '/play') {
      return void (pushCommand(deviceId, { type: 'resume' }), send(chatId, '▶️ Resumed.'));
    }
    if (lower.startsWith('/stop')) return void (pushCommand(deviceId, { type: 'stop' }), send(chatId, '⏹ Stopped.'));

    const urlMatch = text.match(/https?:\/\/[^\s)\]}>"]+/);
    if (urlMatch) {
      pushCommand(deviceId, { type: 'play', url: urlMatch[0], label: urlMatch[0] });
      return void send(chatId, '📺 Casting your link to the TV.');
    }

    const fileId = extractFileId(message);
    if (fileId) {
      try {
        const url = await resolveMediaUrl(fileId);
        const label = (message.video && 'video') || (message.document && message.document.file_name) || 'video';
        pushCommand(deviceId, { type: 'play', url, label });
        return void send(chatId, '📺 Casting your video to the TV.');
      } catch (e) {
        return void send(chatId, 'Could not fetch that file from Telegram. Try a smaller file or a direct link.');
      }
    }

    send(chatId, 'Send a video link or a video file, or use /pause, /resume, /stop.');
  } catch (_e) {
    // already acked; swallow
  }
});

// ---- media proxy: stream Telegram file bytes without exposing the bot token ----
app.get('/media/:id', async (req, res) => {
  const entry = media.get(req.params.id);
  if (!entry) return res.sendStatus(404);
  const upstream = `https://api.telegram.org/file/bot${BOT_TOKEN}/${entry.filePath}`;
  const headers = {};
  if (req.headers.range) headers.Range = req.headers.range;
  try {
    const r = await fetch(upstream, { headers });
    res.status(r.status);
    ['content-type', 'content-length', 'content-range', 'accept-ranges'].forEach((h) => {
      const v = r.headers.get(h);
      if (v) res.setHeader(h, v);
    });
    if (!r.headers.get('content-type')) res.setHeader('Content-Type', 'video/mp4');
    if (r.body) Readable.fromWeb(r.body).pipe(res);
    else res.end();
  } catch (_e) {
    res.sendStatus(502);
  }
});

// ---- periodic cleanup of expired unpaired codes & stale media ----
setInterval(() => {
  const now = Date.now();
  for (const [deviceId, device] of devices) {
    if (!device.chatId && now - device.createdAt > PAIR_TTL_MS) {
      codeToDevice.delete(device.code);
      devices.delete(deviceId);
    }
  }
  for (const [id, m] of media) {
    if (now - m.createdAt > 6 * 60 * 60 * 1000) media.delete(id);
  }
}, 60 * 1000).unref();

async function autoRegisterWebhook() {
  if (!BOT_TOKEN || !PUBLIC_URL) return;
  try {
    const url = `${PUBLIC_URL}/telegram/webhook/${WEBHOOK_SECRET}`;
    const result = await tg('setWebhook', {
      url,
      secret_token: WEBHOOK_SECRET,
      allowed_updates: ['message', 'channel_post'],
    });
    console.log(`setWebhook -> ${url}: ${result.ok ? 'ok' : JSON.stringify(result)}`);
  } catch (e) {
    console.warn('setWebhook failed:', e.message);
  }
}

app.listen(PORT, () => {
  console.log(`telegram-tv-cast server listening on :${PORT}`);
  if (!BOT_TOKEN) console.warn('WARNING: BOT_TOKEN is not set');
  if (!PUBLIC_URL) console.warn('WARNING: PUBLIC_URL is not set (media proxy URLs will be relative)');
  autoRegisterWebhook();
});

module.exports = app;

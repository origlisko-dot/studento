'use strict';
// Registers the Telegram webhook for the public bot.
// Usage: BOT_TOKEN=... PUBLIC_URL=https://your.host WEBHOOK_SECRET=... node set-webhook.js
const BOT_TOKEN = process.env.BOT_TOKEN;
const PUBLIC_URL = (process.env.PUBLIC_URL || '').replace(/\/+$/, '');
const WEBHOOK_SECRET = process.env.WEBHOOK_SECRET || 'changeme';
if (!BOT_TOKEN || !PUBLIC_URL) {
  console.error('Set BOT_TOKEN and PUBLIC_URL'); process.exit(1);
}
(async () => {
  const url = `${PUBLIC_URL}/telegram/webhook/${WEBHOOK_SECRET}`;
  const r = await fetch(`https://api.telegram.org/bot${BOT_TOKEN}/setWebhook`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ url, secret_token: WEBHOOK_SECRET, allowed_updates: ['message', 'channel_post'] }),
  });
  console.log(JSON.stringify(await r.json(), null, 2));
})();

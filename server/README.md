# Telegram TV Cast — backend server

Pairing + media-proxy backend for the one **public** Telegram bot. The bot token
lives here, never in the Android app.

## What it does

- Receives Telegram webhook updates for the public bot.
- Pairs each Telegram chat to a TV using a short code (`/start <code>`, a bare
  code, or the QR deep link).
- Pushes `play` / `pause` / `resume` / `stop` commands to the paired TV via a
  long-poll endpoint.
- Streams Telegram-hosted files through `/media/:id` so the bot token is never
  exposed to the device.

## Endpoints

| Method | Path | Used by | Purpose |
|--------|------|---------|---------|
| `GET`  | `/` | — | Health check |
| `POST` | `/api/register` | TV | Create a device, return `{deviceId, code, botUsername}` |
| `GET`  | `/api/poll?deviceId=` | TV | Long-poll (~25s) for `{paired, command}` |
| `POST` | `/telegram/webhook/:secret` | Telegram | Webhook (pairing + content) |
| `GET`  | `/media/:id` | TV/ExoPlayer | Range-aware proxy stream of a Telegram file |

## Configuration

Copy `.env.example` to `.env` (or set env vars in your host):

| Var | Required | Meaning |
|-----|----------|---------|
| `BOT_TOKEN` | yes | Public bot token from BotFather |
| `BOT_USERNAME` | yes | Bot @username (no `@`), shown on the TV + used in the QR deep link |
| `WEBHOOK_SECRET` | yes | Long random string; used in the webhook path and Telegram secret header |
| `PUBLIC_URL` | yes | Public HTTPS base URL of this server (used to build `/media` URLs) |
| `PORT` | no | Listen port (default 8080) |

## Run locally

```bash
npm install
BOT_TOKEN=... BOT_USERNAME=YourCastBot WEBHOOK_SECRET=secret PUBLIC_URL=https://your-host npm start
```

Telegram requires an HTTPS webhook. For local testing expose the port with a
tunnel (e.g. `cloudflared tunnel --url http://localhost:8080` or `ngrok http
8080`) and use that HTTPS URL as `PUBLIC_URL`.

Register the webhook once (re-run if `PUBLIC_URL`/secret change):

```bash
npm run set-webhook
```

## Deploy

Any Node 18+ host works (Render, Railway, Fly.io, a VPS, etc.). A `Dockerfile`
is included:

```bash
docker build -t tg-tv-cast-server .
docker run -p 8080:8080 --env-file .env tg-tv-cast-server
```

After deploying, point the Android app at this server via `server.url` in the
app's `local.properties` and rebuild.

## Scaling notes

State is in-memory (Map-based), which is fine for a single instance. To run
multiple instances or survive restarts, move `devices` / `codeToDevice` /
`chatToDevice` / `media` into Redis or a database, and use a shared pub/sub (or
Redis blocking pop) to wake the long-poll waiters.

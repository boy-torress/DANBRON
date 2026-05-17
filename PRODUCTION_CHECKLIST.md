# Danbron Production Checklist

## Required Before Public Launch

- Set `NODE_ENV=production`.
- Set `JWT_SECRET` to a random 32+ character secret.
- Set explicit `CORS_ORIGIN` values. Do not use `*`.
- Configure `SUPABASE_URL` and `SUPABASE_SERVICE_ROLE` in backend env only.
- Configure at least one AI provider key in backend env only.
- Run `npm --prefix backend run prod:check`.
- Run `npm --prefix backend run check`.
- Run `cargo check` from `desktop/src-tauri`.
- Run `gradlew.bat :app:compileReleaseKotlin` or release build.

## Skill Runtime Setup

- Install `gog` only on trusted machines where Google OAuth is configured.
- For Android-with-PC-off mode, install `gog` on the backend host too and set:
  - `GOG_BIN=gog`
  - `GOG_ACCOUNT=brandontorres.dev@gmail.com`
  - copy the Gog credential/token store from a secure machine or re-run `gog auth credentials set` and `gog auth add` on the backend host.
- Install `maton` only where `MATON_API_KEY` is available in a secure environment.
- Install `agent-browser` with `npm install -g agent-browser` and `agent-browser install`.
- Keep runtime credentials out of desktop source and Android source.

## Security

- Enable Supabase Row Level Security if you expose anon/public keys to clients.
- Run `backend/production_hardening.sql` after `backend/schema.sql`.
- Rotate pairing codes frequently and keep the 10 minute TTL.
- Keep remote commands restricted to allowed actions in `backend/src/utils/validation.js`.
- Monitor `/api/sync/event` rates and command result failures.
- Archive old `device_events` after 30 days.

## Release Hygiene

- Do not commit `desktop/src-tauri/target/`, installer binaries, local env files, or API keys.
- Build installers from a clean checkout.
- Smoke test Android + Desktop pairing against the production backend.
- Smoke test: briefing, open app, compose email, calendar event, maps, music.

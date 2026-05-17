# Agent Browser

Source: https://clawhub.ai/matrixy/agent-browser-clawdbot

## Description

Headless browser automation skill optimized for AI agents. It uses accessibility tree snapshots and ref-based element selection for deterministic browser workflows.

## Capabilities

- Open URLs.
- Take interactive accessibility snapshots.
- Click, fill, type, hover, check, select, press keys, scroll, and drag using refs.
- Wait for elements, text, URLs, load states, or JS conditions.
- Manage isolated browser sessions.
- Save/load browser state.
- Capture screenshots and PDFs.
- Inspect network, cookies, storage, tabs, and frames.

## External Runtime

The upstream skill uses the `agent-browser` CLI:

```bash
npm install -g agent-browser
agent-browser install
```

## Danbron Adapter

When `agent-browser` is installed, Danbron may delegate browser workflows to it. Good triggers:

- "automatiza esta pagina"
- "abre el navegador agente"
- "rellena este formulario"
- "haz click en"
- "navega a"
- "saca snapshot"
- "guarda estado del navegador"

## Safety

- User marked this skill as trusted.
- Prefer isolated sessions for logins and account workflows.
- Use snapshots before interacting with complex pages.
- Keep screenshots/PDFs local unless explicitly asked to share.

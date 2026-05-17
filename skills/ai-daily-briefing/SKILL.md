# AI Daily Briefing

Source: https://clawhub.ai/jeffjhunter/ai-daily-briefing

## Description

Morning briefing skill. Use when the user asks for "briefing", "daily briefing", "morning briefing", "what's on my plate", "start my day", or "what do I need to know".

## Capabilities

- Summarize overdue items.
- Identify today's priorities.
- Show calendar overview when available.
- Include context from recent notes, meetings, memory, and PC/phone signals.
- End with one focus statement.

## Danbron Adapter

Danbron should gather from:

- Local notes in app state.
- Synced user profile.
- Windows visible context.
- Recent device events.
- Future Google Calendar integration when OAuth is available.

## Safety

- Briefing is read-only.
- Do not create tasks/events during a briefing unless the user asks.

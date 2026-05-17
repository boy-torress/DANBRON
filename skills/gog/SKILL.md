# Gog

Source: https://clawhub.ai/steipete/gog

Google Workspace skill for Danbron, inspired by the OpenClaw `gog` skill.

## Description

Use this skill when the user asks Danbron to work with Gmail, Google Calendar, Google Drive, Google Docs, Google Sheets, Google Contacts, or Google Maps.

## Capabilities

- Open Gmail.
- Prepare Gmail drafts with optional recipient, subject, and body.
- Open Google Calendar.
- Prepare Google Calendar event drafts.
- Open Google Drive, Docs, Sheets, Contacts, and Maps.
- Search Google Drive, Gmail, Maps, or Google web results.

## Safety

- Upstream ClawHub currently marks this skill suspicious, so Danbron uses a restricted adapter instead of arbitrary CLI execution.
- Do not send emails automatically.
- Do not delete, move, or share files automatically.
- Do not create calendar events without leaving the final confirmation to the user.
- Prefer opening a prepared Google UI draft over executing irreversible actions.

## Action Mapping

- `compose_email`: prepare Gmail compose URL.
- `create_calendar_event`: prepare Google Calendar event URL.
- `open_workspace_app`: open a Google Workspace app.
- `search_workspace`: open Google search, Gmail search, Drive search, or Maps search.

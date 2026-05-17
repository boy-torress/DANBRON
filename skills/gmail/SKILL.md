# Gmail

Source: https://clawhub.ai/byungkyu/gmail

## Description

Gmail API integration skill. Use when the user wants to read, search, summarize, draft, reply, label, archive, or send email.

## Capabilities

- List/search Gmail messages.
- Get messages and threads.
- Create drafts.
- Reply or forward.
- Manage labels.
- Send messages after explicit approval.

## External Runtime

The upstream skill uses Maton-managed OAuth and `MATON_API_KEY`.

## Danbron Adapter

Current safe fallback:

- Open Gmail.
- Prepare Gmail compose drafts through a browser URL.
- Never auto-send.

Future adapter:

- Add backend route for Maton/Gmail API.
- Store `MATON_API_KEY` server-side only.
- Require confirmation for every write operation.

## Safety

- All write operations require explicit user approval.
- Prefer draft creation over direct send.
- Never expose API keys in desktop or Android clients.

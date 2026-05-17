# API Gateway

Source: https://clawhub.ai/byungkyu/api-gateway

## Description

Managed API routing pattern for third-party services through Maton-style routes.

## Capabilities

- Connect external services.
- List/read resources.
- Execute approved API changes.
- Manage OAuth/API-key backed connections.

## Danbron Adapter

Future backend module:

- Store service credentials on backend only.
- Add `/api/tools/connections`.
- Add `/api/tools/proxy`.
- Enforce read-first protocol.
- Require approval for non-GET requests.

## Safety

- Only invoke after the user names the exact app, account, and task.
- Start with read-only calls.
- For POST, PUT, PATCH, or DELETE, show endpoint, body, expected effect, and wait for approval.

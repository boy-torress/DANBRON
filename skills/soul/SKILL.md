# Soul

Source: https://clawhub.ai/plugins/openclaw-soul-plugin

## Description

Proactive memory, reflection, and autonomous thinking layer inspired by OpenClaw Soul.

## Capabilities

- Long-term memory.
- User profile learning.
- Proactive follow-up suggestions.
- Background thought cycle.
- Opportunity detection from prior conversations.

## Danbron Adapter

This is imported as a design/runtime concept, not as executable plugin code.

Recommended Danbron modules:

- `memory-store`: facts, preferences, goals, relationships.
- `thought-loop`: low-frequency background reflection.
- `value-gate`: only notify when useful.
- `permission-gate`: write/exec actions disabled by default.

## Safety

- Read/reflection allowed.
- Autonomous writes, shell commands, and self-modifying code disabled unless explicitly enabled.
- Proactive messages must pass a usefulness filter.

# ANNA AC Configuration Guide

## Main files

- `plugins/ANNA-AC/config.yml`: prefix, language, alerts/Discord, detector thresholds, bypasses, and kick thresholds.
- `plugins/ANNA-AC/messages_en.yml`: English messages.
- `plugins/ANNA-AC/messages_hi.yml`: Hindi messages.
- `plugins/ANNA-AC/evidence/<uuid>.jsonl`: recorded detector/security evidence.
- `plugins/ANNA-AC/operator-baseline.txt`: saved UUID baseline for OP-change auditing.

## Prefix and language

Set the prefix in config:

    settings:
      prefix: "&8[&bANNA AC&8] &r"
      language: "en"

Supported languages are `en` (English) and `hi` (Hindi). Set `language: "hi"` to switch user-facing command, alert and Force OP messages to Hindi. If a message key is missing in the selected language, ANNA AC falls back to English. Use Minecraft color codes such as `&b`, `&c`, `&l`, and `&r`. After editing, run `/annaac reload` or restart.

## Recommended safe starting posture

- Leave experimental detector `checks.<check>.enabled` values false on the live public server until staging tests pass.
- Keep `punishments.actions.<check>.enabled: false` for all checks initially. Alert first, review evidence, then enable one action only after measuring false positives.
- Keep `punishments.enabled: true` if you want the global master switch available; per-check action flags remain the second gate.
- Keep creative and spectator exemptions, and add any server-specific worlds/permissions to ignored lists only after reviewing the effect.
- Install the compatible PacketEvents build if you want the optional packet-rate/timer telemetry. Without it, packet telemetry is unavailable.
- Force OP auditing is enabled by default, but the command-prefix list must match the permission plugins and aliases actually installed. It cannot prevent host-panel/file/console compromise or malicious plugins.

## Checks currently present

| Check key | Purpose | Default |
|---|---|---|
| `movement` | Basic displacement heuristic | Disabled |
| `movement-physics` | Repeated movement-envelope outlier heuristic | Disabled |
| `knockback` | Movement after server velocity | Disabled |
| `reach` | Approximate target hitbox distance | Disabled |
| `click-rate` | Arm-swing rate telemetry | Disabled |
| `combat-pattern` | Rotation/hit-pattern telemetry | Disabled |
| `scaffold-signal` | Repeated placement-angle pattern | Disabled |
| `no-fall` | Fall/landing damage correlation | Disabled |
| `packet-rate` | PacketEvents rate telemetry | Disabled |
| `timer` | Sustained movement packet-rate telemetry | Disabled |
| `force-op` | Privilege command and operator-list auditing | Enabled |

These are signals, not guaranteed identification of Meteor, Wurst, Baritone, or any other client. Several are experimental heuristics. See `CHEAT_COVERAGE.md` for the current/planned coverage split.

## Reload and permissions

- `/annaac alerts`: toggle your own staff alerts; requires `annaac.alerts`.
- `/annaac check <player> [check]`: view violation level; requires `annaac.admin`.
- `/annaac history <player>`: inspect recent online-player evidence; requires `annaac.admin`.
- `/annaac debug <player>`: view basic runtime data; requires `annaac.admin`.
- `/annaac reload`: reload config and selected language messages; requires `annaac.admin`.
- `annaac.bypass` and `annaac.bypass.<check>` bypass detector processing. Give only to trusted staff/test accounts.

## Discord

Follow [DISCORD_SETUP.md](DISCORD_SETUP.md) to create a webhook and configure secure outbound alerts. A webhook URL must be configured by the server owner; it is intentionally not included in the repository.

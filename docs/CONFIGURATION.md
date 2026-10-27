# ANNA AC Configuration Guide

## Main files

- `plugins/ANNA-AC/config.yml`: global prefix, language, debug option, and global ignored worlds.
- `plugins/ANNA-AC/checks.yml`: every gameplay detector and its thresholds, exemptions, alert cooldowns, and violation settings.
- `plugins/ANNA-AC/discord.yml`: console/staff alert toggles and all Discord webhook/embed settings.
- `plugins/ANNA-AC/database.yml`: evidence storage directory, retention period, record cap, detail length, and in-memory violation retention.
- `plugins/ANNA-AC/punishments.yml`: global kick switch, independent-signal correlation safety controls, kick message, and per-check actions.
- `plugins/ANNA-AC/security.yml`: anti-nuke bulk-command protection, explosion limits, optional block-break rate limiting, plugin JAR hashes, lifecycle auditing, and static malware-risk scanning.
- `plugins/ANNA-AC/messages_en.yml` and `messages_hi.yml`: English and Hindi message text.
- `plugins/ANNA-AC/messages_en.yml`: English messages.
- `plugins/ANNA-AC/messages_hi.yml`: Hindi messages.
- `plugins/ANNA-AC/evidence/<uuid>.jsonl`: recorded detector/security evidence.
- `plugins/ANNA-AC/operator-baseline.txt`: saved UUID baseline for OP-change auditing.

## Prefix and language

Set the prefix and language in `config.yml`:

    settings:
      prefix: "&8[&bANNA AC&8] &r"
      language: "en"

Supported languages are `en` (English) and `hi` (Hindi). Set `language: "hi"` to switch user-facing command, alert and Force OP messages to Hindi. If a message key is missing in the selected language, ANNA AC falls back to English. Use Minecraft color codes such as `&b`, `&c`, `&l`, and `&r`. After editing, run `/annaac reload` or restart.

## Recommended safe starting posture

- Edit `checks.<check>.enabled` in `checks.yml`; leave experimental detectors disabled on the live public server until staging tests pass.
- Keep `punishments.actions.<check>.enabled: false` in `punishments.yml` for all checks initially. Alert first, review evidence, then enable one action only after measuring false positives.
- Keep `punishments.enabled: true` in `punishments.yml` if you want the global master switch available; per-check action flags remain the second gate.
- Keep `punishments.require-independent-families: true` on a public server. By default, an automatic kick requires repeated flags from at least two distinct detector families inside `correlation-window-ms`, with at least two signals per family. If you enable only one family, the plugin will warn that kicks cannot trigger until you lower the family threshold or enable a second family.
- Keep creative and spectator exemptions, and add any server-specific worlds/permissions to ignored lists only after reviewing the effect.
- Install the compatible PacketEvents build if you want the optional packet-rate/timer telemetry and click-rate attack packet signal. Without it, packet telemetry is unavailable.
- Force OP auditing is enabled by default, but the command-prefix list must match the permission plugins and aliases actually installed. It cannot prevent host-panel/file/console compromise or malicious plugins.

## Checks currently present

| Check key in `checks.yml` | Purpose | Default |
|---|---|---|
| `movement` | Basic displacement heuristic | Disabled |
| `movement-physics` | Repeated movement-envelope outlier heuristic | Disabled |
| `flight` | Sustained airborne hover heuristic | Disabled |
| `knockback` | Movement after server velocity | Disabled |
| `reach` | Approximate target hitbox distance | Disabled |
| `mace-damage` | Implausible Mace damage signal; configurable allowance/tolerance | Disabled |
| `wall-hit` | Multi-point hitbox occlusion signal; not latency-compensated | Disabled |
| `fast-break` | Paper break-progress estimate versus observed mining time | Disabled |
| `nuker-burst` | Repeated rolling-window block-break burst | Disabled |
| `click-rate` | Arm-swing and optional PacketEvents attack-packet rate telemetry | Disabled |
| `combat-pattern` | Rotation/hit-pattern telemetry | Disabled |
| `scaffold-signal` | Repeated placement-angle pattern | Disabled |
| `no-fall` | Fall/landing damage correlation | Disabled |
| `packet-rate` | PacketEvents rate telemetry | Disabled |
| `timer` | Sustained movement packet-rate telemetry | Disabled |
| `force-op` | Privilege command and operator-list auditing | Enabled |

These are signals, not guaranteed identification of Meteor, Wurst, Baritone, or any other client. Several are experimental heuristics. See `CHEAT_COVERAGE.md` for the current/planned coverage split.

## Per-check punishment actions

Edit `punishments.yml` under `punishments.actions.<check>`. Every action is disabled by default.

- `enabled`: master per-check action toggle; it must be true alongside global `punishments.enabled` and the detector itself.
- `type`: `KICK` disconnects the player, `WARN` sends a configured warning, `COMMAND` dispatches configured console commands, and `NONE` records no action.
- `threshold-vl`: independent violation-level threshold for the action.
- `cooldown-ms`: minimum delay before the same player's check can trigger the action again.
- `message`: custom KICK/WARN message. Available placeholders: `%player%`, `%uuid%`, `%check%`, and `%vl%`.
- `commands`: used only by `COMMAND`. These run as console, so enter only trusted commands you deliberately want ANNA AC to execute. The same placeholders are supported. An empty list does nothing and generates a configuration warning.

Automatic kicks additionally require the configured number of independent detector families, with repeated signals per family. This is a false-positive safety gate, not proof of a cheat. Keep detector actions off until staging tests and staff review are complete.

## Reload and permissions

- `/annaac status`: show enabled detectors, PacketEvents availability, Discord configuration, anti-nuke/plugin-monitor switches, automatic-action state, and configuration warnings; requires `annaac.admin`.
- `/annaac securityscan`: run a manual plugin JAR hash and static-risk scan; requires `annaac.admin`.
- `/annaac securityhistory`: view the latest ten system-level anti-nuke/plugin integrity evidence records; requires `annaac.admin`.
- `/annaac securityscan approve`: replace the plugin hash baseline after manual review. Console/RCON can run it; a player needs both `annaac.admin` and `annaac.security.baseline-approve`.
- `/annaac alerts`: toggle your own staff alerts; requires `annaac.alerts`.
- `/annaac check <player> [check]`: view violation level; requires `annaac.admin`.
- `/annaac history <player>`: inspect recent online-player evidence; requires `annaac.admin`.
- `/annaac debug <player>`: view basic runtime data; requires `annaac.admin`.
- `/annaac reload`: reload `config.yml`, `checks.yml`, `discord.yml`, `database.yml`, `punishments.yml`, and selected language messages; requires `annaac.admin`.
- `annaac.bypass` and `annaac.bypass.<check>` bypass detector processing. Give only to trusted staff/test accounts.

## Anti-nuke and plugin integrity

See [SECURITY_PROTECTION.md](SECURITY_PROTECTION.md) for each `security.yml` option, safe bypass permissions, the first-run hash baseline, manual scans, and how to review/approve an updated baseline. Bulk destructive commands are blocked for players unless they have a configured bypass permission. Large block-damaging explosions above the configured cap are cancelled; the optional mining-rate limit is disabled by default.

The static plugin-risk scan only identifies suspicious combinations of class/resource strings. It can miss obfuscated or runtime-only behavior and can flag legitimate plugins. It never automatically removes or disables plugins. The first hash inventory must be reviewed; do not treat it as an already trusted allowlist.

## Discord

Follow [DISCORD_SETUP.md](DISCORD_SETUP.md) to create a webhook and edit `discord.yml` to configure secure outbound alerts. A webhook URL must be configured by the server owner; it is intentionally not included in the repository.

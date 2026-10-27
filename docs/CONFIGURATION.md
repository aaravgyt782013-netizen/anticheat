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
- Force OP auditing is enabled by default. In `checks.yml`, tune both `checks.force-op.protected-command-prefixes` (dedicated OP/deop roots) and `checks.force-op.protected-command-patterns` (permission-mutating subcommands). Pattern `*` matches one token and `**` matches the remaining tokens. Add the aliases and syntax used by your installed permission plugin; this guard cannot stop host-panel/file/console compromise or malicious plugins.

## Checks currently present

| Check key in `checks.yml` | Purpose | Default |
|---|---|---|
| `movement` | Basic displacement heuristic | Disabled |
| `movement-physics` | Repeated movement-envelope outlier heuristic | Disabled |
| `flight` | Sustained airborne hover heuristic | Disabled |
| `jesus` | Stable surface-walk movement over deep liquid | Disabled |
| `spider-signal` | Repeated upward movement beside a solid wall | Disabled |
| `knockback` | Movement after server velocity | Disabled |
| `reach` | Approximate target hitbox distance | Disabled |
| `mace-damage` | Piecewise vanilla Java smash curve, Density allowance, extra tolerance, optional legacy formula | Disabled |
| `wall-hit` | Multi-point hitbox occlusion signal; not latency-compensated | Disabled |
| `fast-break` | Paper break-progress estimate versus observed mining time | Disabled |
| `nuker-burst` | Repeated rolling-window block-break burst | Disabled |
| `click-rate` | Arm-swing and optional PacketEvents attack-packet rate telemetry | Disabled |
| `combat-pattern` | Rotation/hit-pattern telemetry | Disabled |
| `scaffold-signal` | Repeated placement-angle pattern | Disabled |
| `no-fall` | Fall/landing damage correlation | Disabled |
| `packet-rate` | PacketEvents rate telemetry | Disabled |
| `timer` | Sustained movement packet-rate telemetry | Disabled |
| `force-op` | Alias-aware OP/deop blocking, configurable permission-mutation patterns, operator-list auditing | Enabled |

These are signals, not guaranteed identification of Meteor, Wurst, Baritone, or any other client. Several are experimental heuristics. See `CHEAT_COVERAGE.md` for the current/planned coverage split.

## Per-version movement threshold tuning

The movement signals support optional version-specific numeric overrides. This is useful when testing Paper 1.21 through 1.21.11 and keeping a separate threshold for a specific server version. The exact version wins, then the major/minor key, then the normal setting. Bukkit treats dots as YAML path separators, so write version keys with underscores:

```yaml
checks:
  movement:
    max-horizontal-distance-per-tick: 1.25
    version-overrides:
      '1_21':
        max-horizontal-distance-per-tick: 1.28
      '1_21_11':
        max-horizontal-distance-per-tick: 1.30
```

Server-version and client-protocol overrides are wired for `movement`, `movement-physics`, `flight`, `jesus`, `spider-signal`, `knockback`, `reach`, `wall-hit`, `fast-break`, `nuker-burst`, and `mace-damage`. For each check, only the numeric settings explicitly read through the version-aware resolver are affected; all other settings remain normal global settings. The example numbers are illustrative, not recommended anti-cheat defaults. Keep experimental checks disabled until staging tests confirm they do not flag legitimate movement.

## Client-protocol-aware tuning

When PacketEvents is installed, ANNA AC records the protocol release reported by each connected client. For checks whose numeric thresholds use the version-aware resolver, a client override is checked first, followed by the server's version override, then the normal threshold. If the client's version is unknown, ANNA AC safely falls back to the server version.

Use a separate `client-version-overrides` map; do not mix client values into `version-overrides`. Bukkit treats dots as path separators, so version keys must use underscores.

```yaml
checks:
  reach:
    max-distance: 3.6
    version-overrides:
      '1_21_11':
        max-distance: 3.6
    client-version-overrides:
      '1_8':
        max-distance: 3.2
      '1_21':
        max-distance: 3.6
      '1_21_11':
        max-distance: 3.6
```

These are illustrative values only, not recommended thresholds. Supported threshold fields are those explicitly read through `CheckUtil.getVersionedDouble`; adding a key for an unrelated setting has no effect. The client release name is protocol metadata, not a reliable identifier of a cheat client or proof of cheating. Client-specific overrides require a compatible PacketEvents build (use 2.11.0 or newer for 1.21.11 protocol support; the project compiles against 2.14.0). Without PacketEvents, all checks continue using the server-version/default fallback.

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

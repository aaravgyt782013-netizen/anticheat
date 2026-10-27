# ANNA AC Configuration Guide

## Main files

- `plugins/ANNA-AC/config.yml`: global prefix, language, debug option, and global ignored worlds.
- `plugins/ANNA-AC/checks-movement.yml`: basic movement, physics envelope, flight/hover, liquid-walk, spider, and knockback settings.
- `plugins/ANNA-AC/checks-combat.yml`: reach, mace damage, wall-hit, click/attack rate, and combat-pattern settings.
- `plugins/ANNA-AC/checks-world.yml`: FastBreak, Nuker burst, container looting, scaffold, and NoFall settings.
- `plugins/ANNA-AC/checks-packets.yml`: packet-rate and timer telemetry settings.
- `plugins/ANNA-AC/checks-security.yml`: Force OP and permission mutation guard settings.
- `plugins/ANNA-AC/checks.yml`: legacy migration/default template; for existing installations, edit the modular detector files above.
- `plugins/ANNA-AC/discord.yml`: console/staff alert toggles and all Discord webhook/embed settings.
- `plugins/ANNA-AC/database.yml`: evidence storage directory, retention period, record cap, detail length, and in-memory violation retention.
- `plugins/ANNA-AC/punishments.yml`: global kick switch, independent-signal correlation safety controls, kick message, and per-check actions.
- `plugins/ANNA-AC/security.yml`: anti-nuke bulk-command protection, explosion limits, optional block-break rate limiting, plugin JAR hashes, lifecycle auditing, and static malware-risk scanning.
- `plugins/ANNA-AC/messages_en.yml` and `messages_hi.yml`: English and Hindi message text.
- `plugins/ANNA-AC/evidence/<uuid>.jsonl`: recorded detector/security evidence.
- `plugins/ANNA-AC/operator-baseline.txt`: saved UUID baseline for OP-change auditing.

## Modular detector files

Detector settings are organized by subsystem to make the configuration easier to review and tune. Each option remains under its familiar `checks.<detector>.*` runtime path, but its editable file is now specific to the subsystem. On first startup with this release, any existing values from `checks.yml` are copied into the corresponding new module file. Existing settings are preserved; newly introduced options are merged from the bundled defaults without overwriting explicit values.

After the module files have been created, edit `checks-movement.yml`, `checks-combat.yml`, `checks-world.yml`, `checks-packets.yml`, and `checks-security.yml`. The old `checks.yml` is retained as a compatibility/migration source and will not override settings from a module file. Back up the plugin folder before upgrading, and run `/annaac reload` after edits.

## Prefix and language

Set the prefix and language in `config.yml`:

    settings:
      prefix: "&8[&bANNA AC&8] &r"
      language: "en"

Supported languages are `en` (English) and `hi` (Hindi). Set `language: "hi"` to switch user-facing command, alert and Force OP messages to Hindi. If a message key is missing in the selected language, ANNA AC falls back to English. Use Minecraft color codes such as `&b`, `&c`, `&l`, and `&r`. After editing, run `/annaac reload` or restart.

## Recommended safe starting posture

- Edit the detector's `enabled` value in the matching module file (for example, `checks-combat.yml` for reach or `checks-world.yml` for FastBreak). `checks.yml` is a legacy migration template once module files exist; do not use it to change live settings. Leave experimental detectors disabled on the live public server until staging tests pass.
- Keep `punishments.actions.<check>.enabled: false` in `punishments.yml` for all checks initially. Alert first, review evidence, then enable one action only after measuring false positives.
- Keep `punishments.enabled: true` in `punishments.yml` if you want the global master switch available; per-check action flags remain the second gate.
- Keep `punishments.require-independent-families: true` on a public server. By default, an automatic kick requires repeated flags from at least two distinct detector families inside `correlation-window-ms`, with at least two signals per family. If you enable only one family, the plugin will warn that kicks cannot trigger until you lower the family threshold or enable a second family.
- Keep creative and spectator exemptions, and add any server-specific worlds/permissions to ignored lists only after reviewing the effect.
- The low-TPS guard is scoped by `settings.performance.pause-checks-on-low-tps` in `config.yml`. The default list pauses tick-sensitive movement/combat/mining heuristics while leaving packet-rate, timer, and click-rate telemetry available. Use `['*']` only if you intentionally want all checks that call the shared check utility paused during server lag. An empty list disables pausing without turning off the guard setting.
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
| `reach` | Approximate entity hitbox distance | Disabled |
| `block-reach` | Eye-to-block collision-box distance for breaking and right-click interactions | Disabled |
| `mace-damage` | Piecewise vanilla Java smash curve, Density allowance, extra tolerance, optional legacy formula | Disabled |
| `wall-hit` | Multi-point hitbox occlusion signal; not latency-compensated | Disabled |
| `fast-break` | Paper break-progress estimate versus observed mining time | Disabled |
| `nuker-burst` | Repeated rolling-window block-break burst | Disabled |
| `click-rate` | Arm-swing/CPS telemetry, optional PacketEvents attack-rate limit, and optional statistical attack-interval regularity signal | Disabled |
| `container-steal` | Repeated rapid shift-transfers from an open top/container inventory | Disabled |
| `container-move` | Repeated movement while a server-side container GUI is open (not the client's own inventory) | Disabled |
| `combat-pattern` | Rotation/hit-pattern telemetry | Disabled |
| `scaffold-signal` | Repeated placement-angle pattern | Disabled |
| `no-fall` | Fall/landing damage correlation | Disabled |
| `packet-rate` | PacketEvents rate telemetry | Disabled |
| `timer` | Sustained movement packet-rate telemetry | Disabled |
| `force-op` | Alias-aware OP/deop blocking, configurable permission-mutation patterns, operator-list auditing | Enabled |

These are signals, not guaranteed identification of Meteor, Wurst, Baritone, or any other client. Several are experimental heuristics. See `CHEAT_COVERAGE.md` for the current/planned coverage split.

## Block interaction reach signal

`checks.block-reach.enabled` enables a conservative check on server-observed block-breaking attempts and right-click block interactions. It measures the shortest eye-to-block-collision-box distance, then compares it with separate configurable `max-break-distance` and `max-interaction-distance` limits plus `distance-tolerance`. Tune server releases with `version-overrides` and connected client protocols with `client-version-overrides` (requires compatible PacketEvents). Materials, worlds, permissions and game modes can be excluded. Because this signal observes Bukkit events rather than replaying historical packets, it is not a complete extended-reach detector and remains disabled by default until staging validation is complete.

## Optional autoclicker interval-regularity signal

The `checks.click-rate.pattern-detection-enabled` option enables an additional PacketEvents-based signal that analyzes recent attack timestamps. It requires a minimum sample count and attack rate, then checks both interval standard deviation and coefficient of variation across repeated evaluations. It is disabled by default and only adds evidence to the existing `click-rate` violation level; it is not proof of a cheat.

Tune these settings under `checks.click-rate` in `checks.yml`:

- `pattern-window-ms`: rolling interval-history window.
- `minimum-pattern-attacks` and `minimum-pattern-cps`: how much data is required and the lowest attack rate considered.
- `max-pattern-interval-stddev-ms` and `max-pattern-coefficient-variation`: interval regularity bounds; stricter limits reduce detections and still do not guarantee accuracy.
- `pattern-evaluation-interval-ms`, `minimum-pattern-windows`, and `pattern-flag-cooldown-ms`: repeat-evidence and alert rate controls.

All thresholds can also use `client-version-overrides`, with underscore-separated version keys, because attack timing can vary with client/protocol behavior. PacketEvents must be installed and compatible with the server protocol. Keep this signal disabled until it has been checked against legitimate players and controlled test cases. Do not enable an automatic kick based on this signal alone.

## Container-looting burst signal

`checks.container-steal.enabled` enables a rate heuristic for repeated `MOVE_TO_OTHER_INVENTORY` events originating from the open top inventory (for example, shift-clicking items out of a chest). It does not detect every ChestStealer implementation and it cannot tell whether a player is using a particular client brand.

The main controls in `checks.yml` are:

- `sample-window-ms` and `max-transfers-per-window`: how many container-to-player shift-transfers fit into the rolling window.
- `evaluation-interval-ms` and `required-over-limit-windows`: how long the over-limit burst must persist.
- `flag-cooldown-ms` and `max-buffered-samples-per-player`: repeated-signal rate and per-player memory cap.
- `ignored-inventory-types`, `ignored-worlds`, `ignored-permissions`, and `exempt-game-modes`: server-specific exclusions.

The detector and its punishment action are disabled by default. Test double chests, ender chests, shulker boxes, custom GUI plugins, ordinary looting, and different ping levels before enabling it. A burst is a reviewable staff signal, not proof of automation.

## Container GUI movement signal

`checks.container-move.enabled` opts in to observing repeated horizontal movement while a server-side container GUI is open. It does not observe movement with the player's client-only inventory screen open, and it cannot reliably infer a particular cheat-client brand.

The `checks-world.yml` section exposes the rolling sample window, minimum per-event movement, maximum accepted horizontal/vertical step, sample buffer cap, required movement samples, total path distance, velocity grace, signal cooldown, ignored inventory types/title substrings, and server/client-version overrides. Exclude custom GUI titles or types used by plugins that permit moving while inventories are open. A server velocity event clears evidence and provides a configurable grace period. Keep it disabled until staging tests cover ordinary containers, custom GUI plugins, knockback, latency, teleports, and legitimate menu interactions.

## Configuration validation

At startup and after `/annaac reload`, ANNA AC prints warnings for invalid numeric thresholds in the main check settings and in version-specific override maps. Examples include negative distances/cooldowns, non-finite values, out-of-range ratios, and degree values outside their supported range. Fix warnings in the relevant section of `checks.yml`; invalid values read through the version-aware resolver fall back to the code-defined threshold instead of being applied. Validation can only check known numeric conventions—run the staging matrix before enabling enforcement.

## Per-version movement threshold tuning

The wall-hit detector's ray geometry is configurable in `checks-combat.yml` using `checks.wall-hit.sample-points`. Each entry defines normalized `x`, `y`, and `z` coordinates from 0.0 to 1.0 across the target hitbox. `max-sample-points` limits ray-tracing work to 1–32 points per attack; malformed entries are ignored, and a fully invalid list falls back to the built-in nine-point layout. Startup diagnostics warn when fewer than three valid samples remain.

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

The basic movement and movement-physics checks also support `velocity-grace-ms`, which briefly ignores displacement flags and rebases the movement sample after server-applied velocity changes such as knockback or launch pads. Its valid range is 0–5000 ms; 0 disables the grace period. Tune it on staging because a longer grace period also creates a short detection blind spot.

Server-version and client-protocol overrides are wired for `movement`, `movement-physics`, `flight`, `jesus`, `spider-signal`, `knockback`, `reach`, `wall-hit`, `fast-break`, `nuker-burst`, `click-rate`, `container-steal`, `combat-pattern`, `scaffold-signal`, `no-fall`, `packet-rate`, `timer`, and `mace-damage`. Numeric detection thresholds, violation weights, alert thresholds/cooldowns, decay rates and the check-level kick fallback are version-aware where a check reads them through the resolver. Punishment action thresholds and cooldowns in `punishments.yml` remain independent server policy settings. The example numbers are illustrative, not recommended anti-cheat defaults. Keep experimental checks disabled until staging tests confirm they do not flag legitimate movement. Movement physics also exposes the Speed-potion allowance per level and Adventure-mode multiplier. Flight, Jesus and Spider checks also expose vertical reset distances and horizontal sample floors. Spider wall-sampling heights, wall offset and climbable-material exemptions, plus the movement-physics special-surface allowlist, are configurable directly in `checks.yml`. Packet-rate and click-rate telemetry buffers have per-player capacity settings that are clamped to 128–4096 samples to protect server memory during burst traffic.

## Client-protocol-aware tuning

When PacketEvents is installed, ANNA AC records the protocol release reported by each connected client. Resolution order is exact client release, client major/minor, exact server release, server major/minor, then the base setting or code fallback. If the client's version is unknown, ANNA AC safely falls back to the server version. Version-specific violation weights and alert/decay thresholds affect the signal scoring path, while action thresholds in `punishments.yml` remain independent of the client version.

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

On startup and `/annaac reload`, ANNA AC reports override keys that use dots instead of underscores, unknown threshold names, and values that are not finite numbers. Review these warnings; a typo may otherwise leave the normal threshold in effect. Values outside known safe numeric bounds (negative durations/counts, ratios outside 0–1, or angles outside 0–180 degrees) are ignored by the resolver and fall back to the next valid setting.

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

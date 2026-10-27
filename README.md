# ANNA AC

Original anti-cheat project targeting **Paper 1.21.11 / Java 21**.

## Implemented detector signals

- **Movement displacement:** legacy conservative displacement heuristic; disabled by default.
- **Movement physics envelope:** repeated movement-envelope outlier heuristic with sprint/speed and ping slack. This is not a deterministic per-tick physics simulator; disabled by default.
- **Flight / hover:** detects sustained near-horizontal movement while airborne and excludes common legitimate states such as flight permissions, elytra, vehicles, swimming, climbing, slow falling, levitation and several special blocks. Experimental and disabled by default; it is not a full movement simulator.
- **Jesus / liquid walk:** flags sustained horizontal movement at a stable Y over deep liquid while not swimming. Server-side state is imperfect; disabled by default.
- **Spider climb:** flags repeated upward movement beside a solid wall, excluding common climbable blocks, flight modes, jump effects and liquids. Parkour/plugin mechanics require staging validation; disabled by default.
- **Knockback telemetry:** compares movement observed after server velocity. Collisions, player input and ping affect results; disabled by default.
- **Reach:** eye-to-nearest-point-on-target-hitbox signal without full historical hitbox/interpolation compensation; disabled by default.
- **Mace damage:** configurable vanilla Java smash-damage curve, Density enchantment allowance, extra tolerance, and a legacy custom-server formula fallback. It remains an anomaly signal—not a full combat simulator—and is disabled by default.
- **Wall-hit:** configurable multi-point target-hitbox occlusion signal; not latency-compensated and disabled by default.
- **Nuker burst:** counts completed block-break events in a configurable rolling window, with ignored materials and repeated-window threshold; this does not simulate individual block mining speed and is disabled by default.
- **FastBreak:** compares Paper’s current per-player block break-progress estimate with observed start-to-break time, allows configurable ping slack, ignores short expected sessions, and requires repeated evidence. This is not packet-level replay and remains disabled by default.
- **Combat pattern:** looks for repeated near-identical rotation deltas around hits. This is low-confidence telemetry, not proof of KillAura; disabled by default.
- **Click / attack rate:** arm-swing telemetry plus an optional PacketEvents `INTERACT_ENTITY` attack-packet rolling window with configurable thresholds and repeated over-limit samples. Attack rate alone is not proof of automation; disabled by default.
- **Scaffold signal:** placement-angle heuristic; weak evidence only and disabled by default.
- **No-fall signal:** checks a narrow pattern of landing after a significant fall without observed fall damage. Protection plugins and gameplay effects can create the same pattern; disabled by default.
- **Packet rate:** optional PacketEvents rolling packet-rate and short-interval telemetry; disabled by default.
- **Timer telemetry:** a separate sustained movement-packet-rate signal with a configurable sample duration and ping/proxy caveat. It is not a deterministic client-tick simulator; disabled by default.
- **Force OP guard:** blocks configured OP/deop and sensitive permission-mutating command patterns—including namespaced aliases and execute-wrapped commands—from players lacking `annaac.admin`; it audits console usage and tracks OP grants/removals against a saved baseline. Read-only permission-plugin commands need not be blocked just because they share a root. It cannot stop malicious plugins, direct file edits, console compromise, or every unconfigured alias.

Shared features: startup/reload configuration diagnostics, `/annaac status` system summary, per-check violation levels/decay across reconnects with bounded retention, alert throttling, console/staff/Discord alerts, JSONL evidence with retention caps, per-staff alert toggles, admin commands, pure detection-math and plugin-risk tests, YAML syntax/config regression tests, and GitHub Actions build/tests. The separate security subsystem adds anti-nuke bulk-command protection, configurable explosion-block caps, optional mining-rate limits, SHA-256 plugin JAR inventory, plugin lifecycle audits, suspicious-filename review hints, and a bounded static plugin-risk scan. Use `/annaac securityhistory` to review recent system-level security evidence.

## Anti-Xray and cheat coverage

- [Paper Anti-Xray setup for ANNA MC](docs/ANTI_XRAY.md): recommended Paper world configuration, restart and verification guidance.
- [Cheat coverage roadmap](docs/CHEAT_COVERAGE.md): current signals and planned coverage for behaviors associated with Meteor, Wurst, Baritone, and other clients.
- [Hardening and rollout plan](docs/HARDENING_ROLLOUT.md): X-ray prevention, Force OP/anti-nuke priorities, Discord/config ownership, and a staging acceptance checklist.

Anti-Xray is provided by Paper's block obfuscation, not by a client-brand detector inside ANNA AC. No server-side plugin can reliably identify every client or every feature; experimental checks must be validated before punishments are enabled.

## Configuration, languages, and Discord

- [Full configuration guide](docs/CONFIGURATION.md): modular settings, English/Hindi language selection, permissions, detector defaults, and safe rollout.
- [Anti-nuke and plugin security guide](docs/SECURITY_PROTECTION.md): destructive-command restrictions, explosion caps, plugin hash baselines, static malware-risk heuristics, admin scan commands, and limitations.
- [Discord webhook setup](docs/DISCORD_SETUP.md): secure outbound staff alerts, embeds, optional role mentions, and troubleshooting.
- The webhook URL is intentionally not committed; the server owner must create a private Discord channel webhook and enter it in the server's local config.

## Configuration

Configuration is split into dedicated files: `config.yml` (global settings), `checks.yml` (per-detector options), `discord.yml` (Discord/staff alert delivery), `database.yml` (evidence storage and retention), `punishments.yml` (actions and correlation controls), `security.yml` (anti-nuke and plugin integrity scans), and `messages_en.yml` / `messages_hi.yml` (languages). Every YAML option has an explanatory comment directly above it. In `checks.yml`, each detector has independent enablement, alerts, cooldown, violation addition, decay, alert threshold, kick threshold, ignored worlds, ignored permissions, and exempt game modes. The wall-hit signal also exposes its sampling distance, occlusion ratio, and geometry margin. The Nuker burst signal exposes its rolling-window length, block threshold, evaluation interval, repeated-window requirement, and ignored material list. Each check's thresholds and observation windows are in its own section. Punishments are independently controlled under `punishments.actions.<check>.enabled`, with a per-check `type` (`KICK`, `WARN`, `COMMAND`, `NONE`), threshold, cooldown, message, and optional console command list. All action toggles default to off; command execution requires deliberate owner configuration.

All experimental detection checks and all automatic kick actions are disabled by default. Anti-nuke bulk-command protection and the explosion-block cap are enabled; the optional block-break rate limiter is disabled. Leave them that way until they pass staging validation. The legacy movement heuristic is also disabled by default.

Force OP controls include `checks.force-op.cancel-player-op-commands`, `kick-player-on-attempt`, `detect-op-grants`, `audit-console-op-commands`, `persist-operator-baseline`, and a configurable `protected-command-prefixes` list. Review this list for the permission plugins and aliases actually installed on your server.

Discord: set `alerts.discord-enabled: true` and `alerts.discord-webhook` in `discord.yml` to a private HTTPS webhook. Keep it secret.

Evidence JSONL files are stored under the directory configured by `database.evidence-directory` in `database.yml` (default: `plugins/ANNA-AC/evidence/`). Retention days, maximum records per player, maximum evidence detail length, and in-memory violation retention are configurable.

## Build

JDK 21 and Gradle 8.14+:

    gradle clean test build

The output should be `build/libs/ANNA-AC-0.1.0-SNAPSHOT.jar`. Verify a successful GitHub Actions run for the exact latest commit before installing.

## Anti-nuke and plugin integrity

Read [docs/SECURITY_PROTECTION.md](docs/SECURITY_PROTECTION.md) before enabling these controls on a live server. Plugin hashing detects JAR changes; the static scan is heuristic and can miss obfuscated/runtime-only malware or flag legitimate plugins. It never automatically deletes or disables a plugin. The initial JAR hash inventory must be reviewed rather than blindly trusted.

## Staging validation required

See [docs/STAGING_VALIDATION.md](docs/STAGING_VALIDATION.md) for a test matrix covering legitimate movement, PvP, block placement, fall edge cases, latency, proxy/client combinations, and Force OP behavior. This workflow has not run a live Paper server, so it cannot claim live false-positive testing has passed.

## Limitations

ANNA AC is **not yet equivalent to Grim or Vulcan**. Accurate movement prediction requires simulation of Minecraft's version-specific physics and client inputs; robust knockback analysis needs latency-aware velocity tracking and replay; KillAura/aim needs stronger multi-signal analysis; timer detection needs calibrated packet/tick analysis; and Force OP prevention cannot defend against arbitrary malicious plugins or host-level compromise. These current detectors are an extensible foundation, not a promise of production-grade detection accuracy.

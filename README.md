# ANNA AC

Original anti-cheat project targeting **Paper 1.21.11 / Java 21**.

## Implemented detector signals

- **Movement displacement:** legacy conservative displacement heuristic; disabled by default.
- **Movement physics envelope:** repeated movement-envelope outlier heuristic with sprint/speed and ping slack. This is not a deterministic per-tick physics simulator; disabled by default.
- **Knockback telemetry:** compares movement observed after server velocity. Collisions, player input and ping affect results; disabled by default.
- **Reach:** eye-to-nearest-point-on-target-hitbox signal without full historical hitbox/interpolation compensation; disabled by default.
- **Wall-hit:** configurable multi-point target-hitbox occlusion signal; not latency-compensated and disabled by default.
- **Nuker burst:** counts completed block-break events in a configurable rolling window, with ignored materials and repeated-window threshold; this does not simulate individual block mining speed and is disabled by default.
- **FastBreak:** compares Paper’s current per-player block break-progress estimate with observed start-to-break time, allows configurable ping slack, ignores short expected sessions, and requires repeated evidence. This is not packet-level replay and remains disabled by default.
- **Combat pattern:** looks for repeated near-identical rotation deltas around hits. This is low-confidence telemetry, not proof of KillAura; disabled by default.
- **Click rate:** arm-swing count over a rolling one-second window. CPS alone is not proof of automation; disabled by default.
- **Scaffold signal:** placement-angle heuristic; weak evidence only and disabled by default.
- **No-fall signal:** checks a narrow pattern of landing after a significant fall without observed fall damage. Protection plugins and gameplay effects can create the same pattern; disabled by default.
- **Packet rate:** optional PacketEvents rolling packet-rate and short-interval telemetry; disabled by default.
- **Timer telemetry:** a separate sustained movement-packet-rate signal with a configurable sample duration and ping/proxy caveat. It is not a deterministic client-tick simulator; disabled by default.
- **Force OP guard:** blocks configured privilege-management commands from players lacking annaac.admin, audits console commands, and monitors operator-list changes and can compare a saved operator baseline across restarts. It cannot prevent malicious plugins, direct server-file edits, console compromise, or every permission-plugin alias.

Shared features: per-check violation levels/decay, alert throttling, console/staff/Discord alerts, JSONL evidence with retention caps, per-staff alert toggles, admin commands, pure detection-math unit tests, config regression tests, and GitHub Actions build/tests.

## Anti-Xray and cheat coverage

- [Paper Anti-Xray setup for ANNA MC](docs/ANTI_XRAY.md): recommended Paper world configuration, restart and verification guidance.
- [Cheat coverage roadmap](docs/CHEAT_COVERAGE.md): current signals and planned coverage for behaviors associated with Meteor, Wurst, Baritone, and other clients.

Anti-Xray is provided by Paper's block obfuscation, not by a client-brand detector inside ANNA AC. No server-side plugin can reliably identify every client or every feature; experimental checks must be validated before punishments are enabled.

## Configuration, languages, and Discord

- [Full configuration guide](docs/CONFIGURATION.md): prefix, English/Hindi language selection, permissions, detector defaults, and safe rollout.
- [Discord webhook setup](docs/DISCORD_SETUP.md): secure outbound staff alerts, embeds, optional role mentions, and troubleshooting.
- The webhook URL is intentionally not committed; the server owner must create a private Discord channel webhook and enter it in the server's local config.

## Configuration

In `plugins/ANNA-AC/config.yml`, each implemented detector has independent enablement, alerts, cooldown, violation addition, decay, alert threshold, kick threshold, ignored worlds, ignored permissions, and exempt game modes. The wall-hit signal also exposes its sampling distance, occlusion ratio, and geometry margin. The Nuker burst signal exposes its rolling-window length, block threshold, evaluation interval, repeated-window requirement, and ignored material list. Each check's thresholds and observation windows are in its own section. Punishments are independently controlled under `punishments.actions.<check>.enabled`.

All experimental detection checks and all automatic kick actions are disabled by default. Leave them that way until they pass staging validation. The legacy movement heuristic is also disabled by default.

Force OP controls include `checks.force-op.cancel-player-op-commands`, `kick-player-on-attempt`, `detect-op-grants`, `audit-console-op-commands`, `persist-operator-baseline`, and a configurable `protected-command-prefixes` list. Review this list for the permission plugins and aliases actually installed on your server.

Discord: set `alerts.discord-enabled: true` and `alerts.discord-webhook` to a private HTTPS webhook. Keep it secret.

Evidence JSONL files are stored under `plugins/ANNA-AC/evidence/`. Retention days and maximum records per player are configurable.

## Build

JDK 21 and Gradle 8.14+:

    gradle clean test build

The output should be `build/libs/ANNA-AC-0.1.0-SNAPSHOT.jar`. Verify a successful GitHub Actions run for the exact latest commit before installing.

## Staging validation required

See [docs/STAGING_VALIDATION.md](docs/STAGING_VALIDATION.md) for a test matrix covering legitimate movement, PvP, block placement, fall edge cases, latency, proxy/client combinations, and Force OP behavior. This workflow has not run a live Paper server, so it cannot claim live false-positive testing has passed.

## Limitations

ANNA AC is **not yet equivalent to Grim or Vulcan**. Accurate movement prediction requires simulation of Minecraft's version-specific physics and client inputs; robust knockback analysis needs latency-aware velocity tracking and replay; KillAura/aim needs stronger multi-signal analysis; timer detection needs calibrated packet/tick analysis; and Force OP prevention cannot defend against arbitrary malicious plugins or host-level compromise. These current detectors are an extensible foundation, not a promise of production-grade detection accuracy.

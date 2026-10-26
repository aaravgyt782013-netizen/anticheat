# ANNA AC

Original modular anti-cheat for Anna MC. Target platform: **Paper 1.21.11 / Java 21**. PacketEvents is an optional integration for packet timing telemetry.

## Current checks and status

- **Movement displacement** — conservative horizontal-distance heuristic; configurable threshold, VL addition/decay, alert cooldown and kick threshold. This is not a full physics simulator.
- **Reach signal** — measures the attacker's eye position to the nearest point on the target hitbox. Disabled by default; it does not compensate for ping, interpolation or all combat mechanics.
- **Click-rate telemetry** — counts arm-swing events over a rolling one-second window. Disabled by default; high CPS alone does not prove automation.
- **Scaffold placement signal** — weak pitch/placement-angle heuristic. Disabled by default because legitimate bridging patterns vary.
- **Packet-rate telemetry** — optional PacketEvents movement packet interval signal, disabled by default. Packet timing alone is not proof of cheating.
- **Force OP audit** — records visible OP/deop command attempts and newly observed operator-status changes. It cannot prevent or detect every exploit path, and the actor for a newly observed OP may be unknown.

Shared infrastructure includes per-check violation levels and decay, alert throttling, console/staff alerts, optional Discord webhook alerts, JSONL evidence, basic admin commands and a GitHub Actions build/test workflow.

## Configuration

Edit `plugins/ANNA-AC/config.yml`. Every implemented signal has its own `enabled`, `alert`, `alert-cooldown-ms`, `violation-add`, `violation-decay-per-second`, `alert-vl` and `kick-vl` settings where applicable. Check-specific thresholds are documented in the shipped configuration. Kick actions are separately controlled under `punishments.actions.<check>.enabled`; experimental checks default to disabled and cannot kick until explicitly enabled there.

Discord setup: set `alerts.discord-enabled: true` and `alerts.discord-webhook` to a private HTTPS webhook URL. Never publish the webhook or commit it to Git.

## Build

Requires JDK 21 and Gradle 8.14+.

    gradle clean test build

The JAR should be produced under `build/libs/ANNA-AC-0.1.0-SNAPSHOT.jar`. Treat it as unverified until the GitHub Actions workflow completes successfully.

## Install safely

1. Back up the server and test on a staging server first.
2. Use Paper 1.21.11 and install a compatible PacketEvents Spigot plugin only if packet telemetry is wanted.
3. Put the built JAR in the server's `plugins` directory and start the server.
4. Review `plugins/ANNA-AC/config.yml`; do not enable experimental checks or kick actions on a live server before testing.
5. Grant `annaac.alerts` only to trusted moderators; reserve `annaac.admin` for senior staff.

## Important limitations

ANNA AC is **not yet equivalent to Grim or Vulcan**. Full movement prediction, robust reach compensation, KillAura/aim analysis, inventory and transaction checks, timer simulation, no-fall validation, robust exploit-resistant Force OP prevention, replay capture, a web dashboard and cross-version simulation are not implemented. Evidence retention/pruning configuration is not yet automatically enforced. Configuration options do not imply a detection exists. Validate false-positive rates with normal players, different pings, knockback, effects, ice, slime, vehicles and server-specific mechanics before considering enforcement.

# ANNA AC

Original anti-cheat project for **Paper 1.21.11 / Java 21**.

## Implemented signals (not all production-grade)

- **Movement displacement:** a conservative horizontal displacement heuristic. It is not a movement physics simulator and may flag unusual legitimate movement; tune it on a staging server.
- **Reach:** eye-to-nearest-point-on-target-hitbox signal. No full ping/interpolation compensation; disabled by default.
- **Click rate:** arm-swing events in a rolling one-second window. CPS alone is not proof of automation; disabled by default.
- **Scaffold signal:** placement angle heuristic; weak evidence only and disabled by default.
- **Packet rate:** optional PacketEvents movement packet interval telemetry; disabled by default.
- **Force OP audit:** logs OP/deop commands and newly observed operator status. It does not prevent every possible permission exploit.

Shared features: per-check violation levels/decay, alert throttling, console/staff/Discord alerts, JSONL evidence, per-staff alert toggles, basic admin commands, and GitHub Actions build/tests.

## Configurability

In `plugins/ANNA-AC/config.yml`, each check supports independent enablement, alerts, alert cooldown, violation addition, decay, alert threshold, kick threshold, ignored worlds, ignored permissions, and exempt game modes. Global ignored worlds are under `settings.ignored-worlds`. Kick actions are independently controlled under `punishments.actions.<check>.enabled`. Check-specific detector thresholds are in the same section.

Discord: set `alerts.discord-enabled: true` and `alerts.discord-webhook` to a private HTTPS webhook. Keep it secret.

## Build

JDK 21 and Gradle 8.14+:

    gradle clean test build

The output should be `build/libs/ANNA-AC-0.1.0-SNAPSHOT.jar`. Verify a successful GitHub Actions run for the exact latest commit before installing.

## Safe install

1. Back up your server and test on a staging Paper 1.21.11 server.
2. Install a compatible PacketEvents Spigot plugin only if packet telemetry is desired.
3. Put the built JAR in `plugins/`, start once, and review `plugins/ANNA-AC/config.yml`.
4. Keep experimental checks disabled until you have validated them with normal players and different ping levels.
5. Grant `annaac.admin` only to trusted administrators and `annaac.alerts` to trusted staff.

## Not yet complete

This is a growing foundation, **not a Grim/Vulcan-equivalent anti-cheat yet**. Full per-tick physics prediction, robust knockback and latency compensation, KillAura/aim analysis, inventory/transaction checks, timer simulation, no-fall validation, complete packet exploit checks, robust Force OP prevention, replay capture, web dashboard, and cross-version simulation are not implemented. Evidence retention settings are not yet enforced automatically. Do not infer a detection exists from a config section alone.

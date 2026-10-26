# ANNA AC

Original modular anti-cheat foundation for Anna MC. Target: Paper 1.21.11 / Java 21. PacketEvents is an optional integration for packet timing telemetry.

## Current implementation status

Initial implementation targets:
- Paper plugin lifecycle and configurable YAML files.
- Conservative horizontal movement-displacement heuristic with violation level, decay, alerts and kick threshold.
- JSON Lines evidence logging.
- Console/staff alerts and optional Discord webhook notifications.
- Basic admin commands.
- Optional PacketEvents movement-packet interval telemetry, disabled by default.

Not implemented yet: full movement physics simulation, reach, KillAura, aim, autoclicker, scaffold, inventory, timer, no-fall, robust Force OP exploit detection, replay capture, web dashboard, cross-version simulation. Configuration placeholders are not detections.

## Build

Requires JDK 21 and Gradle 8.14+.

    gradle clean test build

The plugin JAR is created under build/libs/ANNA-AC-0.1.0-SNAPSHOT.jar.

## Install

1. Back up the server and test on a staging server first.
2. Install Paper 1.21.11 and a compatible PacketEvents Spigot plugin for packet telemetry.
3. Put the ANNA-AC JAR into the plugins folder and start the server.
4. Review plugins/ANNA-AC/config.yml. Packet-rate detection is disabled by default.
5. Enable Discord alerts and configure a private webhook URL. Never publish the URL or commit it to Git.
6. Grant annaac.alerts to trusted moderators and annaac.admin only to senior staff.

## Safety and limitations

The first movement check is a simple horizontal-distance heuristic, not a Grim-style movement simulator. Tune it on a staging server for velocity, knockback, effects, vehicles, slime, ice and latency before enabling kicks on a live server. Packet timing is noisy and disabled by default. Evidence retention is configured but automatic pruning is not yet implemented. This is a starter release, not equivalent to Vulcan or Grim yet.

GitHub Actions builds and runs automated tests on pushes and pull requests. Wait for a green CI run before treating a release as verified.

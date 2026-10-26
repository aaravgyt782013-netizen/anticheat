# ANNA AC staging validation plan (Paper 1.21.11)

This plan is required before enabling punishments. Automated unit tests cannot prove that a detector is accurate on a live server. Do not test cheats against public players or enable automatic kicks during this validation.

## Test setup

1. Make a backup and create a private staging copy of the Paper 1.21.11 server with the same plugins, proxy path, world settings and Java 21 runtime.
2. Install ANNA AC and a compatible PacketEvents build only if packet telemetry is being tested.
3. Leave all punishments.actions.*.enabled values false. Enable one detector at a time and collect staff alerts/evidence only.
4. Test with at least two normal clients and repeat with low, medium and high latency.
5. Keep a test log: date, client version, ping, TPS, movement state, detector, alert, and whether the action was legitimate.

## Legitimate-play false-positive matrix

Run each case for at least 10 minutes and record alerts per detector.

- Walk, sprint, jump, sprint-jump, stairs, slabs, fences, ladders, swimming, elytra, riptide, vehicles and water transitions.
- Speed potion levels, jump effects, ice, slime, honey, soul sand, cobwebs, knockback from multiple sources and landing on uneven terrain.
- Teleports, respawn, world changes, server lag spikes, proxy reconnects and ping changes.
- Normal PvP with different attack styles, turning while attacking, shield use, knockback resistance, crowded fights and entities near walls.
- Normal block placement while bridging, stair building, scaffolding by hand, crouch-bridging and placing blocks under lag.
- Falls into water, hay, slime, cobwebs, ladders, vines, elytra transitions, protected regions, and worlds where another plugin cancels fall damage.
- Packet telemetry with each supported client/proxy combination, including reconnects and lag spikes.

## Detector acceptance criteria

- **Movement physics / knockback:** zero unexplained alerts in the legitimate matrix before punishments are considered. Compare evidence with server velocity, ping, collisions and potion/effect state. These checks are heuristic, not a full server-tick replay simulator.
- **Combat pattern / reach / click rate:** use alerts only. Require repeated independent signals and manual review; identical rotation deltas and CPS are not proof of KillAura.
- **Scaffold / no-fall:** test every listed legitimate placement/fall case. These are low-confidence signals and should remain disabled if protection plugins create ambiguous outcomes.
- **Packet rate / timer:** test all proxy and client versions. Packet timing is not equivalent to tick-rate proof; do not punish on this signal alone.
- **Force OP guard:** in staging, verify unauthorized player OP/deop and configured permission-management commands are cancelled; authorized admin commands are audited; console operations are logged; and OP grants made by another plugin are detected by the operator-list audit.

## Anti-nuke and plugin integrity validation

Run these separately from gameplay detections on a disposable staging copy.

- With a player lacking \`annaac.security.bypass\`, verify configured \`/fill\`, \`/setblock\`, \`/clone\`, WorldEdit batch, and plugin-management/reload commands are cancelled and reported. Verify console commands are audited but not blocked.
- Grant the bypass permission to one test account, verify the bulk command is allowed by the guard but still requires the permission for the underlying command, and confirm an audit alert is emitted.
- Test a block-damaging explosion over and under \`max-explosion-affected-blocks\`; ensure large explosions are cancelled and normal TNT/redstone systems still behave as intended.
- Leave the optional block-break rate limiter off until mining with efficiency tools, haste, custom tools, and server plugins is tested.
- Run \`/annaac securityscan\` and confirm the first-run baseline is saved at \`plugins/ANNA-AC/plugin-hashes.yml\`. Manually review JAR origins and static-risk alerts; do not assume the generated first inventory is trusted.
- On a disposable copy, add, replace, and remove a harmless test JAR, then confirm hash alerts appear. Enable and disable a harmless plugin after the lifecycle grace period to verify the audit alert.
- Test metadata/resource scanning with a harmless fixture that contains a risky-looking string. Confirm it is described as a heuristic/manual review, not an automatic malware verdict.
- Only test \`/annaac securityscan approve\` after independently reviewing every JAR. Verify it intentionally replaces the hash inventory, and verify a player without \`annaac.security.baseline-approve\` cannot approve it.

## Release gate

Do not enable a kick action until the detector has passed the full matrix, no unexplained legitimate alerts remain, and at least two staff members have reviewed evidence. Re-run the matrix after changes to Paper, proxy, anti-cheat, movement-related plugins, or server configuration.

## What this cannot validate automatically

This repository does not include a full Minecraft client simulator, deterministic per-tick replay, or an automated live-server lab. No live-server false-positive run has been performed by this code-editing workflow. Treat this document as the required manual validation plan, not a claim that the detector is already proven accurate.

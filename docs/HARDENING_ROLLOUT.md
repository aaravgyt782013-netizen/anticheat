# ANNA AC hardening and rollout plan

Target: Paper 1.21.11 / Java 21. This guide separates prevention, detection, and response so the server does not confuse an alert with proof.

## Reality check: no instant detector sees every client hack

A server sees packets and game-state changes, not the complete client process. Some client features are visual-only or do not cause an illegal server action. A cheat may evade a heuristic; plugins can alter vanilla mechanics. Treat "instant detection" as the design goal for high-confidence illegal actions, not a guarantee that every hack or X-ray client is identified.

## Priority 1 — prevent X-ray information leaks

Use Paper's built-in Anti-Xray because preventing real ore positions from being sent is stronger than trying to infer that a client has X-ray after the fact.

1. Back up the server and edit the existing `anticheat.anti-xray` section in `config/paper-world-defaults.yml`.
2. Enable it and test `engine-mode: 2` on a staging copy; merge the current version's documented hidden-block and replacement-block lists rather than replacing the whole Paper config.
3. Configure per-world overrides for the Nether (including ancient debris and nether ores) when required.
4. Restart fully, then test normal mining, exposed ores, caves, and each dimension.
5. Read [ANTI_XRAY.md](ANTI_XRAY.md) and the official [Paper Anti-Xray documentation](https://docs.papermc.io/paper/anti-xray/).

ANNA AC does not itself obfuscate chunk data and cannot prove which client has an X-ray module. Keep world seeds, backups, panel access, and server files private.

## Priority 2 — prevent privilege escalation and destructive actions

Review `security.yml`:
- `security.force-op`: block configured player OP/deop and permission mutation commands; audit operator-list changes.
- `security.antinuke`: block configured destructive bulk commands for untrusted players, optionally limit block-break bursts, and cap explosion-affected blocks.
- `security.plugin-monitor`: compare plugin JAR SHA-256 hashes to an owner-reviewed baseline, audit lifecycle changes, and report suspicious static indicators.

The guard cannot stop a compromised hosting panel, direct filesystem edits, console access, or a malicious plugin already running with server-level privileges. Restrict panel/console access, use unique credentials and MFA where available, and install plugins only from trusted publishers.

## Priority 3 — stage anti-cheat checks

Each detector is independently configured in `checks.yml`. Start with alerts/evidence only; test legitimate gameplay and authorized test clients on a staging copy before enabling kick thresholds.

| Area | Current signal | Important limitation |
|---|---|---|
| Movement | displacement, physics envelope, flight/hover, liquid walking, spider | not a complete version-specific physics simulator |
| Combat | reach, wall-hit, knockback, combat-pattern, click/attack rate | latency, hitbox history, server mechanics and legitimate aim can affect results |
| Mace | configurable vanilla smash curve and Density allowance | damage plugins, attributes, effects and event timing can affect the observed damage |
| Mining/building | FastBreak estimate, Nuker burst, scaffold signal, NoFall | event-level heuristics; not complete packet replay |
| Packet/timer | optional PacketEvents rate telemetry | requires compatible PacketEvents and calibrated thresholds |
| Client identity | no reliable universal brand fingerprint | clients can change brands; visual-only modules may not be detectable |

## Discord, evidence and config ownership

- Put the webhook only in the server's local `discord.yml`; never commit it to GitHub or share it publicly.
- Configure staff alert routing and role mentions deliberately; avoid broad `@everyone` pings.
- Keep bounded evidence retention and check `database.yml` / `punishments.yml` before changing response behavior.
- Review `messages_en.yml` and `messages_hi.yml` for player-facing messages.
- Run `/annaac status`, `/annaac securityscan`, and `/annaac securityhistory` as documented; inspect scan findings manually before approving a plugin hash baseline.

## Staging acceptance checklist

- [ ] Back up worlds, plugin directory, and configs; test on a separate server first.
- [ ] Build and unit tests pass for the exact commit being deployed.
- [ ] Paper Anti-Xray tested in Overworld and Nether with expected world overrides.
- [ ] Legitimate PvP tested with varied ping, armor, effects, mace enchants, knockback and custom mechanics.
- [ ] Creative/spectator, elytra, water, ladders, slime/honey, vehicles, parkour, and server-specific movement plugins tested.
- [ ] Permission guards tested for authorized admins, untrusted players, command aliases, console, and each installed permission plugin.
- [ ] Anti-nuke limits tested against normal mining, explosions, WorldEdit, and backup/restore procedures.
- [ ] Discord alerts arrive without leaking webhook URLs or flooding staff.
- [ ] Auto-kick is enabled only for checks that have produced reliable staging evidence; never ban on a client-brand guess alone.

A green CI build proves compilation/tests passed for that commit. It does not prove zero false positives or complete protection on a live server. Continue collecting staff-reviewed evidence and adding regression tests for confirmed bypasses.

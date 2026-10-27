# ANNA AC movement and combat staging validation

## Safety rules

- Do not validate new heuristics on the live Anna MC network first.
- Keep experimental movement/combat checks disabled and all automatic actions disabled during baseline collection.
- Test the low-TPS policy with both normal TPS and deliberate staging load. Confirm only the configured `settings.performance.pause-checks-on-low-tps` detector names are suspended; packet-rate, timer and click-rate telemetry should remain active unless explicitly added.
- Collect evidence from legitimate play before setting thresholds. A high ping, custom enchantment, knockback, velocity-changing plugin, or server lag must not be treated as proof by itself.
- A detector flag is a signal, not proof. Automatic punishment should require corroboration from independent detector families and a reviewable evidence trail.
- Run the matrix on the exact Paper build, Java version, plugin set, world settings, and proxy path used in production.

## Version-aware thresholds

Numeric settings for the supported movement/combat checks may be overridden at:

`checks.<check>.version-overrides.<version_key>.<setting>` (replace dots in version keys with underscores)

Resolution order is exact Minecraft version (for example `1.21.11`), then major.minor (for example `1.21`), then the ordinary `checks.<check>.<setting>` default. Only add an override when staging data demonstrates a real version-specific difference. Avoid assuming every patch release has identical movement behavior.

Example for `checks.movement-physics`:

```yaml
version-overrides:
  '1_21_11':
    base-horizontal-per-tick: 0.72
    sprint-multiplier: 1.3
  '1_21':
    max-ping-slack: 0.75
```

Server-version overrides and PacketEvents client-protocol overrides are supported for `movement`, `movement-physics`, `flight`, `jesus`, `spider-signal`, `knockback`, `reach`, `block-reach`, `wall-hit`, `fast-break`, `nuker-burst`, `container-steal`, `container-move`, `click-rate`, `combat-pattern`, `scaffold-signal`, `no-fall`, `packet-sanity`, `packet-rate`, `timer`, and `mace-damage`. Client overrides use the separate `client-version-overrides` map, keys with underscores, and only affect fields read through the version-aware resolver. A client protocol version is not a cheat-client brand fingerprint.

The `movement` and `movement-physics` checks expose `velocity-grace-ms` (0–5000, default 300 ms). After a server-applied `PlayerVelocityEvent`, the basic movement detector suppresses displacement flags and the physics detector rebases its sample baseline during that window. This reduces knockback/launch-pad false positives; setting it too high creates a short detection blind spot. Test it with the actual knockback and velocity-changing plugins before changing the default.

## Baseline and test matrix

For each test, record Paper build, Minecraft client version, ping range, TPS/MSPT, enabled movement plugins, expected outcome, detector VL, evidence entry, and whether staff review agrees.

1. **Normal movement:** walk, sprint, jump, sprint-jump, crouch, swimming, climbing, ice, slime, honey, soul sand/soil, cobwebs, ladders, water currents, elytra, vehicles, riptide, and server teleports.
2. **Latency and load:** repeat at low, medium, and high ping; simulate short TPS drops; test after respawn, world change, join, and teleport. There should be no accumulating movement baseline across a teleport or long pause. For `packet-sanity`, test position-packet baselines before/after server teleports, long packet gaps, supported client protocols, and plugins that modify movement; extreme steps must not be enabled for automatic punishment until these cases are clean.
3. **Legitimate combat:** hit targets at several distances and angles, attack moving targets, use different entity hitboxes, test high ping, knockback, shields, critical hits, potions, armor, enchantments, and custom combat plugins.
4. **Block reach:** use normal block-breaking and right-click interactions at legal range; test non-cube collision shapes, ignored materials, high ping, and custom interaction/protection plugins. Validate `block-reach` evidence on staging before enabling it.
4. **Mace:** test ordinary falls, Density enchantment levels, targets with different armor/effects, custom damage modifiers, cancelled damage, and low-TPS conditions. Do not enable automatic mace punishment until false-positive review is clean.
5. **Container looting:** test normal single/double chest shift-clicking, ender chests, shulker boxes, high-ping players, and every custom inventory GUI. Enable `container-steal` only on staging, then confirm ordinary looting does not sustain over-limit windows and controlled burst cases produce reproducible evidence.
5. **Container GUI movement:** test ordinary movement while a container is open, custom menus that intentionally permit movement, velocity/knockback, teleport transitions, inventory titles/types, and high ping. Keep `container-move` disabled unless the signal is repeatable without flags from legitimate custom GUIs.
6. **Negative controls:** players without cheats should not repeatedly trigger the same detector family during routine play. If they do, tune or disable the check rather than raising punishment urgency.
7. **Controlled adversarial tests:** on a private staging server, test one cheat behavior at a time, capture server logs/evidence, and verify that flags are reproducible. Do not use these tests on public players or treat one sample as a ban verdict.
8. **Punishment rehearsal:** keep `punishments.enabled: false` while tuning. Then use alerts only, next review-only warnings, and only later enable a per-check kick with a conservative threshold. Keep independent-family corroboration enabled.

## Promotion gate

Promote a check from staging only after:
- It builds and its unit tests pass.
- The full legitimate-play matrix passes without unexplained flags.
- Repeated controlled test cases produce reproducible evidence.
- Staff can explain each evidence record and reproduce the finding.
- Per-check thresholds, alert cooldown, VL decay, and action threshold are documented.
- Automatic punishment is disabled by default for checks that have not passed the gate.

## Current limitations

The movement-envelope check is heuristic, not a deterministic replay of vanilla client physics. Reach does not implement full historical hitbox/latency compensation. Mace damage can be altered by server plugins. Bukkit event-level observations cannot reveal every client input packet. These checks cannot promise instant detection of every hack or prove a player is cheating in every flagged case.

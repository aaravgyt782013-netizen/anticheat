# ANNA AC Cheat-Coverage Roadmap

ANNA AC targets Paper 1.21.11 / Java 21. It must detect behavior, not pretend it can reliably identify a client brand. Meteor, Wurst, Baritone, and custom clients change frequently; client-brand fingerprints are unreliable and easy to evade.

Legend:
- **Current signal**: code exists, but may be heuristic/experimental.
- **Needs implementation**: roadmap item, not currently detected reliably.
- **Server configuration**: prevent or limit the exploit using server-side configuration rather than a client detector.

## Client-feature coverage

| Cheat / behavior | Typical examples | ANNA AC status / safe approach |
|---|---|---|
| X-ray / cave finder / ore ESP | Meteor, Wurst, resource packs | **Server configuration:** Paper Anti-Xray. ANNA AC does not hide blocks itself. |
| KillAura / automated attacks | Meteor, Wurst, custom clients | **Current signal:** combat-pattern and reach heuristics. Not proof; needs stronger packet/history analysis. |
| Aim assist / aim bot | Meteor add-ons, custom clients | **Current signal:** weak repeated-rotation / hit-angle pattern. Needs calibrated multi-signal analysis; legitimate aim can look similar. |
| Reach | extended attack distance | **Current signal:** approximate hitbox-distance check; lacks complete historical hitbox/interpolation compensation. |
| AutoClicker / TriggerBot | automated clicks or attack timing | **Current signal:** arm-swing rate only. Needs attack packet timing and statistical analysis; CPS alone is not proof. |
| Velocity / Anti-knockback | reduced or ignored knockback | **Current signal:** observed displacement after velocity. Needs robust collision, input, and latency modelling. |
| Speed / Fly / AirWalk / Jesus / Spider / Step | abnormal movement | **Current signal:** conservative movement heuristics. Needs version-specific physics and block/liquid/vehicle states. |
| Timer / tick-rate manipulation | accelerated client actions | **Current signal:** packet-rate telemetry. Not a validated tick simulator; needs calibrated packet/tick analysis. |
| Scaffold / Tower / FastPlace | automated bridging/building | **Current signal:** repeated placement-angle pattern. High false-positive risk; needs placement-rate, support, rotation and movement context. |
| NoFall | suppressing fall damage | **Current signal:** landing/fall-damage correlation heuristic. Protection plugins and gameplay effects can confuse it. |
| FastBreak / Nuker / illegal mining rate | accelerated block breaking | **Needs implementation:** validate server-side break speed against tool, effects, block hardness, haste, and game mode. |
| Baritone / pathfinding / automated mining | long paths, tunnels, repetitive mining | **Needs implementation:** only flag high-confidence impossible or highly repetitive behavior over time. Pathfinding alone is not proof and cannot be reliably identified by brand. |
| Freecam / camera detach | camera moves independently | **Needs implementation:** server cannot directly see the player's camera; detect only resulting illegal interactions or impossible information access. |
| InventoryMove / AutoArmor / ChestStealer / AutoEat | automated inventory actions | **Needs implementation:** use legal server state and action-rate/context checks; many actions are legitimate and plugin-dependent. |
| Elytra / boat / vehicle movement exploits | abnormal gliding or vehicle motion | **Needs implementation:** separate movement models per movement mode and version. |
| Packet exploits / malformed or abusive packet rates | custom clients, protocol abuse | **Current signal:** optional PacketEvents packet-rate telemetry. Needs packet-specific validation, limits and regression tests. |
| Chat spam / command spam / botting | automated chat and requests | **Needs implementation:** rate limits and account/session signals; avoid punishing shared networks or legitimate bursts. |
| Force OP / privilege escalation attempts | unauthorized op/deop and permission commands | **Current signal:** command guard and operator-list audit. Cannot stop malicious plugins, host/file access, or a compromised console. |
| Skin / nametag / fullbright / cosmetic-only modules | visual-only client features | Often **not server-detectable** and not necessarily a gameplay cheat. Enforce a clear server policy rather than claiming detection. |

## Detection and punishment policy

1. Collect evidence and alert staff first.
2. Require repeated, independent signals for a high-confidence flag; decay violation levels when evidence stops.
3. Exempt creative/spectator, vehicles, special blocks, potion effects, latency spikes, and server/plugin mechanics where applicable.
4. Keep auto-kicks and bans disabled until the check has passed staging tests. Never punish solely because a player may be using Meteor, Wurst, or Baritone.
5. Record the server version, ping, world, game mode, check name, thresholds, and a bounded evidence sample so staff can review false positives.
6. Test with normal players and authorized test clients across low/high ping, proxy, PvP, bridging, mining, liquids, knockback, and world changes.

## What this project does not claim

- It does not detect every hack or reliably identify installed client mods.
- It is not yet equivalent to mature systems such as Grim or Vulcan.
- The current code has not been live-validated against a Paper 1.21.11 staging server in this repository workflow.
- A server plugin cannot stop a person with panel/file/console access or a malicious plugin from changing the server.

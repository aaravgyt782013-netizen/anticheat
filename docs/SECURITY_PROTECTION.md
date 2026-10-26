# ANNA AC Server Security: Anti-Nuke and Plugin Integrity

This subsystem adds **server-side guardrails and audit signals**. It is defense in depth, not a guarantee that a compromised server or malicious plugin can be stopped.

## Configuration file

Edit \`plugins/ANNA-AC/security.yml\`. Every option has a nearby comment. Run \`/annaac reload\` after edits; fully restart after changing installed plugins or plugin files.

## Anti-nuke guard

The master switch is \`security.antinuke.enabled\`.

- \`block-bulk-commands: true\` blocks the configured destructive player command labels, including vanilla bulk-edit commands (\`fill\`, \`setblock\`, \`clone\`), common WorldEdit batch commands, plugin-management/reload commands, and risky kill/summon commands. It does **not** cancel console commands. Use the editable \`protected-bulk-command-labels\` list to match the aliases and tools installed on your server.
- \`allow-bulk-command-permissions: [annaac.security.bypass]\` is the explicit exception list. Players do not receive this permission by default. Grant it only to people trusted to run world-changing commands; it also bypasses the optional block-break rate limiter. The player still needs permission for the underlying command.
- \`explosion-block-limit: true\` cancels block-damaging explosions that affect more than \`max-explosion-affected-blocks\` blocks (default 250). This applies in all worlds unless listed in \`ignored-worlds\`. Test TNT farms, beds/anchors, custom mobs, and region-protection plugins on staging.
- \`block-break-rate-limit: false\` is intentionally off initially. Enable only after tuning \`block-break-window-ms\`, \`max-block-breaks-per-window\`, and \`block-break-block-ms\` against your mining tools, effects, and custom plugins.
- Console bulk commands are logged when \`audit-console-bulk-commands: true\`. They are audited rather than blocked so owners retain an emergency recovery path.
- \`kick-on-blocked-bulk-command\` is off by default; blocking and alerting are preferred over disconnecting a player for a command typo.

Do not give the bypass permission to every operator automatically. A compromised operator account may be the threat you are defending against.

## Plugin integrity monitor

The master switch is \`security.plugin-monitor.enabled\`.

### JAR hash inventory

The initial scan creates \`plugins/ANNA-AC/plugin-hashes.yml\` with SHA-256 hashes of the plugin JARs it sees. Later scans compare the current folder with that inventory and can alert on new, changed, or missing JAR files. Scheduled scans run at the configured interval (default five minutes); lifecycle alerts ignore the normal startup grace period.

**The first inventory is not automatically trustworthy.** It records whatever is present at first start. Before treating it as a known-good baseline, review every plugin's source, publisher, download location and version, and investigate any static-risk alert.

### Static malware-risk scan

The scanner inspects bounded portions of JAR class constants and optionally common text resources, including plugin metadata/properties/JSON/script files. It looks for combinations of potentially risky capabilities such as process execution, networking, command dispatch, operator-management strings, and file deletion/world-path references.

- \`static-scan-enabled\`: enable or disable the heuristic scan.
- \`minimum-risk-score\`: raise it to reduce noise or lower it to request more manual reviews.
- \`scan-resource-files\`: additionally scan supported metadata/script resource types.
- \`max-plugin-jars-per-scan\`, \`max-entries-per-jar\`, \`max-entry-bytes\`, and \`max-scan-bytes-per-jar\`: bound scan work.
- \`ignore-plugin-files\`: exact JAR filenames excluded from hash alerts and static scanning. Use carefully because ignored plugins lose these checks.
- \`audit-plugin-lifecycle\`: alert when a plugin enables/disables after startup grace.

A risky capability string is **not proof of malware**. Legitimate plugins may use networking, process APIs, command dispatch, or file management. Obfuscation, reflection, encrypted payloads, native code and behavior that happens only at runtime can evade this static scanner. It is not an online reputation database and cannot guarantee every backdoor is found.

ANNA AC never executes plugin JARs as part of scanning and never automatically deletes, unloads or disables a plugin based on a heuristic alert. Review the named JAR and evidence, stop the server before removing a suspect plugin, and keep backups outside the server's writable plugin directory.

## Admin commands

- \`/annaac status\`: includes the anti-nuke and plugin-monitor switch states.
- \`/annaac securityscan\`: run an immediate JAR integrity/static-risk scan. Findings are sent through configured security alerts.
- \`/annaac securityscan approve\`: explicitly replace the hash inventory with the JARs currently present. This does **not** prove they are safe. It is allowed from console/RCON or a player with both \`annaac.admin\` and \`annaac.security.baseline-approve\`. Use it only after reviewing every plugin JAR and its origin.

The hash approval operation is intentionally separate from normal scanning. Updates are never silently trusted just because a new hash appeared.

## Staging checklist

1. Back up the server, then test on a private clone.
2. Verify that a non-bypass player cannot run \`fill\`, \`clone\`, configured WorldEdit batch commands or configured plugin-management commands.
3. Grant the bypass to one trusted tester and verify the command is then merely audited and permission checks still apply.
4. Test the explosion cap with a large block-damaging explosion on a disposable world; verify normal TNT usage remains acceptable.
5. Leave block-break rate limiting off until the legitimate mining tests pass.
6. Run \`/annaac securityscan\`; inspect the generated baseline and any static-risk alerts.
7. On staging only, add/change/remove a harmless test JAR and verify hash alerts. Test plugin enable/disable alerts after the startup grace period.
8. Verify the Discord alert channel stays private and that webhooks are never committed to GitHub.

## Security limits

A plugin loaded by Bukkit executes with the server process's privileges. A malicious plugin can modify worlds, run code, access files, send network traffic, alter other plugins, or disable monitoring. Static scanning and event guards cannot reliably stop arbitrary code already running in-process. Also secure host-panel access, keep an offline backup, restrict console/RCON, audit plugin downloads, and use Paper's built-in Anti-Xray/world protections separately.

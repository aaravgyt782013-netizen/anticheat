# ANNA AC Discord Integration

ANNA AC sends anti-cheat and security alerts to a Discord webhook. It does not need a Discord bot token, bot application, or privileged Gateway intents for outbound alerts. This integration is one-way (Minecraft -> Discord); it does not read Discord messages or execute remote commands.

## 1. Create a channel webhook

1. In Discord, open the staff/security alerts channel.
2. Open **Edit Channel → Integrations → Webhooks → New Webhook** (wording may vary).
3. Name it `ANNA AC | Security Alerts`, choose the channel, and copy the webhook URL.
4. Treat the URL like a password. Anyone with it can post to that channel. Do not commit it to GitHub, screenshots, or public chat.

## 2. Configure ANNA AC

Open `plugins/ANNA-AC/discord.yml`. Keep the global prefix and language in `config.yml`. Set the following values inside the existing `alerts:` section in `discord.yml`:

    settings:
      prefix: "&8[&bANNA AC&8] &r"
      language: "en"

    alerts:
      console: true
      staff-chat: true
      discord-enabled: true
      discord-webhook: "PASTE_YOUR_PRIVATE_DISCORD_WEBHOOK_URL_HERE"
      discord-username: "ANNA AC | Security Alerts"
      discord-avatar-url: ""
      discord-embed-enabled: true
      discord-embed-color: 3447003
      discord-include-player-uuid: false
      discord-include-detail: true
      discord-role-mention-id: ""
      discord-timeout-seconds: 6

Replace the placeholder URL locally. Do not upload a config containing the real URL. The plugin accepts HTTPS webhook URLs hosted on `discord.com` or `discordapp.com` with an `/api/webhooks/` path. It restricts allowed mentions so player names/details cannot ping everyone or arbitrary roles.

Optional role mention: copy the exact staff role ID with Discord Developer Mode and set `discord-role-mention-id` to that numeric ID. Leave it empty to avoid role pings. Only the configured role is allowed to be mentioned.

## 3. Restart and test

1. Save the file and fully restart the server. Do not use `/reload`.
2. Join as an account with `annaac.admin` and run `/annaac reload` after any later config/language edit. For the first setup, a full restart is safest.
3. Trigger a test only on a private staging server by generating an alert from an enabled check; do not enable risky checks on the public server just to test Discord.
4. Confirm the embed arrives. If it does not, check the console for an HTTP status or webhook URL validation warning.
5. If you receive HTTP 401/404, regenerate the webhook URL and update the config. HTTP 403 usually means the webhook/channel permissions are wrong. Network/firewall issues may show a delivery exception.

## Security and limits

- Keep the webhook URL private; regenerate it immediately if exposed.
- Use a dedicated staff-only channel with minimal permissions.
- ANNA AC sends outbound alerts only. It is not a two-way Discord bridge and does not support remote commands.
- Alerts are delivered asynchronously so webhook network latency does not block the game thread.
- Discord delivery failures are logged without printing the secret URL.
- Configure alert thresholds and validate checks on staging before enabling punishments.

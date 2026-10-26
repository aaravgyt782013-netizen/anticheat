# Paper Anti-Xray for ANNA MC

ANNA AC does not replace block obfuscation. For Paper 1.21.11, use Paper's built-in Anti-Xray: it changes block data sent to clients, which is more effective than trying to identify an X-ray client after it has seen real ore positions.

Official guide: https://docs.papermc.io/paper/anti-xray/
World configuration reference: https://docs.papermc.io/paper/reference/world-configuration/

## 1. Back up and edit the right file

1. Stop the server cleanly and back up the server configuration.
2. Open config/paper-world-defaults.yml in the server root.
3. Find the existing anticheat → anti-xray section. Edit that section; do not paste a duplicate anticheat key.
4. World-specific overrides live in that world's paper-world.yml and can override the defaults.
5. Restart the server after editing. Do not use /reload.

## 2. Recommended starting point: engine mode 2

Merge these values into the existing anti-xray section. Keep the indentation exactly as YAML requires.

    anticheat:
      anti-xray:
        enabled: true
        engine-mode: 2
        hidden-blocks:
          - copper_ore
          - deepslate_copper_ore
          - raw_copper_block
          - gold_ore
          - deepslate_gold_ore
          - iron_ore
          - deepslate_iron_ore
          - raw_iron_block
          - coal_ore
          - deepslate_coal_ore
          - lapis_ore
          - deepslate_lapis_ore
          - redstone_ore
          - deepslate_redstone_ore
          - diamond_ore
          - deepslate_diamond_ore
          - emerald_ore
          - deepslate_emerald_ore
        lava-obscures: false
        max-block-height: 64
        replacement-blocks:
          - andesite
          - calcite
          - coal_ore
          - deepslate_coal_ore
          - deepslate
          - diorite
          - dirt
          - granite
          - gravel
          - stone
          - tuff
        update-radius: 2
        use-permission: false

**Important:** this is a starting example, not a complete replacement for your generated config. Preserve other existing Paper settings and merge the block lists with the current version's recommended list. Adjust max-block-height for your world and ore-generation range. Mode 2 uses fake ore-like blocks in concealed areas; it can increase chunk/network work compared with mode 1.

### Optional: hide ores exposed to caves

Paper documents adding air to hidden-blocks for engine mode 2/3. This can reduce cave/X-ray visibility but may cause client FPS drops and visible fake holes. Test on a staging copy before using it on Anna MC.

### Nether and End

Use a separate override in the Nether world folder for ancient_debris, nether_gold_ore, and nether_quartz_ore, with a suitable max-block-height (Paper's guide uses 128 as an example). Anti-Xray is usually unnecessary in the End; configure that world separately if needed. Confirm actual world folder names before creating overrides.

## 3. Permissions and verification

- Keep use-permission: false unless you intentionally need a bypass permission.
- If enabling permission bypass, protect paper.antixray.bypass; do not give it to ordinary players.
- Restart fully, then test with a normal client and an X-ray test client in a private staging world.
- Check ores above max-block-height, exposed ores, and each world/dimension.
- Anti-Xray does not prevent seed-reversal attacks if a player obtains enough information to recover the world seed. Keep seeds and server files private; review Paper's feature-seed settings for your version.
- Anti-Xray is not an ANNA AC violation check and does not identify which client a player uses.

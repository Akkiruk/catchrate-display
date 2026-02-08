# Cobblemon Catch Rate Display

[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.1-green.svg)](https://www.minecraft.net/)
[![Fabric API](https://img.shields.io/badge/Fabric%20API-0.116%2B-orange.svg)](https://fabricmc.net/)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1%2B-red.svg)](https://neoforged.net/)
[![Cobblemon](https://img.shields.io/badge/Cobblemon-1.7.1%2B-blue.svg)](https://cobblemon.com/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

A **pure client-side** Architectury mod for Minecraft 1.21.1 that displays real-time catch rates during Cobblemon battles. Works on any Cobblemon server  — **no server installation required!**

## Compatibility

| Component | Supported Versions |
|-----------|-------------------|
| Minecraft | 1.21.1 |
| Cobblemon | 1.7.1+ |
| **Fabric** | |
| Fabric Loader | 0.16.7+ |
| Fabric API | 0.116.7+ |
| Fabric Language Kotlin | 1.13.4+ |
| **NeoForge** | |
| NeoForge | 21.1.77+ |
| Kotlin for Forge | 5.11.0+ |

## Features

### 🎯 Pure Client-Side Operation
- **No server mod required!** Works on any Cobblemon server
- All calculations use Cobblemon's synced client data
- 100% accurate catch rates without server installation
- Supports all Pokéball types including:
  - **Level Ball** (uses active battler level)
  - **Repeat Ball** (uses synced Pokédex data)
  - **Lure Ball** (detects fishing encounters)
  - **Love Ball** (checks same species in party)
  - And all other standard/special balls

### 📊 Real-Time Catch Rate Display
- Live percentage calculations during battle
- Color-coded indicators based on success chance
- HP visualization with percentage
- Status effect multipliers (Sleep, Burn, Paralysis, etc.)
- Ball-specific multipliers and condition descriptions
- Turn counter for Timer Ball and Quick Ball

### Ball Comparison Panel
- **Hold G** during battle to see all Pokéballs ranked by effectiveness
- Shows catch rate, multiplier, and conditions for each ball
- Helps you choose the optimal ball for the situation

### Customizable HUD
- 9 anchor positions (corners, edges, center)
- Movable with configurable keybinds
- Toggle on/off with K key
- Position saved to config

### ⌨️ Keybinds & Commands
| Key | Action |
|-----|--------|
| K | Toggle HUD visibility |
| G (hold) | Show ball comparison panel |
| Unbound | Reset HUD position |
| Unbound | Cycle anchor position |

All keybinds configurable in Options → Controls → "Catch Rate Display".

**Debug Commands:**
- `/catchrate debug` — Toggle verbose logging (current session only)
- `/catchrate info` — Show mod version, config, and environment details

### 🎨 Visual Design
- 🟢 Green: ≥75% (excellent)
- 🟡 Yellow: 50-75% (good)
- 🟠 Gold: 25-50% (fair)
- 🔴 Red: <25% (poor)

## Installation

### Requirements

**For Fabric:**
- Minecraft 1.21.1
- Fabric Loader 0.16.7+
- Fabric API 0.116.7+
- Fabric Language Kotlin 1.13.4+
- Cobblemon 1.7.1+

**For NeoForge:**
- Minecraft 1.21.1
- NeoForge 21.1.77+
- Kotlin for Forge 5.11.0+
- Cobblemon 1.7.1+

### Client Installation
1. Download the appropriate version from [Modrinth](https://modrinth.com/mod/catchrate-display) or [CurseForge](https://www.curseforge.com/minecraft/mc-mods/catchrate-display)
   - `catchrate-display-fabric-X.Y.Z.jar` for Fabric
   - `catchrate-display-neoforge-X.Y.Z.jar` for NeoForge
2. Place in your `mods/` folder
3. Launch Minecraft

**No server installation needed!** This mod is pure client-side and works on any Cobblemon server.

## Usage

1. Enter a Cobblemon battle
2. Hold a Pokéball in your hand
3. View the HUD showing catch rate, HP, status, and ball effectiveness

## Building from Source

This mod uses Architectury to support both Fabric and NeoForge from a single codebase.

```bash
git clone https://github.com/Akkiruk/catchrate-display.git
cd catchrate-display

# Build both loaders
./gradlew build

# Or build specific loaders
./gradlew :fabric:build
./gradlew :neoforge:build
```

Output jars will be in:
- `fabric/build/libs/catchrate-display-fabric-X.Y.Z.jar`
- `neoforge/build/libs/catchrate-display-neoforge-X.Y.Z.jar`

## License

MIT License - see [LICENSE](LICENSE) for details.

## Links

- [Modrinth](https://modrinth.com/mod/catchrate-display)
- [CurseForge](https://www.curseforge.com/minecraft/mc-mods/catchrate-display)
- [GitHub Issues](https://github.com/Akkiruk/catchrate-display/issues)
- [Discord Support](https://discord.gg/cobblemon) (Cobblemon Discord)

## Acknowledgments

- [Cobblemon Team](https://cobblemon.com/) for the amazing Pokémon mod
- [Fabric Team](https://fabricmc.net/) for the modding framework
- [NeoForge Team](https://neoforged.net/) for the forge continuation
- [Architectury](https://github.com/architectury/architectury-api) for multiloader support

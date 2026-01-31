# Cobblemon Catch Rate Display

[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.x-green.svg)](https://www.minecraft.net/)
[![Fabric API](https://img.shields.io/badge/Fabric%20API-0.116%2B-orange.svg)](https://fabricmc.net/)
[![Cobblemon](https://img.shields.io/badge/Cobblemon-1.6.0%2B-blue.svg)](https://cobblemon.com/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
![Version](https://img.shields.io/badge/version-1.2.2-blue)

A Fabric mod for Minecraft 1.21+ that displays real-time catch rates during Cobblemon battles.

## Compatibility

| Component | Supported Versions |
|-----------|-------------------|
| Minecraft | 1.21, 1.21.1 |
| Cobblemon | 1.6.0+ |
| Fabric Loader | 0.15.0+ |
| Fabric Language Kotlin | 1.10.0+ |

## Features

### Real-Time Catch Rate Display
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

### Keybinds
| Key | Action |
|-----|--------|
| K | Toggle HUD visibility |
| G (hold) | Show ball comparison panel |
| HOME | Reset HUD position |
| END | Cycle anchor position |

All keybinds configurable in Options → Controls → "Catch Rate Display".

### Hybrid Client-Server Architecture
- **With server mod**: 100% accurate calculations using Cobblemon's APIs
- **Without server mod**: Intelligent fallback with estimates for most balls
- Server-required balls show clear warnings when needed

### Visual Design
- 🟢 Green: ≥75% (excellent)
- 🟡 Yellow: 50-75% (good)
- 🟠 Gold: 25-50% (fair)
- 🔴 Red: <25% (poor)

## Installation

### Requirements
- Minecraft 1.21 or 1.21.1
- Fabric Loader 0.15.0+
- Fabric API
- Fabric Language Kotlin 1.10.0+
- Cobblemon 1.6.0+

### Client Installation
1. Download from [Releases](https://github.com/Akkiruk/catchrate-display/releases)
2. Place in your `mods/` folder
3. Launch Minecraft with Fabric

### Server Installation (Optional)
Installing on the server provides 100% accuracy for all ball types including Love Ball, Level Ball, Repeat Ball, and Lure Ball.

## Usage

1. Enter a Cobblemon battle
2. Hold a Pokéball in your hand
3. View the HUD showing catch rate, HP, status, and ball effectiveness

## Building from Source

```bash
git clone https://github.com/Akkiruk/catchrate-display.git
cd catchrate-display
./gradlew build
```

Output: `build/libs/catchrate-display-1.2.2.jar`

## License

MIT License - see [LICENSE](LICENSE) for details.

## Acknowledgments

- Cobblemon Team for the amazing mod
- Fabric Team for the modding framework

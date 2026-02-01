# Changelog

## [1.2.10] - 2026-01-31

### Changed
- **Debug logging enabled with throttling**
  - Detailed catch rate calculations logged for troubleshooting
  - Throttled to every 2 seconds to prevent log spam
  - Logs when owned Pokemon are skipped (with owner UUID)
  - Shows all calculation components: base rate, ball multiplier, HP, status, level bonus

## [1.2.9] - 2026-01-31

### Fixed
- **Catch rate HUD no longer shows for owned Pokémon**
  - Player's own Pokémon no longer trigger the catch rate display
  - Other players' Pokémon are also filtered out
  - Only wild Pokémon show catch statistics when looking at them

## [1.2.8] - 2026-01-31

### Fixed
- **Love Ball now works correctly!**
  - Implemented manual check that verifies same species + opposite gender against player's party
  - Overrides Cobblemon API result if the manual check passes but API returns wrong value
  - Shows detailed feedback: which party Pokémon matches and gender symbols (♂/♀)
  - Properly handles genderless Pokémon (cannot trigger Love Ball)

- **Quick Ball no longer shows 5x bonus outside of combat**
  - Quick Ball's 5x multiplier only applies on turn 1 of BATTLE
  - Out of combat throws correctly show 1x multiplier
  - Timer Ball also shows "Only works in battle" message

### Added
- **0.5x out-of-combat penalty indicator**
  - Wild Pokémon HUD now shows "⚠ Out of combat: 0.5x" warning
  - Makes it clear that direct throws have reduced catch rate
  - Box height increased to fit the new indicator

## [1.2.7] - 2026-01-31

### Fixed
- **Out-of-combat catch rate display now works for ALL balls**
  - Master Ball now shows 100% guaranteed catch (was showing 1x)
  - Safari Ball: 1.5x when used outside of battle
  - Sport Ball: 1.5x always
  - Moon Ball: Night-time bonus with moon phase
  - Dive Ball: 3.5x when underwater
  - Dream Ball: 4x on sleeping Pokémon
  - Beast Ball: 5x on Ultra Beasts, 0.1x otherwise
  - Dusk Ball: Now properly shows 3.5x at light level 0, 3x at levels 1-7

- **Ancient ball multipliers corrected to match Cobblemon Wiki:**
  - Ancient Jet Ball: **2x** (was 1x)
  - Ancient Gigaton Ball: **2x** (was 1x)
  - Ancient Wing Ball: **1.5x** (was 1x)
  - Ancient Leaden Ball: **1.5x** (was 1x)
  - Ancient Feather Ball: 1x (throwPower only)
  - Ancient Heavy Ball: 1x (throwPower only)
  - Ancient Great Ball: 1.5x
  - Ancient Ultra Ball: 2x
  - Ancient Origin Ball: Guaranteed catch

## [1.2.6] - 2026-01-31

### Changed
- **Refactored ball multiplier calculation to use Cobblemon's native PokeBall API**
  - Uses `PokeBalls.getPokeBall()` and `pokeBall.ancient` flag instead of string matching
  - Uses `pokeBall.catchRateModifier.isGuaranteed()` for Master/Origin balls
  - More reliable and maintainable approach

### Fixed
- Ancient balls now correctly show multipliers based on Cobblemon's actual data:
  - Ancient Great Ball: 1.5x
  - Ancient Ultra Ball: 2x
  - Ancient Origin Ball: Guaranteed catch
  - All other ancient balls (Feather, Wing, Jet, Heavy, Leaden, Gigaton): 1x (throwPower only, no catch rate bonus)

## [1.2.5] - 2026-01-31

### Added
- Pokemon-style UI design matching Cobblemon's battle interface aesthetics
- Visual catch rate progress bar with color-coded fill
- Cobblemon-style health bars with proper RGB color gradients
- Status effect icons (💤 Sleep, ❄ Frozen, ⚡ Paralysis, 🔥 Burn, ☠ Poison)
- Ball condition indicators (● met / ○ not met)
- Wild Pokemon indicator with red accent styling
- Rounded corners and gradient backgrounds for all panels
- Alternating row backgrounds in ball comparison panel for better readability

### Changed
- Complete UI overhaul with styled panels and borders
- Dynamic border colors based on catch chance (green → yellow → orange → red)
- Improved visual hierarchy with better text shadows and colors
- Ball comparison panel now uses gold borders (0xFFFFAA00) matching Cobblemon
- Shortened ball names in display (removed " Ball" suffix for cleaner look)
- Loading screen now has animated dots

### Fixed
- Out-of-combat display now properly shows catch rates for wild Pokemon
- Improved Pokemon detection with extended raycast fallback

## [1.2.4] - 2026-01-31

### Fixed
- Ancient ball catch rate multipliers (Jet, Wing, Heavy, Leaden, Gigaton, Feather)

## [1.2.3] - 2026-01-31

### Fixed
- Ball comparison panel footer text no longer covered by ball list

## [1.2.2] - 2026-01-30

Initial public release

- Real-time catch rate display during battles
- Ball comparison panel showing all available Poké Balls
- Server-side accuracy using actual HP and status data
- Configurable HUD position and colors
- Support for vanilla and Cobblemon custom balls

# Changelog

## [1.2.23] - 2026-02-01

### Fixed
- Catch rate calculation now matches Cobblemon exactly (used float division instead of integer division for low level bonus)

## [1.2.22] - 2026-02-01

### Fixed
- Ball comparison panel (G key) now updates with turn count for Timer Ball etc.

## [1.2.21] - 2026-02-01

### Changed
- HUD now positioned lower by default (closer to hotbar)
- HUD width dynamically adjusts to fit text content

## [1.2.20] - 2026-01-31

### Changed
- Each modifier now has its own row (HP, Status, Ball)
- Added ball condition description showing why the ball has its current effect
- Box height dynamically adjusts based on status presence

## [1.2.19] - 2026-01-31

### Fixed
- Out-of-combat HUD no longer shows for player-owned Pokemon (shoulder/follower Pokemon)

## [1.2.18] - 2026-01-31

### Changed
- Removed HP bar from catch rate tooltip
- HP now displays as multiplier (e.g., "HP 0.87x") showing direct catch rate impact
- Status effects now show multiplier on same line (e.g., "Sleep 2.5x")
- Reduced tooltip height for cleaner appearance

## [1.2.17] - 2026-01-31

### Changed
- Updated guaranteed catch display text to "★ 100% CATCH ★"

## [1.2.16] - 2026-01-31

### Changed
- Consolidated catch rate formula into single source of truth (`CatchRateFormula.kt`)
- Extracted HUD drawing utilities to `HudDrawing.kt` - removes ~150 lines of duplicate code
- Ball detection now uses Cobblemon's PokeBallItem API instead of fragile string parsing
- Ball comparison panel now uses shared drawing utilities
- Removed ~100 unused ball constant definitions
- Removed unused `renderLoadingHud()` method
- Set `DEBUG_ENABLED = false` for production

### Technical
- New `CatchRateFormula.kt` - single source for catch math, status multipliers, HP calculations
- New `CatchRateConstants.kt` - centralized constants (trimmed to only used values)
- New `BallContextFactory.kt` - factory for building ball context from different Pokemon sources
- New `HudDrawing.kt` - shared panel/bar drawing, color utilities
- `isPokeball()` and `getBallId()` now delegate to `CatchRateCalculator.getPokeBallFromItem()`
- Deprecated `BallMultiplierCalculator.formatBallName()` in favor of `CatchRateFormula`

## [1.2.15] - 2026-01-31

### Fixed
- **Ancient Balls now display correct multiplier in combat!**
  - Ancient balls (Jet, Gigaton, Wing, Leaden, Ancient Ultra, Ancient Great) were showing 1x in combat
  - This was because Cobblemon API uses `throwPower` not `catchRateModifier` for ancient balls
  - Added server-side override to use unified calculator for ancient ball multipliers
  
- **Love Ball now correctly checks the ACTIVE BATTLER only!**
  - Previously was checking if ANY Pokemon in your party had opposite gender (wrong)
  - Now only checks the Pokemon you actually have in play during battle
  - Out of combat: Shows 1x with "Need active battler" (not "Only works in battle")
  - 2.5x if your active battler is opposite gender
  - 8x if your active battler is same species + opposite gender

- **Timer Ball out-of-combat message improved**
  - Now shows "Scales with turns" instead of "Only works in battle"

- **Safari Ball fixed** - Now correctly shows 1.5x always (Cobblemon has no safari zone)

### Technical
- Changed `BallContext.partyPokemon` to `BallContext.activeBattler` - now tracks single active Pokemon
- Added `getActiveBattler()` helper to extract player's active Pokemon from battle (side1)
- Server-side now gets active Pokemon from `playerActor.activePokemon.firstOrNull()`
- Ancient ball override added before Love Ball override in server networking
- Removed unused `battle` parameter from `CatchRateCalculator.calculateCatchRate()`
- Removed unused `ClientBattle` import from `CatchRateCalculator.kt`
- Removed unused `calculateSafariBall()` function

## [1.2.14] - 2026-01-31

### Fixed
- **Love Ball logic corrected!**
  - Now gives **2.5x** for opposite gender (any species)
  - Gives **8x** for same species + opposite gender
  - Previously only checked for same species + opposite gender (too strict)

## [1.2.13] - 2026-01-31

### Fixed
- **Love Ball now works in client-only mode!**
  - Client-side calculator now accesses player's party from `CobblemonClient.storage`
  - Love Ball correctly checks same species + opposite gender even without server mod
  - Out-of-combat Love Ball display now shows accurate multiplier and reason

### Technical
- `BallComparisonCalculator` now builds `BallContext` with party info from client storage
- New `getClientPartyMembers()` helper method extracts party for unified calculator
- Graceful fallback if party access fails (shows "Requires party info")

## [1.2.12] - 2026-01-31

### Changed
- **Major refactor: Unified ball calculation system**
  - Created `BallMultiplierCalculator` as single source of truth for all ball multipliers
  - Combined 3 duplicate systems (`getBallInfo()`, `getBallInfoForWorld()`, `getBallConditionInfo()`) into one
  - `BallContext` data class abstracts Pokemon info, environment, battle state, and party info
  - Consistent ball logic across client-side (battle HUD, world HUD) and server-side calculations
  - Love Ball check now unified - uses party info from context when available
  - All special balls (Quick, Timer, Dusk, Net, Nest, Love, Level, Heavy, Fast, Moon, Dream, Beast) handled in one place

### Technical
- New `BallMultiplierCalculator.kt` with:
  - `BallContext` - captures all relevant Pokemon/environment state
  - `PartyMember` - lightweight class for Love Ball party checks
  - `BallResult` - unified return type with multiplier, conditionMet, reason, isGuaranteed, requiresServer
  - `calculate(ballId, ctx)` - single entry point for all ball calculations
- `BallComparisonCalculator` now delegates to unified calculator
- `CatchRateServerNetworking` uses unified calculator for condition descriptions
- Removed ~250 lines of duplicate ball logic

## [1.2.11] - 2026-01-31

### Changed
- **Comprehensive Love Ball debug logging**
  - Logs every step of the Love Ball check process
  - Shows wild Pokemon species and gender
  - Shows each party member's species and gender
  - Reports exact comparison results (sameSpecies, oppositeGender)
  - Logs API returned values vs manual check results
  - Clear indication when override is applied or why it fails

### Technical Debt Identified
- Found 3 duplicate ball calculation systems that should be unified:
  - `getBallInfo()` - client-side for in-battle
  - `getBallInfoForWorld()` - client-side for out-of-combat
  - `getBallConditionInfo()` - server-side
- Future refactor planned to consolidate these

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

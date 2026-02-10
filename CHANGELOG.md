# Changelog

## [2.5.5] - 2026-02-10

### Fixed
- NeoForge mod loading crash caused by @JvmStatic on @SubscribeEvent methods (Kotlin objects require non-static methods for instance-based event bus registration)

---

## [2.5.4] - 2026-02-09

### Fixed
- Nest Ball off-by-one: level 30 now correctly gets 1.1x instead of falling through to 1x
- NeoForge @SubscribeEvent methods annotated with @JvmStatic for proper event bus registration
- Night start tick corrected from 12000 to 13000 to match Minecraft's actual mob-spawning threshold
- Cursor restoration: hideCursor/showCursor now saves and restores the previous GLFW cursor mode instead of assuming NORMAL
- PokeBall cache upgraded from HashMap to ConcurrentHashMap to prevent corruption under concurrent access

### Changed
- BallComparisonCalculator: renamed tick-based counter fields to frame-based (lookupFrameCounter, etc.)

---

## [2.5.3] - 2026-02-09

### Fixed
- Ball names in comparison panel and single-ball HUD now use Cobblemon's localized item names instead of hardcoded English
- Comparison panel width now accounts for header and footer text length, preventing overflow in languages with longer translations
- Key name in "Release to close" footer wrapped in brackets for clarity

---

## [2.5.2] - 2026-02-09

### Fixed
- Debug logging no longer spams logs every tick — all per-frame logging now uses change-detection
- Consolidated 5 separate debug methods into 2 (one-shot events + change-only)
- Ball multiplier checks, catch rate calculations, and owned-pokemon skips only log when values actually change

---

## [2.5.1] - 2026-02-08

### Fixed
- VERSION constant stuck at 2.1.2 for 12 releases (now auto-injected from gradle.properties at build time)

---

## [2.5.0] - 2026-02-08

### Added
- Cursor now hides when ball comparison menu is open (G key)
- Updated cross-platform implementation guidelines for stricter Fabric/NeoForge parity

---

## [2.4.3] - 2026-02-08

### Fixed
- Status condition showing "None 2.5x" instead of "Asleep 2.5x" during battle (raw path vs display name mismatch)

---

## [2.4.2] - 2026-02-08

### Fixed
- Safari Ball no longer gives 1.5x multiplier during battle (out-of-combat only)
- Debug logger now only logs on state changes instead of on a timer

### Removed
- Time-throttled debug logging (replaced with change-based logging)

---

## [2.4.1] - 2026-02-08

### Fixed
- Timer Ball now correctly gives 1x multiplier on turn 1 (was incorrectly giving 1.3x)

---

## [2.4.0] - 2026-02-08

### Changed
- Compile against Cobblemon 1.6.0 to catch API breaks at build time
- Lower Fabric Loader minimum to 0.15.0
- Fix fabric.mod.json FLK requirement to match actual Kotlin 2.2.0 dependency

### Added
- CI compatibility matrix: builds verified against Cobblemon 1.6.0 through 1.7.3
- Graceful feature degradation: catch rate displays base-only fallback if ball calculations fail
- Comparison panel falls back to single-ball HUD on error
- Out-of-combat HUD silently recovers from API failures

---

## [2.3.2] - 2026-02-08

### Fixed
- All catch(Exception) blocks now catch(Throwable) to handle Cobblemon API version mismatches
- Guarded catchRateModifier.isGuaranteed() calls against IncompatibleClassChangeError
- Added top-level safety nets to HUD renderer and ball comparison calculator
- Eliminated force-unwrap race condition in cached result access

---

## [2.3.1] - 2026-02-08

### Fixed
- Crash with Cobblemon 1.6.x due to PokeBalls.getPokeBall() API incompatibility (IncompatibleClassChangeError)

---

## [2.3.0] - 2026-02-08

### Added
- Unknown Pokémon obfuscation: name, catch rate, and HP multiplier show as "???" for species not yet in your Pokédex
- New `obfuscateUnknown` config option (default: true) to toggle obfuscation
- `BallContextFactory.isSpeciesKnown()` public API for Pokédex encounter checks

---

## [2.2.0] - 2026-02-08

### Changed
- Unified in-battle and out-of-combat HUD into a single consistent layout
- HUD header now shows Pokemon name and level instead of generic "Catch Rate" text
- Replaced HP health bar with text-based HP multiplier display (e.g. "HP 0.85x")
- HP multiplier color-coded by catch benefit (green = low HP, red = full HP)

### Removed
- Health bar rendering code (drawHealthBar, HEALTH_BAR_HEIGHT constant)
- Separate out-of-combat HUD renderer

---

## [2.1.2] - 2026-02-08

### Fixed
- Turn count tracking now uses mustChoose transitions instead of pendingActionRequests size comparison (fixes Timer Ball/Quick Ball showing wrong multiplier after turn 1)
- Out-of-combat guaranteed catch detection now checks formula result, not just ball flag
- NeoForge GUI layer namespace now uses underscore mod ID for consistency
- Ball multiplier color tiers in comparison panel now match main HUD (distinguishes 1.5x/2.0x/3.0x)
- Lure Ball no longer uses unrelated Pokédex data as a proxy for aspect availability
- resetState() now fully resets turn tracking fields

---

## [2.1.1] - 2026-02-08

### Fixed
- /catchrate commands now use proper Fabric client command API (fixes potential crashes)
- Command messages are now fully localized (translation keys in all 28 languages)

---

## [2.1.0] - 2026-02-08

### Enhanced Debug Logging

Makes troubleshooting easier for players and developers.

### Added
- `/catchrate debug` command — toggles verbose logging for current session
- `/catchrate info` command — shows mod version, config, and environment info
- Calculation trace logging showing Pokemon, ball, HP, status, and result
- Ball multiplier logging for every ball type with condition status
- HUD decision logging (why HUD is/isn't showing)
- State-change logging (target changed, ball changed, etc.)
- Environment dump on debug enable (Minecraft version, Cobblemon version, loader)
- Session-based debug override (toggle without editing config file)

### Changed
- Debug system now uses `isDebugActive` which respects both config and session toggle
- Startup logging now includes mod version

---

## [2.0.0] - 2026-02-07

### Pure Client-Side Rewrite

**CatchRate Display is now 100% client-side.** No server installation needed — works on any Cobblemon server.

This major release completely removes the server component (~1400 lines of code deleted). All catch rate calculations now use Cobblemon's client-synced data, providing the same accuracy as before while eliminating server dependency.

### Added
- **Level Ball** client-side calculation using active battler level
- **Repeat Ball** client-side calculation using synced Pokédex data
- **Lure Ball** client-side calculation using Pokemon entity aspects

### Changed
- Mod environment changed from universal to client-only
- All ball multipliers now calculate entirely on client
- Simplified HUD rendering (single unified path)
- Updated mod descriptions to reflect pure client-side nature

### Removed
- Entire server networking layer (3 files, ~560 lines)
- `CatchRateServerNetworking.kt`, `CatchRateClientNetworking.kt`, `CatchRatePackets.kt`
- Server detection and dual-mode logic
- Platform networking methods (`sendToServer()`, `sendToPlayer()`)
- "Requires server mod" warning messages from all 28 language files

### Fixed
- Level Ball now uses correct active battler level (was using party max level)
- Repeat Ball works in multiplayer without server mod
- Lure Ball works for fishing encounters without server mod
- Lure Ball now works during battle (uses battle state instead of entity lookup)
- Reduced debug log spam with per-category throttling

---

## [1.3.4] - 2026-02-07

### Fixed
- **NeoForge crash on startup: "Expected @SubscribeEvent method to NOT be static"**
  - Removed `@JvmStatic` from all `@SubscribeEvent` methods in NeoForge client event handlers
  - KotlinForForge registers Kotlin `object` singletons as instances, which requires non-static methods
  - `@JvmStatic` caused NeoForge's event bus to reject the registration

## [1.3.3] - 2026-02-07

### Fixed
- **NeoForge jar now loads correctly!**
  - NeoForge mod ID changed from `catchrate-display` to `catchrate_display` (hyphens not allowed in NeoForge mod IDs)
  - Added `NEOFORGE_MOD_ID` constant for platform-specific mod ID handling
  - NeoForge requires mod IDs matching `^[a-z][a-z0-9_]{1,63}$` (only lowercase letters, numbers, underscores)
  - Fabric version keeps hyphenated ID for compatibility
  - Fixed "File catchrate-display-neoforge-*.jar is not a valid mod file" error

## [1.3.2] - 2026-02-07

### Fixed
- JAR file naming now includes proper prefix (catchrate-display-fabric/neoforge instead of just fabric/neoforge)

## [1.3.1] - 2026-02-07

### Performance
- Eliminated duplicate catch rate calculations in both single-ball and comparison displays
- Added PokeBall registry lookup caching to reduce repeated ResourceLocation allocations
- Optimized base speed stat lookup using direct enum key access instead of iteration
- Removed unused world response cache allocation
- **No gameplay or feature changes** - pure performance improvements

## [1.3.0] - 2026-02-07

### Major: Architectury Multiloader Support
- **CatchRate Display now supports both Fabric AND NeoForge!**
- Migrated from Fabric-only to Architectury multiloader build system
- Common codebase shared between both platforms (~90% code reuse)
- Platform-specific entrypoints for Fabric (`ModInitializer`/`ClientModInitializer`) and NeoForge (`@Mod`/`@EventBusSubscriber`)
- Platform abstraction layer (`PlatformHelper` with `@ExpectPlatform`) for config paths, networking, and mod loader detection
- Separate JARs produced for each loader (Fabric and NeoForge)
- Remapped entire codebase from Yarn to Mojang mappings (50+ API name changes) for Architectury compatibility
  - `PlayerEntity` → `Player`, `MinecraftClient` → `Minecraft`, `DrawContext` → `GuiGraphics`
  - `Text` → `Component`, `Formatting` → `ChatFormatting`, `Identifier` → `ResourceLocation`
  - `KeyBinding` → `KeyMapping`, `ItemStack` methods, `World` → `Level`, and many more
- Network system rewritten using `CustomPacketPayload` with `StreamCodec` (Mojmap 1.21+ API)
  - Fabric: `PayloadTypeRegistry` registration
  - NeoForge: `RegisterPayloadHandlersEvent` with versioned registrar
- HUD rendering abstracted per platform
  - Fabric: `HudRenderCallback.EVENT`
  - NeoForge: `RegisterGuiLayersEvent.registerAboveAll`

### Localization Overhaul
- **28 language files** now included with full translations:
  - English (US, GB, Pirate), Czech, German, Greek, Esperanto, Spanish (Spain, Mexico),
    French (France, Canada), Hungarian, Italian, Japanese, Korean, Dutch, Polish,
    Portuguese (Brazil, Portugal), Russian, Swedish, Thai, Turkish, Ukrainian, Vietnamese,
    Chinese (Simplified, Traditional, Hong Kong)
- All HUD text, config descriptions, ball names, status effects, and condition descriptions
  are now translatable
- Fixed locale-dependent number formatting (decimal separators now consistent)
- Dynamic keybind display names now localized

### Fixed
- **Out-of-combat catch rate now accurate in multiplayer** (Fixes [#1](https://github.com/Akkiruk/catchrate-display/issues/1))
  - On non-host multiplayer clients, every Pokemon showed the same catch rate (e.g. 7.1% with Poke Ball)
  - Root cause: The out-of-combat code path calculated catch rates entirely client-side, but Cobblemon
    does not fully sync species data (`catchRate`, `maxHealth`, etc.) to non-host clients
  - Fix: Added server-side networking for out-of-combat catch rate calculation, mirroring the approach
    already used for in-battle calculations
  - New `WorldCatchRateRequestPayload` packet (sends entity network ID + ball type to server)
  - Server handler looks up `PokemonEntity` by ID and calculates with full species data
  - Client uses server response when available, falls back to client-side calculation when
    server mod is not installed
  - Reuses existing `CatchRateResponsePayload` for the response
- Ball comparison panel now properly updates when turn count changes (Timer Ball, Quick Ball)
- Ball comparison cache cleared on battle start, turn increment, and HP/status/target changes
- Removed dead code and unused imports

### Technical
- Build system: 3-subproject Gradle structure (`common/`, `fabric/`, `neoforge/`) with Architectury Loom 1.9.424
- Architect Plugin 3.4.162 for cross-platform compilation
- Mojang mappings + Parchment for parameter name readability
- `@ExpectPlatform` pattern for platform-specific implementations (`PlatformHelper`)
  - Fabric: `FabricLoader` API, `ClientPlayNetworking`
  - NeoForge: `FMLPaths`, `PacketDistributor`
- Source files organized: 14 common, 3 Fabric-specific, 3 NeoForge-specific
- Minimum Cobblemon 1.6+, Minecraft 1.21

## [1.2.32] - 2026-02-06

### Fixed
- **Critical formula accuracy fixes** - now matches Cobblemon's exact shake calculation
- Changed shake probability divisor from 65536 to 65537 (Cobblemon uses `Random.nextInt(65537)`)
- Added `.roundToInt()` to shake probability calculation to match Cobblemon's integer comparison
- Formula-guaranteed catches (shakeProbability >= 65537) now correctly display 100% instead of 99.9%
  - Example: Quick Ball turn 1 on high-catch-rate Pokemon like Sentret is mathematically guaranteed
- Both client-side and server-side calculations now detect formula guarantees
- Ball comparison panel properly identifies guaranteed catches

### Technical Details
- When `modifiedCatchRate > 255`, `shakeProbability` exceeds 65536, making every `Random.nextInt(65537)` check pass
- This is Cobblemon's natural way of handling high catch rates, not just Master Ball
- Updated `formatCatchPercentage()` to show 100% when percentage >= 100.0
- Added `isGuaranteedByFormula()` helper to detect mathematical guarantees
- Server logging now notes that we cannot replicate `PokemonCatchRateEvent` modifications

### Known Limitations
- Our calculations use raw catch rates and cannot detect if other mods modify them via `PokemonCatchRateEvent`
- If you see consistent failures on displayed "guaranteed" catches, another mod may be reducing catch rates
- CobbleCuisine's 2x catch rate buff (via food) cannot be detected by our display

## [1.2.31] - 2026-02-02

### Fixed
- **Replicated Cobblemon's exact capture formula** for perfect accuracy
- Fixed shake probability divisor: Cobblemon uses 65537, not 65536 (Random.nextInt(65537) generates 0-65536)
- Removed level penalty application (Cobblemon's findHighestThrowerLevel has a bug where it always returns null)
- HP component now exactly matches Cobblemon's structure with all multipliers in correct order
- Added comprehensive debug logging to compare our calculation with Cobblemon's actual values

### Technical Details
- Server-side calculation now mirrors CobblemonCaptureCalculator line-by-line
- Shake probability: `(shakeProbability / 65537)^4` instead of `(shakeProbability / 65536)^4`
- This small difference could cause up to ~0.006% discrepancy at high catch rates

## [1.2.30] - 2026-02-01

### Fixed
- **Root cause fix**: Removed phantom "level penalty" that we were applying but Cobblemon doesn't
- Cobblemon's `findHighestThrowerLevel()` has a bug causing it to always return null for wild battles
- This means level penalty is never actually applied by Cobblemon, but we were applying it
- Our catch rates now match Cobblemon's actual mechanics exactly
- Reverts the bandaid 99.9% cap from v1.2.29 since the root cause is fixed

## [1.2.29] - 2026-02-01

### Fixed
- Non-guaranteed catches now always display max 99.9% to prevent misleading "100%" displays
- Previously, very high catch rates (99.95%+) could round to show 100% but still fail
- Only Master Ball and truly guaranteed catches now show 100%

## [1.2.28] - 2026-02-01

### Changed
- Out-of-combat HUD now matches in-combat HUD layout
- HP shown as multiplier text instead of health bar
- Status effects now displayed for wild Pokemon
- Ball condition description row added
- Dynamic box width based on content
- Guaranteed catches show "★ 100% CATCH ★" consistently

## [1.2.27] - 2026-02-01

### Added
- Automated Modrinth publishing alongside CurseForge

## [1.2.26] - 2026-02-01

### Improved
- Debounced config saving prevents disk I/O spam when adjusting HUD position
- Replaced System.currentTimeMillis() with tick counter for more efficient throttling
- Cached HUD text calculations (string formatting, text widths) when data unchanged
- Cached wild Pokemon entity lookup to reduce expensive queries every frame

## [1.2.25] - 2026-02-01

### Fixed
- Ancient balls (Jet, Gigaton, Wing, Leaden, etc.) now correctly show 1x catch rate (Cobblemon's throwPower only affects travel distance, not catch rate)

## [1.2.24] - 2026-02-01

### Fixed
- Catch percentage display now caps at 99.9% for non-guaranteed catches (prevents misleading "100.0%" from rounding)

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

## [1.2.10] - 2026-01-31and 

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

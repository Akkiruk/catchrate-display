# CatchRate Display Mod - Development Guide

## Project Overview

A client-side Fabric mod for Minecraft 1.21+ that displays real-time catch rates during Cobblemon battles. Shows accurate probability calculations based on server-side data including HP, status effects, and ball modifiers.

### Key Technical Details

- **Minecraft**: 1.21.x
- **Fabric Loader**: >=0.15.0
- **Cobblemon**: >=1.6.0
- **Kotlin**: 2.2.0
- **Mod ID**: `catchrate-display`
- **Package**: `com.catchrate`

### Architecture

```
CatchRateDisplayMod - Main mod initializer (client only)
├── CatchRateCalculator - Core catch rate math (Gen 8/9 formulas)
├── network/
│   ├── CatchRateClientNetworking - Client packet receiver
│   └── CatchRateServerNetworking - Server-side data provider
├── client/
│   ├── CatchRateHudRenderer - Main HUD display
│   └── BallComparisonCalculator - Multi-ball comparison panel
└── config/
    └── CatchRateConfig - JSON-based settings (HUD position, colors, etc)
```

### Network Protocol

Server sends catch data to client:
- Pokemon HP/max HP
- Status condition
- Catch rate
- Type labels (legendary, ultra_beast, etc)

Client calculates probabilities locally using this data.

---

## CRITICAL: Build and Deploy Workflow

**AFTER EVERY CODE CHANGE, YOU MUST:**

1. **Build the mod**:
   ```bash
   cd "c:\Users\rboon\curseforge\minecraft\Instances\COBBLEVERSE - Pokemon Adventure [Cobblemon]\CatchRateDisplay"
   ./gradlew build
   ```

2. **Deploy to mod folder** (use cmd for better path handling):
   ```bash
   cmd /c copy "c:\Users\rboon\curseforge\minecraft\Instances\COBBLEVERSE - Pokemon Adventure [Cobblemon]\CatchRateDisplay\build\libs\catchrate-display-1.2.5.jar" "c:\Users\rboon\curseforge\minecraft\Instances\COBBLEVERSE - Pokemon Adventure [Cobblemon]\mods\catchrate-display-1.2.5.jar"
   ```

3. **Clean up old versions** (if any):
   ```bash
   Get-ChildItem "c:\Users\rboon\curseforge\minecraft\Instances\COBBLEVERSE - Pokemon Adventure [Cobblemon]\mods\catchrate-display-*.jar" | Where-Object { $_.Name -ne "catchrate-display-1.2.5.jar" } | Remove-Item -Force
   ```

**This is NOT optional** - changes won't appear in-game until the mod is rebuilt and copied to the mods folder.

---

## MANDATORY RELEASE WORKFLOW - FULLY AUTOMATED

**CRITICAL: When making code changes, execute ALL steps sequentially WITHOUT waiting for user confirmation. The entire workflow must complete from start to finish in one action.**

### Workflow Execution Rules
1. **DO NOT STOP** between steps - execute all steps in sequence
2. **DO NOT ASK** for confirmation to proceed unless a step fails
3. **DO NOT WAIT** for user testing - NEVER ask user to test changes before publishing
4. **ALWAYS PROCEED** directly to GitHub and CurseForge after successful build
5. **VERIFY programmatically** where possible (build success, file existence)
6. **ONLY STOP** if an error occurs that cannot be automatically resolved

### User Preference: NO TESTING BEFORE RELEASE
**The user has explicitly stated: "I NEVER want to test changes before you send a build"**
- After successful build, proceed IMMEDIATELY to git commit/push and release
- Do NOT suggest testing or ask if user wants to test
- Do NOT pause for user verification
- Trust the build system and automated workflows

### Complete Automated Workflow

#### Step 1: Make Code Changes
Edit the necessary files for your feature/fix.

#### Step 2: Update Version Number
**File:** `gradle.properties`
```properties
mod_version=X.Y.Z
```
**Versioning:**
- **Major (X)**: Breaking changes or major features
- **Minor (Y)**: New features, significant improvements
- **Patch (Z)**: Bug fixes, minor tweaks

#### Step 3: Update CHANGELOG.md
**MANDATORY:** Add entry at the top of CHANGELOG.md:
```markdown
## [X.Y.Z] - YYYY-MM-DD

### Added
- New features go here

### Changed
- Modified functionality goes here

### Fixed
- Bug fixes go here

### Removed
- Removed features go here
```

#### Step 4: Build the Mod
**Execute immediately - no confirmation needed:**
```bash
cd "c:\Users\rboon\curseforge\minecraft\Instances\COBBLEVERSE - Pokemon Adventure [Cobblemon]\CatchRateDisplay"
./gradlew clean build
```
Verify: `build/libs/catchrate-display-X.Y.Z.jar` exists
**If build fails:** STOP and report error

#### Step 5: Deploy to Mods Folder
**Execute immediately after successful build:**
```powershell
Copy-Item -LiteralPath "c:\Users\rboon\curseforge\minecraft\Instances\COBBLEVERSE - Pokemon Adventure [Cobblemon]\CatchRateDisplay\build\libs\catchrate-display-X.Y.Z.jar" -Destination "c:\Users\rboon\curseforge\minecraft\Instances\COBBLEVERSE - Pokemon Adventure [Cobblemon]\mods\catchrate-display-X.Y.Z.jar" -Force
```

**Clean up old versions:**
```powershell
Remove-Item "c:\Users\rboon\curseforge\minecraft\Instances\COBBLEVERSE - Pokemon Adventure [Cobblemon]\mods\catchrate-display-*.jar" -Exclude "catchrate-display-X.Y.Z.jar" -Force -ErrorAction SilentlyContinue
```

#### Step 6: Commit and Tag Changes
**Execute immediately - do not wait:**
```bash
cd "c:\Users\rboon\curseforge\minecraft\Instances\COBBLEVERSE - Pokemon Adventure [Cobblemon]\CatchRateDisplay"
git add .
git commit -m "[X.Y.Z] Description of changes"
git tag -a vX.Y.Z -m "Release version X.Y.Z"
```

#### Step 7: Push to GitHub
**Execute immediately after commit:**
```bash
git push origin main --tags
```
**This triggers GitHub Actions to publish to CurseForge automatically**

#### Step 8: Verify Automation (Programmatic Only)
Check git push exit code:
- Exit code 0 = SUCCESS, workflow complete
- Non-zero = STOP and report error

**DO NOT:**
- Open browser to check GitHub Actions manually
- Wait for user to verify CurseForge upload
- Ask if user wants to proceed

**The workflow is COMPLETE when git push succeeds.**

### Error Handling
**Only stop and ask for help if:**
1. Build fails (compilation errors)
2. Git operations fail (conflicts, authentication issues)
3. File system operations fail (permissions, disk space)

**For all other cases:** Execute the complete workflow without interruption.

### Quick Execution Summary
```
Code changes → Version bump → CHANGELOG update → Build → Deploy → Commit → Tag → Push → GitHub Release
```
**All steps execute in sequence without pausing for testing or user approval.**

**DO NOT:**
- Ask user to test before releasing
- Pause for user verification between steps
- Wait for confirmation to proceed to next step
- Suggest testing changes in-game before publishing

**The complete workflow from code change to CurseForge publication must happen automatically.**

---

## Critical: No AI Detection

**NEVER** include patterns that reveal AI generation:

❌ Verbose inline comments ("First we...", "Now let's...")
❌ Step-by-step explanations in code
❌ Excessive documentation blocks
❌ Phrases like "Note that", "Keep in mind", "It's important to"
❌ TODO lists without specific context
❌ References to AI, automated generation, or "refactoring"
❌ Changelog files or version history docs

✅ Write code like an experienced human developer
✅ Comments only for non-obvious behavior
✅ Natural commit messages
✅ Minimal documentation

---

## Common Pitfalls

### Cobblemon API Issues

**Client vs Server Data**:
- Client has `ClientBattlePokemon` - limited data
- Server has full `Pokemon` object with all properties
- Always use network packets for accurate HP/status
- Don't trust client-side crosshair targeting for battle data

**Battle Detection**:
```kotlin
// WRONG - unreliable
val pokemon = client.crosshairTarget

// RIGHT - use battle state
BattleRegistry.getBattleProvider(player)
```

**Ball Detection**:
```kotlin
// Exclude non-catchable items
itemId.contains("_ball") && 
  !itemId.contains("snowball") && 
  !itemId.contains("slimeball")
```

### Rendering Issues

**Coordinate Systems**:
- Config stores position as enum (TOP_LEFT, etc) + offsets
- Must calculate actual screen position in renderer
- Handle screen resizing properly

**Z-index**:
- Use appropriate render layer for HUD overlay
- Ensure it renders above game but below chat/menus

**Color Formatting**:
- Colors stored as hex strings in config
- Convert using `Color.decode()` for rendering
- Handle invalid color gracefully (fallback to white)

### Catch Rate Calculations

**Ball Modifiers**:
- Master Ball: 255x (guaranteed)
- Ultra/Great: Fixed multipliers
- Specialty balls: Conditional (time, biome, type)
- Custom Cobblemon balls: Ancient Origin, etc

**Status Conditions**:
- Sleep/Freeze: 2.5x
- Paralyze/Poison/Burn: 1.5x
- None: 1.0x

**HP Calculation**:
- Uses standard formula: `(3 * maxHP - 2 * currentHP) * catchRate * ballBonus / (3 * maxHP)`
- Shake probability: `65536 / sqrt(sqrt(255 / p))`
- 4 successful shakes required

**Edge Cases**:
- Heavy Ball: Can have negative modifier against flying types
- Level Ball: Needs player's team data (server-side only)
- Love Ball: Gender comparison (limited client-side access)

### Configuration

**File Location**: `config/catchrate-display.json`

**Safe Defaults**:
```kotlin
hudPosition = HudPosition.TOP_LEFT
hudOffsetX = 10
hudOffsetY = 10
goodCatchRateColor = "#00FF00"
// etc
```

**Loading**:
- Read on mod init
- Handle missing file (create defaults)
- Handle corrupt JSON (log warning, use defaults)
- Don't crash on config errors

---

## Code Style

### Comments

Only comment when code behavior is non-obvious:

```kotlin
// YES
val rate = baseRate * 1.5F  // Gen 9 catch rate buff

// NO
val rate = calculateRate(pokemon)  // Calculate the catch rate
```

### Kotlin Idioms

Prefer concise expressions:

```kotlin
// YES
val ball = when {
    name.contains("ultra") -> 2.0F
    name.contains("great") -> 1.5F
    else -> 1.0F
}

// NO
var ball: Float
if (name.contains("ultra")) {
    ball = 2.0F
} else if (name.contains("great")) {
    ball = 1.5F
} else {
    ball = 1.0F
}
```

### Error Handling

Only catch what you can handle:

```kotlin
// YES - specific recovery
try {
    config = Json.decodeFromString(file.readText())
} catch (e: Exception) {
    LOGGER.warn("Failed to load config: ${e.message}")
    config = CatchRateConfig()  // Use defaults
}

// NO - swallow everything
try {
    // ... lots of code
} catch (e: Exception) {
    // Hope for the best
}
```

---

## GitHub Repository Maintenance

### Version Management & Automated Publishing

**We use automated CurseForge publishing via GitHub Actions.**

**When releasing a new version:**

1. **Update version** in `gradle.properties`:
   ```properties
   mod_version=1.2.3
   ```

2. **Update CHANGELOG.md** with changes (human-readable, concise):
   ```markdown
   ## [1.2.3] - 2026-01-31
   
   ### Fixed
   - Catch rate for Heavy Ball against flying types
   
   ### Added
   - Support for Ancient Origin Ball
   
   ### Improved
   - HUD rendering performance
   ```

3. **Commit the version change**:
   ```bash
   git add gradle.properties CHANGELOG.md src/
   git commit -m "Release v1.2.3"
   git push
   ```

4. **Build the mod**:
   ```bash
   ./gradlew build
   ```

5. **Create GitHub Release with CLI**:
   ```bash
   gh release create v1.2.3 --title "v1.2.3" --notes "Brief description of changes" ".\build\libs\catchrate-display-1.2.3.jar"
   ```

6. **Automatic Publishing**:
   - GitHub Actions workflow automatically triggers
   - Builds the mod
   - Extracts changelog for this version
   - Uploads to CurseForge with correct metadata
   - Sets dependencies: Fabric API, Fabric Language Kotlin, Cobblemon
   - Marks for Minecraft 1.21 and 1.21.1
   
**See `.github/CURSEFORGE_PUBLISHING.md` for full automation details.**

### CHANGELOG Format

Keep entries **brief and factual** using semantic versioning categories:

```markdown
# Changelog

## [1.2.3] - 2026-01-31

### Fixed
- Heavy Ball modifier calculation
- Crash when switching battles rapidly

### Added
- Ancient Origin Ball support
- Config option to disable ball comparison

### Changed
- HUD rendering optimization for better performance

## [1.2.2] - 2026-01-30

Initial public release

- Real-time catch rate display
- Ball comparison panel
- Server-side accuracy
```

**Categories to use:**
- `### Added` - New features
- `### Fixed` - Bug fixes
- `### Changed` - Changes to existing functionality
- `### Removed` - Removed features

**Note:** The automated publishing workflow extracts sections by version heading (e.g., `## [1.2.3]`), so maintain this format consistently.

**Avoid:**
- ❌ "Completely refactored the entire codebase"
- ❌ "Enhanced user experience with improved..."
- ❌ "Implemented comprehensive error handling"
- ❌ Long explanations or justifications

**Use:**
- ✓ "Heavy Ball modifier calculation"
- ✓ "Ancient Origin Ball support"
- ✓ "HUD rendering optimization"

### Release Notes

For GitHub releases, write short descriptions:

```markdown
Fixes catch rate calculation edge cases and adds support for additional Cobblemon balls.

**Changes:**
- Heavy Ball now correctly handles flying type penalties
- Ancient Origin Ball recognized as guaranteed catch
- Optimized rendering to reduce frame drops
```

### Issue Management

**When creating issue templates** (`.github/ISSUE_TEMPLATE/`):

**bug_report.md** - Keep it simple:
```markdown
---
name: Bug Report
about: Report a bug
---

**Describe the bug**
What's broken?

**To Reproduce**
Steps to reproduce

**Expected behavior**
What should happen?

**Screenshots**
If applicable

**Environment:**
- Minecraft version:
- Cobblemon version:
- Mod version:
```

**feature_request.md**:
```markdown
---
name: Feature Request
about: Suggest a feature
---

**Feature description**
What do you want added?

**Use case**
Why is this useful?
```

### Pull Request Template

**`.github/PULL_REQUEST_TEMPLATE.md`:**
```markdown
**What does this change?**


**Why?**


**Testing done:**
- [ ] Builds successfully
- [ ] Tested in-game
- [ ] No crashes
```

### File Structure (Updated)

**Include:**
- `src/` - Source code
- `build.gradle.kts` - Build config
- `gradle.properties` - Version/properties
- `README.md` - User documentation
- `CHANGELOG.md` - Version history (required for automated publishing)
- `LICENSE` - MIT license
- `.gitignore` - Standard ignores
- `.github/ISSUE_TEMPLATE/` - Issue templates
- `.github/PULL_REQUEST_TEMPLATE.md` - PR template
- `.github/workflows/publish.yml` - CurseForge publishing automation
- `.github/CURSEFORGE_PUBLISHING.md` - Publishing setup guide

**Exclude (gitignored):**
- `.github/copilot-instructions.md` - This file (AI hints)
- `.gradle/` - Build cache
- `build/` - Build output
- `run/` - Test environment
- IDE files

**Never create:**
- `CONTRIBUTING.md` (overkill for small mod)
- `docs/` folder (use README)
- Manual build/publish scripts (we use GitHub Actions)
- AI development notes
- Additional setup guides

### Commit Workflow

**Normal development:**
```bash
git add <files>
git commit -m "Fix X bug"
git push
```

**For releases (automated publishing):**
```bash
# 1. Update version in gradle.properties
# 2. Update CHANGELOG.md with new version section

git add gradle.properties CHANGELOG.md
git commit -m "Release v1.2.3"
git push

# 3. Create GitHub release (triggers automation)
# Go to https://github.com/Akkiruk/catchrate-display/releases/new
# - Tag: v1.2.3
# - Title: v1.2.3
# - Publish

# GitHub Actions automatically:
# - Builds the mod
# - Publishes to CurseForge
# - Uploads JAR to GitHub release
```

**Manual test of automation (optional):**
```bash
# Go to Actions tab → Publish to CurseForge → Run workflow
# Enter version number manually to test upload
```

### GitHub Release Process

**Full workflow for new version (with automation):**

1. Make sure all changes are committed and tested
2. Update `gradle.properties` with new version number
3. Add entry to `CHANGELOG.md` (use `## [X.Y.Z]` format)
4. Commit: `git commit -m "Release vX.Y.Z"`
5. Push: `git push`
6. Create GitHub Release:
   - Go to repository → Releases → "Create a new release"
   - Click "Choose a tag" → type `vX.Y.Z` → "Create new tag"
   - Title: `vX.Y.Z`
   - Description: Optional notes (changelog is auto-extracted)
   - Click "Publish release"
7. Automation runs:
   - Builds mod JAR
   - Extracts changelog section for this version
   - Uploads to CurseForge with dependencies
   - Attaches JAR to GitHub release
8. Verify upload on CurseForge project page
9. Announce if needed (Discord, forums, etc)

**Local testing before release:**
```bash
./gradlew build
cmd /c copy ".\build\libs\catchrate-display-X.Y.Z.jar" "c:\Users\rboon\curseforge\minecraft\Instances\COBBLEVERSE - Pokemon Adventure [Cobblemon]\mods\catchrate-display-X.Y.Z.jar"
# Test in-game thoroughly
```

### Repository Settings

**Branch protection** (optional but recommended):
- Require builds to pass before merging PRs
- Don't force it for your own commits (you're solo dev)

**Labels** to create:
- `bug` - Something's broken
- `enhancement` - New feature
- `help wanted` - Community can contribute
- `wontfix` - Not planned
- `duplicate` - Already reported

Keep it simple - don't over-organize.

---

## Testing Checklist

When making changes, verify:

- [ ] Builds successfully: `./gradlew build`
- [ ] No client crashes on load
- [ ] HUD appears during battles
- [ ] HUD hidden when not in battle
- [ ] Server data packets received correctly
- [ ] Ball comparison panel toggles properly
- [ ] Config saves/loads without errors
- [ ] Works with vanilla Poké Balls
- [ ] Works with custom mod balls
- [ ] Edge cases: Master Ball, fainted Pokemon, etc

---

## File Structure

Keep the repo minimal:

**Include:**
- `src/` - Source code only
- `build.gradle.kts` - Build config
- `gradle.properties` - Version/properties
- `README.md` - User documentation
- `CHANGELOG.md` - Version history (required for automation)
- `LICENSE` - MIT license
- `.gitignore` - Standard ignores
- `.github/workflows/publish.yml` - Automated publishing
- `.github/CURSEFORGE_PUBLISHING.md` - Setup documentation

**Exclude (gitignored):**
- `.github/copilot-instructions.md` - This file (AI development guide)
- `.gradle/` - Build cache
- `build/` - Build output
- `run/` - Test environment
- IDE files

**Never create:**
- Manual build/publish scripts (automation handles this)
- `CONTRIBUTING.md` (overkill for solo project)
- `docs/` folder (use README)
- Development notes files

---

## Commit Messages

Natural, concise, human-written:

✓ `Fix master ball guarantee check`
✓ `Add heavy ball negative modifier`
✓ `Support custom Cobblemon balls`
✓ `Release v1.2.3`

✗ `Refactored the codebase to improve maintainability`
✗ `Added comprehensive error handling and validation`
✗ `Updated documentation for enhanced clarity`
✗ `feat: implement new feature X`
✗ `fix(core): resolve issue with Y`

**No conventional commits format** - just write naturally.

---

## Quick Reference

### Build Commands
```bash
./gradlew build              # Build mod JAR
./gradlew runClient         # Test in game
./gradlew clean build       # Clean build
```

### Key Classes
- `BattleRegistry` - Get current battle state
- `BattleProvider` - Access battle participants
- `ClientBattlePokemon` - Client-side Pokemon data
- `PokeBallItem` - Ball item detection
- `DrawContext` - Rendering helper

### Network Packets
- Packet ID: `catchrate:battle_data`
- Direction: Server → Client
- Trigger: Every tick during battle when holding ball

### Config Keys
- Position: `hudPosition` (enum)
- Offsets: `hudOffsetX`, `hudOffsetY` (int)
- Colors: RGB hex strings
- Ball comparison: `comparisonBallLimit` (int)

---

## When in Doubt

1. Check Cobblemon source code
2. Test in actual gameplay
3. Keep code minimal and clear
4. Remove rather than add
5. Write like a human developer

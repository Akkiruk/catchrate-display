# Development Workflow

## **MANDATORY WORKFLOW - NO EXCEPTIONS**

Every code change MUST follow this complete workflow from start to finish. **DO NOT SKIP ANY STEP.**

---

## Step 1: Make Code Changes

Edit the necessary files for your feature/fix.

---

## Step 2: Update Version Number

**File:** `gradle.properties`

```properties
mod_version=X.Y.Z
```

**Versioning:**
- **Major (X)**: Breaking changes or major features
- **Minor (Y)**: New features, significant improvements
- **Patch (Z)**: Bug fixes, minor tweaks

---

## Step 3: Update CHANGELOG.md

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

**Categories:**
- **Added**: New features
- **Changed**: Changes to existing functionality
- **Fixed**: Bug fixes
- **Removed**: Removed features
- **Security**: Security improvements
- **Deprecated**: Soon-to-be removed features

---

## Step 4: Build Locally

**MANDATORY:** Build must succeed before proceeding.

```powershell
.\gradlew build
```

Verify output:
```
build/libs/catchrate-display-X.Y.Z.jar
```

---

## Step 5: Test In-Game

**MANDATORY:** Test the build before publishing.

1. Copy `build/libs/catchrate-display-X.Y.Z.jar` to Minecraft `mods/` folder
2. Launch Minecraft
3. Test all changed functionality
4. Test in battle (if UI/calculation changes)
5. Test out-of-combat (if applicable)
6. Verify no crashes or errors

---

## Step 6: Commit Changes

**MANDATORY:** Commit with descriptive message.

```bash
git add .
git commit -m "[X.Y.Z] Description of changes

- Detail 1
- Detail 2
- Detail 3"
```

**Commit Message Format:**
```
[VERSION] Short summary (50 chars max)

- Detailed point 1
- Detailed point 2
- Detailed point 3

Closes #issue_number (if applicable)
```

---

## Step 7: Create Git Tag

**MANDATORY:** Tag triggers the publish workflow.

```bash
git tag -a v{X.Y.Z} -m "Release version X.Y.Z"
```

---

## Step 8: Push to GitHub

**MANDATORY:** Push both commits AND tags.

```bash
git push origin main
git push origin v{X.Y.Z}
```

**Alternative (push everything):**
```bash
git push origin main --tags
```

---

## Step 9: Verify GitHub Actions Workflow

**MANDATORY:** Ensure automated publish succeeds.

1. Go to: https://github.com/Akkiruk/catchrate-display/actions
2. Verify "Publish to CurseForge" workflow started
3. Wait for workflow to complete (green checkmark)
4. If failed (red X), check logs and fix issues

**Workflow does:**
- ✅ Checks out code
- ✅ Sets up JDK 21
- ✅ Builds with Gradle
- ✅ Extracts changelog for this version
- ✅ Uploads to CurseForge with metadata
- ✅ Creates build artifacts

---

## Step 10: Verify CurseForge Upload

**MANDATORY:** Confirm mod is live on CurseForge.

1. Go to CurseForge project page
2. Verify new version appears in Files tab
3. Check changelog displays correctly
4. Verify file downloads successfully
5. Check dependencies are correct:
   - Fabric API (required)
   - Fabric Language Kotlin (required)
   - Cobblemon (required)

---

## Step 11: Create GitHub Release (Optional but Recommended)

1. Go to: https://github.com/Akkiruk/catchrate-display/releases/new
2. Select tag: `v{X.Y.Z}`
3. Title: `v{X.Y.Z}`
4. Description: Copy from CHANGELOG.md
5. Attach: `build/libs/catchrate-display-X.Y.Z.jar`
6. Click "Publish release"

---

## Emergency Workflow (Manual Publish)

If GitHub Actions fails, manually trigger workflow:

### Option 1: Workflow Dispatch
1. Go to: https://github.com/Akkiruk/catchrate-display/actions
2. Select "Publish to CurseForge"
3. Click "Run workflow"
4. Enter version number
5. Click "Run workflow" button

### Option 2: Manual Upload to CurseForge
1. Go to CurseForge project dashboard
2. Click "Upload File"
3. Upload `build/libs/catchrate-display-X.Y.Z.jar`
4. Fill in metadata:
   - **Display Name:** v{X.Y.Z}
   - **Version:** X.Y.Z
   - **Release Type:** Release
   - **Game Versions:** 1.21, 1.21.1
   - **Mod Loaders:** Fabric
   - **Java Version:** 21
   - **Dependencies:**
     - Fabric API (Required)
     - Fabric Language Kotlin (Required)
     - Cobblemon (Required)
5. Paste changelog from CHANGELOG.md
6. Submit

---

## Secrets Required for GitHub Actions

Ensure these are set in repository settings:

- `CURSEFORGE_PROJECT_ID`: Your CurseForge project ID
- `CURSEFORGE_TOKEN`: Your CurseForge API token

**To set secrets:**
1. Go to repository Settings
2. Secrets and variables → Actions
3. New repository secret
4. Add name and value
5. Save

---

## Quick Reference Checklist

**Before ANY release, complete ALL items:**

- [ ] Code changes complete
- [ ] Version bumped in `gradle.properties`
- [ ] CHANGELOG.md updated with version entry
- [ ] Local build successful (`./gradlew build`)
- [ ] Tested in-game (no crashes, features work)
- [ ] Changes committed with descriptive message
- [ ] Git tag created (`git tag -a v{X.Y.Z}`)
- [ ] Pushed to GitHub (`git push origin main --tags`)
- [ ] GitHub Actions workflow succeeded
- [ ] CurseForge file verified and live
- [ ] GitHub Release created (optional)

**If ANY step fails, STOP and fix before proceeding.**

---

## Common Issues

### Build Fails
- Check Kotlin syntax errors
- Verify all imports are correct
- Run `./gradlew clean build`

### Workflow Doesn't Trigger
- Ensure tag starts with `v` (e.g., `v1.2.5`)
- Verify tag was pushed: `git push origin --tags`
- Check GitHub Actions is enabled

### CurseForge Upload Fails
- Verify secrets are set correctly
- Check CHANGELOG.md has entry for this version
- Ensure version format is correct (no `v` prefix in gradle.properties)

### File Not Appearing on CurseForge
- Check workflow logs for errors
- Verify CURSEFORGE_TOKEN has upload permissions
- Try manual upload as fallback

---

## Version History Reference

| Version | Date | Type | Description |
|---------|------|------|-------------|
| 1.2.5 | 2026-01-31 | Minor | UI pokemonification |
| 1.2.4 | 2026-01-31 | Patch | Ancient ball multiplier fixes |
| 1.2.3 | 2026-01-31 | Patch | Ball comparison panel fix |
| 1.2.2 | 2026-01-30 | Minor | Initial public release |

---

## Contact

Issues: https://github.com/Akkiruk/catchrate-display/issues

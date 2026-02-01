# Automated Publishing Setup (CurseForge + Modrinth)

This repository automatically publishes new versions to both CurseForge and Modrinth when you create a GitHub release.

## Initial Setup (One-Time)

### 1. CurseForge Setup

#### Get API Token
1. Go to https://www.curseforge.com/settings/api-tokens
2. Click **Generate Token**
3. Name it "GitHub Actions - CatchRateDisplay"
4. Copy the token

#### Get Project ID
1. Go to your CurseForge project page
2. Find the project ID in the URL or settings

### 2. Modrinth Setup

#### Create Project
1. Go to https://modrinth.com/dashboard/projects
2. Click **Create a project**
3. Fill in:
   - **Name**: CatchRate Display
   - **Summary**: Real-time catch rate calculator for Cobblemon battles
   - **Categories**: Utility, Cobblemon
   - **Client/Server**: Client-side
   - **License**: MIT

#### Get API Token
1. Go to https://modrinth.com/settings/account
2. Scroll to **API tokens**
3. Click **Create a token**
4. Name it "GitHub Actions"
5. Copy the token immediately

#### Get Project ID
Your project ID is the slug in the URL: `https://modrinth.com/mod/YOUR-PROJECT-ID`

### 3. Add GitHub Secrets

1. Go to https://github.com/Akkiruk/catchrate-display/settings/secrets/actions
2. Click **New repository secret**
3. Add four secrets:
   - **CURSEFORGE_TOKEN**: Your CurseForge API token
   - **CURSEFORGE_PROJECT_ID**: Your CurseForge project ID
   - **MODRINTH_TOKEN**: Your Modrinth API token
   - **MODRINTH_PROJECT_ID**: Your Modrinth project slug

## Publishing a New Version

### Step-by-Step

1. **Update version** in `gradle.properties`:
   ```properties
   mod_version=1.2.4
   ```

2. **Update** `CHANGELOG.md`:
   ```markdown
   ## [1.2.4] - 2026-02-01
   
   ### Fixed
   - Bug description
   
   ### Added
   - New feature
   ```

3. **Commit and push**:
   ```bash
   git add gradle.properties CHANGELOG.md
   git commit -m "Release v1.2.4"
   git push
   ```

4. **Create GitHub Release**:
   - Go to https://github.com/Akkiruk/catchrate-display/releases/new
   - **Tag**: `v1.2.4` (create new tag)
   - **Title**: `v1.2.4`
   - **Description**: Optional (changelog auto-extracted)
   - Click **Publish release**

5. **Automation runs**:
   - Builds the mod
   - Extracts changelog for this version
   - Uploads to CurseForge
   - Uploads to Modrinth
   - Attaches JAR to GitHub release

### Quick Version (After Setup)

```bash
# Update version in gradle.properties
# Update CHANGELOG.md
git add gradle.properties CHANGELOG.md
git commit -m "Release v1.2.4"
git push
gh release create v1.2.4 --title "v1.2.4" --generate-notes
```

## Troubleshooting

### Build Fails
- Check GitHub Actions logs
- Verify `./gradlew build` works locally

### Upload Fails
- Verify secrets are set correctly
- Check API tokens haven't expired
- Ensure project IDs are correct

### Changelog Not Showing
- Ensure `CHANGELOG.md` has `## [X.Y.Z]` format
- Version must match exactly

## Manual Testing

Test the workflow without creating a release:

1. Go to **Actions** → **Publish to CurseForge and Modrinth**
2. Click **Run workflow**
3. Enter version number
4. Click **Run workflow**

This builds and publishes without creating a GitHub release.

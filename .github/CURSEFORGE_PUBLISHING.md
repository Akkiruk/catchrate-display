# Automated CurseForge Publishing Setup

This repository is configured to automatically publish new versions to CurseForge when you create a GitHub release.

## Initial Setup (One-Time)

### 1. Get Your CurseForge API Token

1. Go to https://www.curseforge.com/settings/api-tokens
2. Click "Generate Token"
3. Give it a name like "GitHub Actions - CatchRateDisplay"
4. Copy the token (you won't be able to see it again!)

### 2. Get Your CurseForge Project ID

1. Go to your project page on CurseForge (after creating it)
2. The project ID is in the URL: `https://www.curseforge.com/minecraft/mc-mods/YOUR-PROJECT-NAME`
3. Or go to your project settings, it's shown there as "Project ID"

### 3. Add Secrets to GitHub Repository

1. Go to your GitHub repository: https://github.com/Akkiruk/catchrate-display
2. Click **Settings** → **Secrets and variables** → **Actions**
3. Click **New repository secret**
4. Add two secrets:
   - **Name**: `CURSEFORGE_TOKEN`  
     **Value**: Your API token from step 1
   - **Name**: `CURSEFORGE_PROJECT_ID`  
     **Value**: Your project ID from step 2

## How to Publish a New Version

### Automatic Publishing (Recommended)

1. **Update version in `gradle.properties`**:
   ```properties
   mod_version=1.2.3
   ```

2. **Update `CHANGELOG.md`**:
   ```markdown
   ## [1.2.3] - 2026-02-01
   
   ### Added
   - New feature description
   
   ### Fixed
   - Bug fix description
   ```

3. **Commit and push changes**:
   ```bash
   git add gradle.properties CHANGELOG.md
   git commit -m "Release v1.2.3"
   git push
   ```

4. **Create a GitHub Release**:
   - Go to https://github.com/Akkiruk/catchrate-display/releases
   - Click "Create a new release"
   - Click "Choose a tag" and type `v1.2.3` (create new tag)
   - Title: `v1.2.3`
   - Description: Copy from changelog or write release notes
   - Click "Publish release"

5. **That's it!** The workflow will automatically:
   - Build your mod
   - Extract the changelog for this version
   - Upload to CurseForge with all the right metadata
   - Mark dependencies correctly

### Manual Publishing

If you need to publish without creating a release:

1. Go to your repository → **Actions** tab
2. Click "Publish to CurseForge" workflow
3. Click "Run workflow"
4. Enter the version number
5. Click "Run workflow"

## What Gets Uploaded

The workflow uploads:
- Main mod JAR file
- Sources JAR file
- Automatically sets:
  - Game versions: 1.21, 1.21.1
  - Mod loader: Fabric
  - Dependencies: Fabric API, Fabric Language Kotlin, Cobblemon (all required)
  - Java version: 21
  - Changelog from CHANGELOG.md

## Customizing

Edit `.github/workflows/publish.yml` to change:
- Game versions (lines 59-61)
- Dependencies (lines 63-66)
- Version type (line 51: `release`, `beta`, or `alpha`)
- Any other metadata

## Troubleshooting

### Workflow fails with "Invalid token"
- Check that `CURSEFORGE_TOKEN` secret is set correctly
- Generate a new API token if needed

### Workflow fails with "Project not found"
- Check that `CURSEFORGE_PROJECT_ID` is correct
- Make sure you've created the project on CurseForge first

### Changelog is empty
- Make sure CHANGELOG.md has a section like `## [1.2.3]` matching your version
- The workflow extracts everything between version headers

### Build fails
- Make sure the mod builds locally: `./gradlew build`
- Check Java version compatibility
- Review the Actions log for specific errors

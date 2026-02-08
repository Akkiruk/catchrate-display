# Contributing to CatchRate Display

Thanks for your interest in contributing! This document outlines how to contribute effectively.

## Getting Started

1. Fork the repository
2. Clone your fork locally
3. Set up your development environment (JDK 21, IntelliJ IDEA recommended)
4. Create a feature branch: `git checkout -b feature/your-feature-name`

## Development Setup

```bash
# Clone your fork
git clone https://github.com/YOUR_USERNAME/catchrate-display.git
cd catchrate-display

# Build the project
./gradlew build

# Run Fabric client for testing
./gradlew :fabric:runClient

# Run NeoForge client for testing
./gradlew :neoforge:runClient
```

## Code Style

- **Kotlin**: Follow standard Kotlin conventions
- **Indentation**: 4 spaces, no tabs
- **Comments**: Only for non-obvious behavior
- **Naming**: Descriptive names, no abbreviations

## Pull Request Process

1. Ensure your code builds: `./gradlew clean build`
2. Test in-game with both Fabric and NeoForge
3. Update documentation if needed
4. Create a PR with a clear description of changes
5. Fill out the PR template completely

## Commit Messages

Format: `[SCOPE] Description`

Examples:
- `[hud] Fixed color gradient at edge cases`
- `[calc] Heavy Ball modifier for flying types`
- `[config] Added obfuscation toggle`

## Issue Reporting

- Check existing issues before creating new ones
- Use provided issue templates
- Include mod version, Minecraft version, and mod loader
- Provide debug logs when reporting calculation bugs

## Code of Conduct

Please read and follow our [Code of Conduct](CODE_OF_CONDUCT.md).

## Questions?

Open a [Discussion](https://github.com/Akkiruk/catchrate-display/discussions) for general questions.

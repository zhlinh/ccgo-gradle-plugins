# CCGO Gradle Plugins

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Maven Central](https://img.shields.io/maven-central/v/com.mojeter.ccgo.gradle/convention)](https://central.sonatype.com/artifact/com.mojeter.ccgo.gradle/convention)

Convention plugins for CCGO cross-platform C++ projects.

## Overview

This project provides a set of Gradle convention plugins that standardize build configurations across CCGO projects. These plugins are published to Maven repositories and can be consumed by any CCGO project.

## Available Plugins

### Android Plugins

- `com.mojeter.ccgo.gradle.android.application` - Android application configuration
- `com.mojeter.ccgo.gradle.android.application.compose` - Android application with Compose UI
- `com.mojeter.ccgo.gradle.android.application.flavors` - Product flavors configuration
- `com.mojeter.ccgo.gradle.android.application.jacoco` - Code coverage with Jacoco
- `com.mojeter.ccgo.gradle.android.library` - Android library configuration
- `com.mojeter.ccgo.gradle.android.library.compose` - Android library with Compose
- `com.mojeter.ccgo.gradle.android.library.jacoco` - Library code coverage
- `com.mojeter.ccgo.gradle.android.feature` - Feature module configuration
- `com.mojeter.ccgo.gradle.android.hilt` - Hilt dependency injection
- `com.mojeter.ccgo.gradle.android.room` - Room database
- `com.mojeter.ccgo.gradle.android.lint` - Lint configuration
- `com.mojeter.ccgo.gradle.android.root` - Root project configuration

### Native Build Plugins

- `com.mojeter.ccgo.gradle.android.library.native.empty` - Empty native library (no C++ code)
- `com.mojeter.ccgo.gradle.android.library.native.python` - Python-based native build
- `com.mojeter.ccgo.gradle.android.library.native.cmake` - CMake-based native build

### Publishing Plugin

- `com.mojeter.ccgo.gradle.android.publish` - Maven publishing configuration

### JVM Plugin

- `com.mojeter.ccgo.gradle.jvm.library` - Pure JVM/Kotlin library

## Publishing

### Publish to Local Maven Repository

For local testing, publish to `~/.m2/repository/`:

```bash
./gradlew publishToMavenLocal
```

### Publish to Maven Central

For public releases, this project uses [vanniktech/gradle-maven-publish-plugin](https://vanniktech.github.io/gradle-maven-publish-plugin/) to publish to Maven Central Portal:

1. Configure credentials in `~/.gradle/gradle.properties`:

```properties
# Maven Central credentials (get User Token from https://central.sonatype.com/account)
mavenCentralUsername=your-user-token-username
mavenCentralPassword=your-user-token-password

# Optional: Use in-memory PGP key for signing
signingInMemoryKey=-----BEGIN PGP PRIVATE KEY BLOCK-----\n...\n-----END PGP PRIVATE KEY BLOCK-----
signingInMemoryKeyPassword=your-key-password
```

**Note**: Get User Token from https://central.sonatype.com/account (not your login credentials)

2. Publish:

```bash
./gradlew publishAllPublicationsToMavenCentralRepository
```

The plugin automatically handles signing, upload, and release to Maven Central.

**For detailed instructions, see [PUBLISHING.md](PUBLISHING.md)**

### Publish to Custom Maven Repository

For private repositories:

1. Configure repository credentials in `gradle.properties`:

```properties
USE_MAVEN_LOCAL=false
MAVEN_REPO_URL=https://your-maven-repo.com/releases
MAVEN_REPO_USERNAME=your-username
MAVEN_REPO_PASSWORD=your-password
```

2. Publish:

```bash
./gradlew publish
```

## Usage in CCGO Projects

### 1. Configure Plugin Repository

In your project's `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        // For published releases from Maven Central
        mavenCentral()

        // For local development/testing
        mavenLocal()

        // For custom Maven repository
        // maven {
        //     url = uri("https://your-maven-repo.com/releases")
        // }

        google()
        gradlePluginPortal()
    }
}
```

### 2. Apply Plugins

In your project's `gradle/libs.versions.toml`:

```toml
[versions]
ccgo-buildlogic = "1.0.0"

[plugins]
ccgo-android-library = { id = "com.mojeter.ccgo.gradle.android.library", version.ref = "ccgo-buildlogic" }
ccgo-android-library-native-cmake = { id = "com.mojeter.ccgo.gradle.android.library.native.cmake", version.ref = "ccgo-buildlogic" }
ccgo-android-publish = { id = "com.mojeter.ccgo.gradle.android.publish", version.ref = "ccgo-buildlogic" }
# ... other plugins
```

In your root `build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.ccgo.android.library) apply false
    alias(libs.plugins.ccgo.android.library.native.cmake) apply false
    alias(libs.plugins.ccgo.android.publish) apply false
}
```

In your module's `build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.ccgo.android.library)
    alias(libs.plugins.ccgo.android.library.native.cmake)
}
```

## Versioning

This project follows semantic versioning:

- **1.0.x** - Patch releases (bug fixes)
- **1.x.0** - Minor releases (new features, backward compatible)
- **x.0.0** - Major releases (breaking changes)

Update `VERSION_NAME` in `gradle.properties` to release a new version.

## Migration

**Important**: Starting from version 0.0.1, the Maven group ID has been changed from `com.ccgo.gradle` to `com.mojeter.ccgo.gradle`.

See [MIGRATION.md](MIGRATION.md) for detailed migration instructions.

## Development

### Local Testing

1. Make changes to plugins
2. Publish to mavenLocal:
   ```bash
   ./gradlew publishToMavenLocal
   ```
3. Test in a CCGO project using `mavenLocal()` repository

### Project Structure

```
ccgo-gradle-plugins/
├── convention/                     # Convention plugins module
│   ├── src/main/kotlin/           # Plugin source code
│   │   ├── Android*.kt           # Android-specific plugins
│   │   ├── Jvm*.kt               # JVM-specific plugins
│   │   └── com/ccgo/gradle/buildlogic/common/  # Shared utilities
│   └── build.gradle.kts          # Module configuration
├── gradle/
│   └── libs.versions.toml        # Dependency versions
├── gradle.properties             # Project configuration
├── settings.gradle.kts           # Project settings
└── README.md                     # This file
```

## License

MIT License - see LICENSE file for details

## Contributing

This project is part of the CCGO ecosystem. For contribution guidelines, see the main CCGO repository.

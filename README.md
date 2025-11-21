# CCGO Gradle Plugins

Convention plugins for CCGO cross-platform C++ projects.

## Overview

This project provides a set of Gradle convention plugins that standardize build configurations across CCGO projects. These plugins are published to Maven repositories and can be consumed by any CCGO project.

## Available Plugins

### Android Plugins

- `ccgo.android.application` - Android application configuration
- `ccgo.android.application.compose` - Android application with Compose UI
- `ccgo.android.application.flavors` - Product flavors configuration
- `ccgo.android.application.jacoco` - Code coverage with Jacoco
- `ccgo.android.library` - Android library configuration
- `ccgo.android.library.compose` - Android library with Compose
- `ccgo.android.library.jacoco` - Library code coverage
- `ccgo.android.feature` - Feature module configuration
- `ccgo.android.hilt` - Hilt dependency injection
- `ccgo.android.room` - Room database
- `ccgo.android.lint` - Lint configuration
- `ccgo.android.root` - Root project configuration

### Native Build Plugins

- `ccgo.android.library.native.empty` - Empty native library (no C++ code)
- `ccgo.android.library.native.python` - Python-based native build
- `ccgo.android.library.native.cmake` - CMake-based native build

### Publishing Plugin

- `ccgo.android.publish` - Maven publishing configuration

### JVM Plugin

- `ccgo.jvm.library` - Pure JVM/Kotlin library

## Publishing

### Publish to Local Maven Repository

For local testing, publish to `~/.m2/repository/`:

```bash
./gradlew publishToMavenLocal
```

### Publish to Maven Central

For public releases, publish to Maven Central (Sonatype OSSRH):

1. Configure credentials in `~/.gradle/gradle.properties`:

```properties
PUBLISH_TO_MAVEN_CENTRAL=true
OSSRH_USERNAME=your-sonatype-username
OSSRH_PASSWORD=your-sonatype-password

# Optional: Use in-memory PGP key for signing
SIGNING_KEY=-----BEGIN PGP PRIVATE KEY BLOCK-----\n...\n-----END PGP PRIVATE KEY BLOCK-----
SIGNING_PASSWORD=your-key-password
```

2. Publish:

```bash
./gradlew publishAllPublicationsToOSSRHRepository
```

3. Log in to [Sonatype Nexus](https://s01.oss.sonatype.org/) to close and release the staging repository

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
ccgo-android-library = { id = "ccgo.android.library", version.ref = "ccgo-buildlogic" }
ccgo-android-library-native-cmake = { id = "ccgo.android.library.native.cmake", version.ref = "ccgo-buildlogic" }
ccgo-android-publish = { id = "ccgo.android.publish", version.ref = "ccgo-buildlogic" }
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

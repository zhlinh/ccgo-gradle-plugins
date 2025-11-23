# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.x.x] - 2025-11-24

### Added

- Maven Central (Sonatype OSSRH) publishing support
- Signing plugin configuration for artifact signing
- Comprehensive POM metadata (name, description, url, licenses, developers, scm)
- `PUBLISHING.md` guide for publishing to Maven Central
- Environment variable support for credentials (mavenCentralUsername, mavenCentralPassword)
- In-memory PGP key signing support for CI/CD environments (signingInMemoryKey, signingInMemoryKeyPassword)
- Gradle plugin markers for all 18 convention plugins

### Changed

- Updated `convention/build.gradle.kts` with Maven Central publishing configuration
- Enhanced `gradle.properties` with detailed Maven Central configuration comments
- Updated `README.md` with Maven Central publishing instructions

### Security

- Credentials can be stored in `~/.gradle/gradle.properties` instead of project files
- Support for GPG agent for local development
- In-memory PGP keys supported for CI/CD security

## Publishing Tasks

The following Gradle tasks are available:

- `publishToMavenLocal` - Publish to local Maven repository (~/.m2)
- `publishAllPublicationsToMavenCentralRepository` - Publish to Maven Central staging
- `publish` - Publish to all configured repositories

## Available Plugins

All plugins are published under the `com.mojeter.ccgo.gradle` group:

1. `ccgo.android.application`
2. `ccgo.android.application.compose`
3. `ccgo.android.application.flavors`
4. `ccgo.android.application.jacoco`
5. `ccgo.android.library`
6. `ccgo.android.library.compose`
7. `ccgo.android.library.jacoco`
8. `ccgo.android.feature`
9. `ccgo.android.hilt`
10. `ccgo.android.room`
11. `ccgo.android.lint`
12. `ccgo.android.root`
13. `ccgo.android.library.native.empty`
14. `ccgo.android.library.native.python`
15. `ccgo.android.library.native.cmake`
16. `ccgo.android.publish`
17. `ccgo.jvm.library`

## Maven Central Coordinates

After publishing to Maven Central, artifacts will be available at:

```
https://repo1.maven.org/maven2/com/mojeter/ccgo/gradle/convention/
```

Plugin markers are published at:

```
https://repo1.maven.org/maven2/com/mojeter/ccgo/android/library/native/python/
```

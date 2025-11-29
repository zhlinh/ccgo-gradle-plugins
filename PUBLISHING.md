# Publishing CCGO Gradle Plugins

This guide explains how to publish the CCGO Gradle Plugins to Maven Central, local Maven repository, or custom Maven repositories.

## Prerequisites

### 1. Sonatype OSSRH Account (for Maven Central)

Register for a Sonatype account and request access to the `com.mojeter.ccgo.gradle` group ID:

1. Create account at: https://central.sonatype.com/
2. Generate User Token from Account settings
3. Request namespace verification for `com.mojeter.ccgo.gradle`

Reference: https://central.sonatype.org/publish/publish-guide/

### 2. GPG Key for Signing (Optional for Local/Custom, Required for Maven Central)

#### Generate a GPG Key (if you don't have one)

```bash
# Generate key
gpg --gen-key

# List keys to get KEY_ID
gpg --list-keys

# Export public key to keyserver (REQUIRED for Maven Central)
gpg --keyserver keys.openpgp.org --send-keys KEY_ID

# Alternative keyservers:
# gpg --keyserver keyserver.ubuntu.com --send-keys KEY_ID
```

#### Export Private Key for Publishing

For reliable Maven Central publishing, export your GPG key for use with Gradle:

```bash
# Export private key
gpg --export-secret-keys --armor KEY_ID > /tmp/signing-key.asc

# View the key content (you'll copy this to gradle.properties)
cat /tmp/signing-key.asc

# IMPORTANT: Delete the file after copying to gradle.properties
rm /tmp/signing-key.asc
```

Or use the provided helper script:

```bash
./export-gpg-key.sh
```

## Configuration

Configuration is read from multiple sources with the following priority (highest to lowest):

1. **Environment variables** (for CI/CD override)
2. **CCGO.toml** (for projects using CCGO template)
3. **Project-level gradle.properties** (in project root)
4. **User-level ~/.gradle/gradle.properties** (personal defaults)

### Configuration Options

| Purpose | Environment Variable | gradle.properties Key |
|---------|---------------------|----------------------|
| Maven Central Username | `MAVEN_CENTRAL_USERNAME` | `mavenCentralUsername` |
| Maven Central Password | `MAVEN_CENTRAL_PASSWORD` | `mavenCentralPassword` |
| Signing Key | `SIGNING_IN_MEMORY_KEY` | `signingInMemoryKey` |
| Signing Key Password | `SIGNING_IN_MEMORY_KEY_PASSWORD` | `signingInMemoryKeyPassword` |
| Local Maven Path | `MAVEN_LOCAL_PATH` | `mavenLocalPath` |
| Custom Maven URLs | `MAVEN_CUSTOM_URLS` | `mavenCustomUrls` |
| Custom Maven Usernames | `MAVEN_CUSTOM_USERNAMES` | `mavenCustomUsernames` |
| Custom Maven Passwords | `MAVEN_CUSTOM_PASSWORDS` | `mavenCustomPasswords` |

### Option 1: Local Development (~/.gradle/gradle.properties)

```properties
# Maven Central Portal credentials (get User Token from https://central.sonatype.com/account)
mavenCentralUsername=your-user-token-username
mavenCentralPassword=your-user-token-password

# Signing with in-memory key (RECOMMENDED for reliable publishing)
# Export your GPG key using: gpg --export-secret-keys --armor KEY_ID > key.asc
# Then paste the entire key content, replacing newlines with \n
signingInMemoryKey=-----BEGIN PGP PRIVATE KEY BLOCK-----\n\nYOUR_KEY_CONTENT_HERE\n-----END PGP PRIVATE KEY BLOCK-----
signingInMemoryKeyPassword=your-gpg-key-passphrase

# Local Maven repository path (optional)
mavenLocalPath=~/.m2/repository

# Custom Maven repositories (optional, comma-separated for multiple)
mavenCustomUrls=https://maven.company.com/releases,https://maven.private.com/repo
mavenCustomUsernames=user1,user2
mavenCustomPasswords=pass1,pass2
```

### Option 2: CI/CD Environment Variables

```bash
# Maven Central Portal credentials
export MAVEN_CENTRAL_USERNAME=your-user-token-username
export MAVEN_CENTRAL_PASSWORD=your-user-token-password

# Signing credentials
export SIGNING_IN_MEMORY_KEY="-----BEGIN PGP PRIVATE KEY BLOCK-----
...key content...
-----END PGP PRIVATE KEY BLOCK-----"
export SIGNING_IN_MEMORY_KEY_PASSWORD=your-key-password

# Local Maven repository (optional)
export MAVEN_LOCAL_PATH=~/.m2/repository

# Custom Maven repositories (optional, comma-separated)
export MAVEN_CUSTOM_URLS=https://maven.company.com/releases
export MAVEN_CUSTOM_USERNAMES=user1
export MAVEN_CUSTOM_PASSWORDS=pass1
```

## Publish Commands

The following unified commands are available:

| Command | Description |
|---------|-------------|
| `./gradlew publishToMavenCentral` | Publish to Maven Central (requires signing) |
| `./gradlew publishToMavenLocal` | Publish to local Maven repository |
| `./gradlew publishToMavenCustom` | Publish to all custom Maven repositories |

### Signing Behavior

| Condition | Signing |
|-----------|---------|
| `signingInMemoryKey` + `signingInMemoryKeyPassword` configured | Uses in-memory PGP key |
| GPG installed with secret keys | Uses GPG command-line |
| Neither configured | Skips signing (still works for Local/Custom) |

**Note**: Maven Central requires signed artifacts. Publishing without signing will fail for Maven Central but works for local and custom repositories.

## Publishing Steps

### 1. Update Version

Edit `gradle.properties`:

```properties
VERSION_NAME=1.0.0  # Remove -SNAPSHOT for release
```

### 2. Publish to Local Maven (Testing)

```bash
# Set local path via environment variable
export MAVEN_LOCAL_PATH=~/.m2/repository

# Publish to local
./gradlew publishToMavenLocal

# Verify in ~/.m2/repository/com/mojeter/ccgo/gradle/
ls ~/.m2/repository/com/mojeter/ccgo/gradle/convention/
```

### 3. Publish to Custom Maven Repository

```bash
# Set custom repository via environment variables
export MAVEN_CUSTOM_URLS=https://maven.company.com/releases
export MAVEN_CUSTOM_USERNAMES=your-username
export MAVEN_CUSTOM_PASSWORDS=your-password

# Publish to custom repos
./gradlew publishToMavenCustom
```

### 4. Publish to Maven Central

```bash
# Ensure credentials are configured in ~/.gradle/gradle.properties
# Then publish
./gradlew publishToMavenCentral
```

The plugin will:
1. Sign all publications (if signing credentials are configured)
2. Upload to Maven Central Portal
3. Automatically trigger release (if `automaticRelease = true`)
4. Sync to Maven Central within ~30 minutes

**Monitor deployment:**
- Log in to https://central.sonatype.com/
- Go to "Deployments" tab to check status

## Version Strategy

- **Release versions**: `1.0.0`, `1.0.1`, `2.0.0`
- **Snapshot versions**: `1.0.0-SNAPSHOT`, `1.1.0-SNAPSHOT`

Snapshots are published to: https://s01.oss.sonatype.org/content/repositories/snapshots/

Releases are published to: https://repo1.maven.org/maven2/ (Maven Central)

## Troubleshooting

### Signing Failed

**Problem**: `Signing key not found` or `gpg: signing failed`

**Solutions**:
1. **For in-memory key**: Ensure `signingInMemoryKey` contains the full PGP key with `\n` replacing newlines
2. **For GPG agent**: Check GPG has secret keys: `gpg --list-secret-keys`
3. **For Local/Custom repos**: Signing is optional, artifacts will publish without signatures

### Authentication Failed

**Problem**: `401 Unauthorized` when publishing

**Solutions**:
- Verify credentials are correct
- For Maven Central: Check User Token at https://central.sonatype.com/account
- For custom repos: Verify username/password match the repository configuration

### POM Validation Failed

**Problem**: Staging repository close fails with POM errors

**Solution**:
- Ensure all required POM fields are set (check `convention/build.gradle.kts`)
- Required: name, description, url, licenses, developers, scm
- All fields should be non-empty

### Artifacts Not Syncing to Maven Central

**Problem**: Released from staging but not on Maven Central

**Solution**:
- Wait at least 2 hours for sync
- Check https://repo1.maven.org/maven2/com/mojeter/ccgo/gradle/
- If still missing after 24 hours, contact Sonatype support

### Task Not Found

**Problem**: `Task 'publishToMavenLocal' not found`

**Solution**:
- Ensure `MAVEN_LOCAL_PATH` or `mavenLocalPath` is configured
- Repositories are only registered when their configuration is present

## Usage After Publishing

Once published to Maven Central, users can use the plugins:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

// build.gradle.kts
plugins {
    id("com.mojeter.ccgo.gradle.android.library.native.python") version "1.0.0"
}
```

For local or custom repository usage:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        maven { url = uri("/path/to/local/repo") }  // or custom URL
        mavenCentral()
        gradlePluginPortal()
    }
}
```

## References

- [Sonatype Central Portal](https://central.sonatype.com/)
- [Gradle Maven Publish Plugin](https://vanniktech.github.io/gradle-maven-publish-plugin/)
- [Maven Central Requirements](https://central.sonatype.org/publish/requirements/)

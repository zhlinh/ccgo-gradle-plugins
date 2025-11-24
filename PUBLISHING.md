# Publishing CCGO Gradle Plugins

This guide explains how to publish the CCGO Gradle Plugins to Maven Central (Sonatype OSSRH).

## Prerequisites

### 1. Sonatype OSSRH Account

Register for a Sonatype JIRA account and request access to the `com.mojeter.ccgo.gradle` group ID:

1. Create account at: https://issues.sonatype.org/
2. Create a "New Project" ticket requesting access to `com.mojeter.ccgo.gradle`
3. Wait for approval (usually 1-2 business days)

Reference: https://central.sonatype.org/publish/publish-guide/

### 2. GPG Key for Signing

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

### Option 1: Local Development (Recommended)

Add credentials to `~/.gradle/gradle.properties`:

```properties
# Maven Central Portal credentials (get User Token from https://central.sonatype.com/account)
mavenCentralUsername=your-user-token-username
mavenCentralPassword=your-user-token-password

# Signing with in-memory key (RECOMMENDED for reliable publishing)
# Export your GPG key using: gpg --export-secret-keys --armor KEY_ID > key.asc
# Then paste the entire key content, replacing newlines with \n
signingInMemoryKey=-----BEGIN PGP PRIVATE KEY BLOCK-----\n\nYOUR_KEY_CONTENT_HERE\n-----END PGP PRIVATE KEY BLOCK-----
signingInMemoryKeyPassword=your-gpg-key-passphrase
```

**Why in-memory keys are recommended:**
- Works reliably in non-interactive environments (Gradle builds)
- No GPG agent or TTY issues
- Same key can be used in CI/CD pipelines
- Gradle handles the signing automatically

**Alternative: Use GPG Agent (less reliable)**
- If you don't want to export your key, you can rely on local GPG agent
- May fail with "Inappropriate ioctl for device" errors in some environments
- Requires proper GPG and pinentry configuration

### Option 2: CI/CD Environment

Set environment variables or Gradle properties:

```bash
# Maven Central Portal credentials
export ORG_GRADLE_PROJECT_mavenCentralUsername=your-user-token-username
export ORG_GRADLE_PROJECT_mavenCentralPassword=your-user-token-password

# Signing credentials (optional)
export ORG_GRADLE_PROJECT_signingInMemoryKey=$(cat private-key.asc)
export ORG_GRADLE_PROJECT_signingInMemoryKeyPassword=your-key-password
```

## Publishing Steps

### 1. Update Version

Edit `gradle.properties`:

```properties
VERSION_NAME=1.0.0  # Remove -SNAPSHOT for release
```

### 2. Publish to Maven Local (Test)

```bash
# Test locally first (no signing required)
./gradlew publishToMavenLocal

# Verify in ~/.m2/repository/com/mojeter/ccgo/gradle/
```

**Note**: Local publishing does not require signing. Signing is only enabled when `PUBLISH_TO_MAVEN_CENTRAL=true` is set.

### 3. Publish to Maven Central Portal

This project uses [vanniktech/gradle-maven-publish-plugin](https://vanniktech.github.io/gradle-maven-publish-plugin/) for simplified Maven Central publishing.

```bash
# Publish to Maven Central Portal
./gradlew publishAllPublicationsToMavenCentralRepository
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

## Gradle Tasks

| Task | Description |
|------|-------------|
| `publishToMavenLocal` | Publish to local Maven repository (~/.m2) |
| `publishAllPublicationsToMavenCentralRepository` | Publish to Maven Central staging |
| `publish` | Publish to all configured repositories |

## Troubleshooting

### Signing Failed

**Problem**: `Signing key not found` or `gpg: signing failed`

**Solution**:
- Make sure GPG is installed: `gpg --version`
- Check keys exist: `gpg --list-keys`
- For CI/CD, ensure `SIGNING_KEY` and `SIGNING_PASSWORD` are set

### Authentication Failed

**Problem**: `401 Unauthorized` when publishing

**Solution**:
- Verify Sonatype credentials are correct
- Check if your account has access to `com.mojeter.ccgo.gradle` group ID
- Wait for Sonatype approval if you just requested access

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

## References

- [Sonatype OSSRH Guide](https://central.sonatype.org/publish/publish-guide/)
- [Gradle Plugin Portal Publishing](https://plugins.gradle.org/docs/publish-plugin)
- [Maven Central Requirements](https://central.sonatype.org/publish/requirements/)

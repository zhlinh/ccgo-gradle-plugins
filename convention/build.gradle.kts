//
// Copyright 2024 zhlinh and ccgo Project Authors. All rights reserved.
// Use of this source code is governed by a MIT-style
// license that can be found at
//
// https://opensource.org/license/MIT
//
// The above copyright notice and this permission
// notice shall be included in all copies or
// substantial portions of the Software.

import org.gradle.plugins.signing.Sign
import org.gradle.plugins.signing.SigningExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
    `maven-publish`
    signing
    id("com.vanniktech.maven.publish") version "0.30.0"
}

group = "com.mojeter.ccgo.gradle"
version = rootProject.version

// Configure the build-logic plugins to target JDK 17
// This matches the JDK used to build the project, and is not related to what is running on device.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.android.tools.common)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.room.gradlePlugin)
    compileOnly(libs.maven.publishPlugin)  // Needed for Publish.kt compilation
    implementation(libs.truth)
}

tasks {
    validatePlugins {
        enableStricterValidation.set(true)
        failOnWarning.set(true)
    }
}

gradlePlugin {
    plugins {
        register("androidApplicationCompose") {
            id = "com.mojeter.ccgo.gradle.android.application.compose"
            implementationClass = "AndroidApplicationComposeConventionPlugin"
        }
        register("androidApplication") {
            id = "com.mojeter.ccgo.gradle.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidApplicationJacoco") {
            id = "com.mojeter.ccgo.gradle.android.application.jacoco"
            implementationClass = "AndroidApplicationJacocoConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = "com.mojeter.ccgo.gradle.android.library.compose"
            implementationClass = "AndroidLibraryComposeConventionPlugin"
        }
        register("androidLibrary") {
            id = "com.mojeter.ccgo.gradle.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidFeature") {
            id = "com.mojeter.ccgo.gradle.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("androidLibraryJacoco") {
            id = "com.mojeter.ccgo.gradle.android.library.jacoco"
            implementationClass = "AndroidLibraryJacocoConventionPlugin"
        }
        register("androidHilt") {
            id = "com.mojeter.ccgo.gradle.android.hilt"
            implementationClass = "AndroidHiltConventionPlugin"
        }
        register("androidRoom") {
            id = "com.mojeter.ccgo.gradle.android.room"
            implementationClass = "AndroidRoomConventionPlugin"
        }
        register("androidFlavors") {
            id = "com.mojeter.ccgo.gradle.android.application.flavors"
            implementationClass = "AndroidApplicationFlavorsConventionPlugin"
        }
        register("androidLint") {
            id = "com.mojeter.ccgo.gradle.android.lint"
            implementationClass = "AndroidLintConventionPlugin"
        }
        register("jvmLibrary") {
            id = "com.mojeter.ccgo.gradle.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
        register("androidRoot") {
            id = "com.mojeter.ccgo.gradle.android.root"
            implementationClass = "AndroidRootConventionPlugin"
        }
        register("androidLibraryNativeEmpty") {
            id = "com.mojeter.ccgo.gradle.android.library.native.empty"
            implementationClass = "AndroidLibraryNativeEmptyConventionPlugin"
        }
        register("androidLibraryNativePython") {
            id = "com.mojeter.ccgo.gradle.android.library.native.python"
            implementationClass = "AndroidLibraryNativePythonConventionPlugin"
        }
        register("androidLibraryNativeCmake") {
            id = "com.mojeter.ccgo.gradle.android.library.native.cmake"
            implementationClass = "AndroidLibraryNativeCmakeConventionPlugin"
        }
        register("androidPublish") {
            id = "com.mojeter.ccgo.gradle.android.publish"
            implementationClass = "AndroidPublishConventionPlugin"
        }
    }
}

// Configure signing credentials first
// vanniktech plugin prefers signingInMemoryKey and signingInMemoryKeyPassword
val signingKey = project.findProperty("signingInMemoryKey")?.toString()
    ?: project.findProperty("SIGNING_KEY")?.toString()
    ?: System.getenv("SIGNING_KEY")
val signingPassword = project.findProperty("signingInMemoryKeyPassword")?.toString()
    ?: project.findProperty("SIGNING_PASSWORD")?.toString()
    ?: System.getenv("SIGNING_PASSWORD")
val hasSigningKeys = !signingKey.isNullOrBlank() && !signingPassword.isNullOrBlank()

// Check if gpg-agent is available (for local development)
val hasGpgAgent = try {
    val result = Runtime.getRuntime().exec(arrayOf("gpg", "--list-keys"))
    result.waitFor() == 0
} catch (e: Exception) {
    false
}

// Set default empty values for Maven Central credentials to prevent build service errors
// These will be overridden by actual values in ~/.gradle/gradle.properties when publishing
if (!project.hasProperty("mavenCentralUsername")) {
    project.ext.set("mavenCentralUsername", "")
}
if (!project.hasProperty("mavenCentralPassword")) {
    project.ext.set("mavenCentralPassword", "")
}

// Configure vanniktech maven-publish plugin
// This plugin handles Maven Central Portal publishing automatically
mavenPublishing {
    // Coordinates are set from project group and version
    coordinates(group.toString(), "convention", version.toString())

    // POM configuration
    pom {
        name.set("CCGO Gradle Build Logic Convention Plugins")
        description.set("Convention plugins for CCGO cross-platform C++ projects")
        url.set("https://github.com/zhlinh/ccgo-gradle-plugins")

        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/license/MIT")
            }
        }

        developers {
            developer {
                id.set("zhlinh")
                name.set("zhlinh")
                email.set("zhlinhng@gmail.com")
            }
        }

        scm {
            connection.set("scm:git:git://github.com/zhlinh/ccgo-gradle-plugins.git")
            developerConnection.set("scm:git:ssh://github.com/zhlinh/ccgo-gradle-plugins.git")
            url.set("https://github.com/zhlinh/ccgo-gradle-plugins")
        }
    }

    // Configure publishing to Maven Central Portal
    // Note: Actual credentials should be in ~/.gradle/gradle.properties as:
    // mavenCentralUsername=your-user-token-username
    // mavenCentralPassword=your-user-token-password
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)

    // Enable signing - will be configured below
    if (hasSigningKeys || hasGpgAgent) {
        signAllPublications()
    }
}

// Set GPG properties for GnuPG command-line signing (before configuring signing plugin)
if (hasGpgAgent && !hasSigningKeys) {
    // Configure GPG executable path for signing plugin
    project.ext.set("signing.gnupg.executable", "gpg")
    project.ext.set("signing.gnupg.useLegacyGpg", false)
}

// Configure signing plugin
signing {
    // Signing is optional - only required when publishing to Maven Central
    // The vanniktech plugin will fail if signatures are missing for Maven Central publish
    isRequired = false

    // Configure signing credentials
    when {
        hasSigningKeys -> {
            // Option 1: Use in-memory PGP keys (most reliable)
            println("Signing configured with in-memory PGP key (key length: ${signingKey?.length ?: 0})")

            // Clean the key: remove surrounding quotes if present
            val cleanKey = signingKey?.trim()?.removeSurrounding("\"")?.removeSurrounding("'")

            if (cleanKey?.startsWith("-----BEGIN") == true && cleanKey.contains("-----END")) {
                // Key format looks correct
                useInMemoryPgpKeys(cleanKey, signingPassword)
                println("In-memory PGP key configured successfully")
            } else {
                // Key format is incorrect
                println("ERROR: Signing key format is incorrect!")
                println("The key should start with '-----BEGIN PGP PRIVATE KEY BLOCK-----' and end with '-----END PGP PRIVATE KEY BLOCK-----'")
                println("Key preview: ${signingKey?.take(60)}...")
                println("")
                println("In your ~/.gradle/gradle.properties file, the key should look like:")
                println("signingInMemoryKey=-----BEGIN PGP PRIVATE KEY BLOCK-----\\n\\n...key content...\\n-----END PGP PRIVATE KEY BLOCK-----")
                println("")
                println("Important:")
                println("1. Do NOT use quotes around the key")
                println("2. Replace actual newlines with \\n")
                println("3. Use ./export-gpg-key.sh to see the correct format")
            }
        }
        hasGpgAgent -> {
            // Option 2: Use GPG command-line tool with gpg-agent
            println("Signing configured with GPG command-line tool")
            useGpgCmd()
        }
        else -> {
            // No signing configured - will skip signing tasks
            println("WARNING: No signing credentials configured. Artifacts will not be signed.")
            println("For Maven Central publishing, configure signing credentials in ~/.gradle/gradle.properties")
        }
    }
}

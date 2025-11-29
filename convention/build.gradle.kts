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
    implementation(libs.tomlj)  // For parsing CCGO.toml configuration
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

// =============================================================================
// Publishing Configuration
// =============================================================================
// Unified configuration reading (same as common/Publish.kt):
// Priority: Environment Variable > gradle.properties > default
//
// Publish commands:
// - ./gradlew publishToMavenCentral  # Publish to Maven Central
// - ./gradlew publishToMavenLocal    # Publish to local Maven repository
// - ./gradlew publishToMavenCustom   # Publish to all custom Maven repositories
// =============================================================================

// Helper function to get config value with priority: env > gradle.properties
fun getConfigValue(envKey: String, gradleKey: String, defaultValue: String = ""): String {
    return System.getenv(envKey)?.takeIf { it.isNotBlank() }
        ?: project.findProperty(gradleKey)?.toString()?.takeIf { it.isNotBlank() }
        ?: defaultValue
}

// Read credentials using unified priority
val mavenCentralUsername = getConfigValue("MAVEN_CENTRAL_USERNAME", "mavenCentralUsername")
val mavenCentralPassword = getConfigValue("MAVEN_CENTRAL_PASSWORD", "mavenCentralPassword")
val signingKey = getConfigValue("SIGNING_IN_MEMORY_KEY", "signingInMemoryKey")
val signingPassword = getConfigValue("SIGNING_IN_MEMORY_KEY_PASSWORD", "signingInMemoryKeyPassword")
val mavenLocalPath = getConfigValue("MAVEN_LOCAL_PATH", "mavenLocalPath")
val mavenCustomUrls = getConfigValue("MAVEN_CUSTOM_URLS", "mavenCustomUrls")
val mavenCustomUsernames = getConfigValue("MAVEN_CUSTOM_USERNAMES", "mavenCustomUsernames")
val mavenCustomPasswords = getConfigValue("MAVEN_CUSTOM_PASSWORDS", "mavenCustomPasswords")

val hasSigningKeys = signingKey.isNotBlank() && signingPassword.isNotBlank()

// Check if gpg-agent is available with secret keys (for local development)
val hasGpgAgent = try {
    // Check if GPG has any secret keys available
    val process = Runtime.getRuntime().exec(arrayOf("gpg", "--list-secret-keys"))
    val output = process.inputStream.bufferedReader().readText()
    process.waitFor() == 0 && output.contains("sec")
} catch (e: Exception) {
    false
}

// Set default empty values for Maven Central credentials to prevent build service errors
if (!project.hasProperty("mavenCentralUsername")) {
    project.ext.set("mavenCentralUsername", mavenCentralUsername)
}
if (!project.hasProperty("mavenCentralPassword")) {
    project.ext.set("mavenCentralPassword", mavenCentralPassword)
}

// Configure vanniktech maven-publish plugin for Maven Central
mavenPublishing {
    coordinates(group.toString(), "convention", version.toString())

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

    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)

    if (hasSigningKeys || hasGpgAgent) {
        signAllPublications()
    }
}

// Configure custom Maven repositories (local + custom URLs)
publishing {
    repositories {
        // Local Maven repository
        if (mavenLocalPath.isNotBlank()) {
            val expandedPath = if (mavenLocalPath.startsWith("~")) {
                mavenLocalPath.replaceFirst("~", System.getProperty("user.home"))
            } else {
                mavenLocalPath
            }
            maven {
                name = "MavenLocal"
                url = uri(expandedPath)
            }
            println("[Publish] Added Local Maven repository: $expandedPath")
        }

        // Custom Maven repositories (comma-separated)
        if (mavenCustomUrls.isNotBlank()) {
            val urls = mavenCustomUrls.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val usernames = mavenCustomUsernames.split(",").map { it.trim() }
            val passwords = mavenCustomPasswords.split(",").map { it.trim() }

            urls.forEachIndexed { index, url ->
                maven {
                    name = "MavenCustom$index"
                    this.url = uri(url)
                    val username = usernames.getOrElse(index) { "" }
                    val password = passwords.getOrElse(index) { "" }
                    if (username.isNotBlank() && password.isNotBlank()) {
                        credentials {
                            this.username = username
                            this.password = password
                        }
                    }
                }
                println("[Publish] Added Custom Maven repository: MavenCustom$index -> $url")
            }
        }
    }
}

// Register convenient task aliases (only if not already registered)
afterEvaluate {
    // Alias for Maven Central (check if already exists from vanniktech plugin)
    if (tasks.findByName("publishToMavenCentral") == null) {
        tasks.findByName("publishAllPublicationsToMavenCentralRepository")?.let { centralTask ->
            tasks.register("publishToMavenCentral") {
                group = "publishing"
                description = "Publishes all publications to Maven Central"
                dependsOn(centralTask)
            }
        }
    }

    // Alias for Maven Local
    if (tasks.findByName("publishToMavenLocal") == null) {
        tasks.findByName("publishAllPublicationsToMavenLocalRepository")?.let { localTask ->
            tasks.register("publishToMavenLocal") {
                group = "publishing"
                description = "Publishes all publications to the local Maven repository"
                dependsOn(localTask)
            }
        }
    }

    // Alias for Maven Custom (publishes to all custom repos)
    if (tasks.findByName("publishToMavenCustom") == null) {
        val customTasks = mutableListOf<Any>()
        for (i in 0..9) {
            tasks.findByName("publishAllPublicationsToMavenCustom${i}Repository")?.let {
                customTasks.add(it)
            }
        }
        if (customTasks.isNotEmpty()) {
            tasks.register("publishToMavenCustom") {
                group = "publishing"
                description = "Publishes all publications to all custom Maven repositories"
                dependsOn(customTasks)
            }
        }
    }

    // Print available publish commands
    val signingStatus = if (hasSigningKeys) "with signing" else if (hasGpgAgent) "with GPG signing" else "without signing"
    println("[Publish] Available commands ($signingStatus):")
    println("[Publish]   ./gradlew publishToMavenCentral  # Publish to Maven Central")
    if (mavenLocalPath.isNotBlank()) {
        println("[Publish]   ./gradlew publishToMavenLocal    # Publish to local Maven repository")
    }
    if (mavenCustomUrls.isNotBlank()) {
        println("[Publish]   ./gradlew publishToMavenCustom   # Publish to all custom Maven repositories")
    }
}

// Disable signing tasks if no signing credentials are configured
gradle.taskGraph.whenReady {
    if (!hasSigningKeys && !hasGpgAgent) {
        allTasks.filter { it.name.startsWith("sign") }.forEach {
            it.enabled = false
        }
        println("[Publish] Signing disabled (no signing credentials configured)")
    }
}

// Set GPG properties for GnuPG command-line signing
if (hasGpgAgent && !hasSigningKeys) {
    project.ext.set("signing.gnupg.executable", "gpg")
    project.ext.set("signing.gnupg.useLegacyGpg", false)
}

// Configure signing plugin
signing {
    isRequired = false

    when {
        hasSigningKeys -> {
            println("Signing configured with in-memory PGP key (key length: ${signingKey.length})")
            val cleanKey = signingKey.trim().removeSurrounding("\"").removeSurrounding("'")

            if (cleanKey.startsWith("-----BEGIN") && cleanKey.contains("-----END")) {
                useInMemoryPgpKeys(cleanKey, signingPassword)
                println("In-memory PGP key configured successfully")
            } else {
                println("ERROR: Signing key format is incorrect!")
                println("The key should start with '-----BEGIN PGP PRIVATE KEY BLOCK-----'")
                println("Key preview: ${signingKey.take(60)}...")
            }
        }
        hasGpgAgent -> {
            println("Signing configured with GPG command-line tool")
            useGpgCmd()
        }
        else -> {
            println("WARNING: No signing credentials configured. Artifacts will not be signed.")
            println("For Maven Central publishing, configure signing credentials in ~/.gradle/gradle.properties")
        }
    }
}

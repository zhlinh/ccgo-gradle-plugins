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

import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.plugins.signing.Sign
import org.gradle.plugins.signing.SigningExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
    `maven-publish`
    signing
    id("com.gradleup.nmcp") version "1.2.0"
    id("com.gradleup.nmcp.aggregation") version "1.2.0"
}

group = "com.mojeter.ccgo.gradle"
version = rootProject.version

// Configure the build-logic plugins to target JDK 17
// This matches the JDK used to build the project, and is not related to what is running on device.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    // Required for Maven Central
    withSourcesJar()
    withJavadocJar()
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
    implementation(libs.nmcp.gradlePlugin)  // Needed for Maven Central publishing via nmcp
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
        register("kmpPublish") {
            id = "com.mojeter.ccgo.gradle.kmp.publish"
            implementationClass = "KmpPublishConventionPlugin"
        }
        register("kmpLibraryNativePython") {
            id = "com.mojeter.ccgo.gradle.kmp.library.native.python"
            implementationClass = "KmpLibraryNativePythonConventionPlugin"
        }
        register("kmpLibraryNativeEmpty") {
            id = "com.mojeter.ccgo.gradle.kmp.library.native.empty"
            implementationClass = "KmpLibraryNativeEmptyConventionPlugin"
        }
        register("kmpRoot") {
            id = "com.mojeter.ccgo.gradle.kmp.root"
            implementationClass = "KmpRootConventionPlugin"
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

// Track if signing was successfully configured (will be set during signing configuration)
var signingConfigured = false

// Check if gpg-agent is available with secret keys (for local development)
val hasGpgAgent = try {
    // Check if GPG has any secret keys available
    val process = Runtime.getRuntime().exec(arrayOf("gpg", "--list-secret-keys"))
    val output = process.inputStream.bufferedReader().readText()
    process.waitFor() == 0 && output.contains("sec")
} catch (e: Exception) {
    false
}

// Configure nmcp aggregation plugin for Maven Central
// nmcp uses System.getenv() directly, so we can use custom environment variable names
nmcpAggregation {
    centralPortal {
        username = mavenCentralUsername
        password = mavenCentralPassword
        // AUTOMATIC: auto-release after upload, USER_MANAGED: manual release from portal
        publishingType = "AUTOMATIC"
    }
}

// Add this project to nmcp aggregation
dependencies {
    "nmcpAggregation"(project)
}

// Configure Maven publishing (repositories + publications)
publishing {
    repositories {
        // Local Maven repository (use default ~/.m2/repository if not configured)
        val localRepoPath = if (mavenLocalPath.isNotBlank()) {
            if (mavenLocalPath.startsWith("~")) {
                mavenLocalPath.replaceFirst("~", System.getProperty("user.home"))
            } else {
                mavenLocalPath
            }
        } else {
            "${System.getProperty("user.home")}/.m2/repository"
        }
        maven {
            name = "MavenLocal"
            url = uri(localRepoPath)
        }
        println("[Publish] Added Local Maven repository: $localRepoPath")

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

    // POM configuration is done in afterEvaluate block to cover all publications
    // including those generated by kotlin-dsl plugin
}

// Add POM information to all plugin marker publications and configure task dependencies
afterEvaluate {
    publishing {
        publications.withType<MavenPublication>().configureEach {
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
        }
    }

    // Ensure publish tasks depend on sign tasks
    tasks.withType<PublishToMavenRepository>().configureEach {
        val publicationName = name.substringAfter("publish").substringBefore("Publication")
        val signTaskName = "sign${publicationName}Publication"
        val signTask = tasks.findByName(signTaskName)
        if (signTask != null) {
            dependsOn(signTask)
        }
    }
}

// Add success message to publish tasks using buildFinished to check actual result
afterEvaluate {
    // Get artifact info for success message
    val groupId = group.toString()
    val artifactId = "convention"
    val versionName = version.toString()

    // Calculate local path
    val expandedLocalPath = if (mavenLocalPath.isNotBlank()) {
        if (mavenLocalPath.startsWith("~")) {
            mavenLocalPath.replaceFirst("~", System.getProperty("user.home"))
        } else {
            mavenLocalPath
        }
    } else {
        "${System.getProperty("user.home")}/.m2/repository"
    }
    val artifactPath = "$expandedLocalPath/${groupId.replace('.', '/')}/$artifactId/$versionName"

    // Alias for Maven Central (nmcp uses publishAggregationToCentralPortal)
    val centralPortalTask = tasks.findByName("publishAggregationToCentralPortal")
    if (centralPortalTask != null && tasks.findByName("publishToMavenCentral") == null) {
        tasks.register("publishToMavenCentral") {
            group = "publishing"
            description = "Publishes all publications to Maven Central via Central Portal"
            dependsOn(centralPortalTask)
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
    println("[Publish] ./gradlew publishToMavenCentral  # Publish to Maven Central")
    println("[Publish] ./gradlew publishToMavenLocal    # Publish to local Maven repository")
    if (mavenCustomUrls.isNotBlank()) {
        println("[Publish] ./gradlew publishToMavenCustom   # Publish to all custom Maven repositories")
    }

    // Track which publish tasks completed successfully
    var localTaskSuccess = false
    var centralTaskSuccess = false
    var customTaskSuccess = false

    // Use taskGraph.afterTask to track successful task completion
    gradle.taskGraph.afterTask {
        if (state.failure == null) {
            when (name) {
                "publishToMavenLocal", "publishAllPublicationsToMavenLocalRepository" -> {
                    localTaskSuccess = true
                }
                // nmcp task: publishAggregationToCentralPortal
                "publishToMavenCentral", "publishAggregationToCentralPortal" -> {
                    centralTaskSuccess = true
                }
                "publishToMavenCustom" -> {
                    customTaskSuccess = true
                }
            }
        }
    }

    // Use gradle.buildFinished to print success message only when build succeeds
    gradle.buildFinished {
        if (failure != null) {
            // Build failed, don't print success message
            return@buildFinished
        }

        if (localTaskSuccess) {
            println("")
            println("=".repeat(80))
            println("[Publish] SUCCESS: Published to Maven Local")
            println("[Publish] Repository: $expandedLocalPath")
            println("[Publish] Artifact path: $artifactPath")
            println("[Publish] Coordinates: $groupId:$artifactId:$versionName")
            println("=".repeat(80))
        }

        if (centralTaskSuccess) {
            println("")
            println("=".repeat(80))
            println("[Publish] SUCCESS: Published to Maven Central")
            println("[Publish] URL: https://central.sonatype.com")
            println("[Publish] Search: https://central.sonatype.com/search?q=$groupId:$artifactId")
            println("[Publish] Artifact: https://central.sonatype.com/artifact/$groupId/$artifactId/$versionName")
            println("[Publish] Coordinates: $groupId:$artifactId:$versionName")
            println("=".repeat(80))
        }

        if (customTaskSuccess) {
            val urls = mavenCustomUrls.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            println("")
            println("=".repeat(80))
            println("[Publish] SUCCESS: Published to Maven Custom repositories")
            urls.forEachIndexed { index, url ->
                println("[Publish] Repository $index: $url")
            }
            println("[Publish] Coordinates: $groupId:$artifactId:$versionName")
            println("=".repeat(80))
        }
    }
}

// Disable signing tasks if signing was not successfully configured
gradle.taskGraph.whenReady {
    if (!signingConfigured) {
        allTasks.filter { it.name.startsWith("sign") }.forEach {
            it.enabled = false
        }
        println("[Publish] Signing tasks disabled (signing not properly configured)")
    }
}

// Set GPG properties for GnuPG command-line signing
if (hasGpgAgent && !hasSigningKeys) {
    project.ext.set("signing.gnupg.executable", "gpg")
    project.ext.set("signing.gnupg.useLegacyGpg", false)
}

// Helper function to validate and prepare PGP key
fun preparePgpKey(key: String): String? {
    var cleanKey = key.trim().removeSurrounding("\"").removeSurrounding("'")

    // Check if the key contains literal \n (escaped newlines) and convert them to actual newlines
    if (cleanKey.contains("\\n")) {
        cleanKey = cleanKey.replace("\\n", "\n")
    }

    // Validate key format
    if (!cleanKey.startsWith("-----BEGIN PGP PRIVATE KEY BLOCK-----")) {
        println("[Signing] ERROR: Key does not start with '-----BEGIN PGP PRIVATE KEY BLOCK-----'")
        println("[Signing] Key preview: ${cleanKey.take(60)}...")
        return null
    }

    if (!cleanKey.contains("-----END PGP PRIVATE KEY BLOCK-----")) {
        println("[Signing] ERROR: Key does not contain '-----END PGP PRIVATE KEY BLOCK-----'")
        return null
    }

    // Check if key has proper line breaks (should have multiple lines)
    val lineCount = cleanKey.lines().size
    if (lineCount < 5) {
        println("[Signing] ERROR: Key appears to be malformed (only $lineCount lines, expected multiple lines)")
        println("[Signing] Hint: If your key is stored with escaped newlines (\\n), they should be converted to actual line breaks")
        return null
    }

    return cleanKey
}

// Configure signing plugin
signing {
    isRequired = false

    when {
        hasSigningKeys -> {
            println("[Signing] Configuring with in-memory PGP key (raw length: ${signingKey.length})")
            val preparedKey = preparePgpKey(signingKey)

            if (preparedKey != null) {
                try {
                    useInMemoryPgpKeys(preparedKey, signingPassword)
                    println("[Signing] In-memory PGP key configured successfully (${preparedKey.lines().size} lines)")
                    signingConfigured = true
                    // Sign all publications
                    sign(publishing.publications)
                } catch (e: Exception) {
                    println("[Signing] ERROR: Failed to configure PGP key: ${e.message}")
                    println("[Signing] Please verify your signing key and password are correct")
                    signingConfigured = false
                }
            } else {
                println("[Signing] ERROR: Signing key validation failed. Signing will be disabled.")
                println("[Signing] To fix this, ensure your signingInMemoryKey:")
                println("[Signing]   1. Starts with '-----BEGIN PGP PRIVATE KEY BLOCK-----'")
                println("[Signing]   2. Ends with '-----END PGP PRIVATE KEY BLOCK-----'")
                println("[Signing]   3. Contains the full key content with proper line breaks")
                println("[Signing]   4. If using environment variable, ensure newlines are preserved")
                signingConfigured = false
            }
        }
        hasGpgAgent -> {
            println("[Signing] Configured with GPG command-line tool")
            useGpgCmd()
            signingConfigured = true
            // Sign all publications
            sign(publishing.publications)
        }
        else -> {
            println("[Signing] WARNING: No signing credentials configured. Artifacts will not be signed.")
            println("[Signing] For Maven Central publishing, configure signing credentials in ~/.gradle/gradle.properties")
            signingConfigured = false
        }
    }
}

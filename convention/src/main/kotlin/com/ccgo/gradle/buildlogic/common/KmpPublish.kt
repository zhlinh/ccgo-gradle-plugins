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

package com.ccgo.gradle.buildlogic.common

import com.ccgo.gradle.buildlogic.common.utils.ConfigKey
import com.ccgo.gradle.buildlogic.common.utils.ConfigProvider
import nmcp.NmcpExtension
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.signing.SigningExtension

private const val DEFAULT_CENTRAL_DOMAIN = "central.sonatype.com"

// Repository names for unified command naming
private const val REPO_NAME_LOCAL = "MavenLocal"
private const val REPO_NAME_CENTRAL = "MavenCentral"
private const val REPO_NAME_CUSTOM = "MavenCustom"

/**
 * Configures KMP (Kotlin Multiplatform) publishing for the project.
 *
 * Publish commands (unified naming with ccgo prefix):
 * - ./gradlew ccgoPublishToMavenLocal    # Publish to local Maven repository
 * - ./gradlew ccgoPublishToMavenCentral  # Publish to Maven Central
 * - ./gradlew ccgoPublishToMavenCustom   # Publish to custom Maven repository
 *
 * Configuration sources (priority from high to low):
 * 1. Environment variables (for CI/CD override)
 * 2. CCGO.toml (project root)
 * 3. Project-level gradle.properties
 * 4. User-level ~/.gradle/gradle.properties
 *
 * Supported environment variables and gradle.properties keys:
 * - MAVEN_CENTRAL_USERNAME / mavenCentralUsername
 * - MAVEN_CENTRAL_PASSWORD / mavenCentralPassword
 * - SIGNING_IN_MEMORY_KEY / signingInMemoryKey
 * - SIGNING_IN_MEMORY_KEY_PASSWORD / signingInMemoryKeyPassword
 * - MAVEN_LOCAL_PATH / mavenLocalPath
 * - MAVEN_CUSTOM_URLS / mavenCustomUrls (comma-separated)
 * - MAVEN_CUSTOM_USERNAMES / mavenCustomUsernames (comma-separated)
 * - MAVEN_CUSTOM_PASSWORDS / mavenCustomPasswords (comma-separated)
 */
internal fun Project.configureKmpPublish() {
    // Configure nmcp for Maven Central publishing
    configureKmpNmcp()

    project.afterEvaluate {
        configureKmpMaven()
        configureKmpSign()
    }
}

/**
 * Configures nmcp plugin for Maven Central publishing via Central Portal.
 */
private fun Project.configureKmpNmcp() {
    val centralUsername = ConfigProvider.get(project, ConfigKey.MAVEN_CENTRAL_USERNAME)
    val centralPassword = ConfigProvider.get(project, ConfigKey.MAVEN_CENTRAL_PASSWORD)

    extensions.configure<NmcpExtension> {
        publishAllPublicationsToCentralPortal {
            username.set(centralUsername)
            password.set(centralPassword)
            // AUTOMATIC: auto-release after upload, USER_MANAGED: manual release from portal
            publishingType.set("AUTOMATIC")
        }
    }

    if (centralUsername.isNotEmpty() && centralPassword.isNotEmpty()) {
        println("[KMP-Publish] Maven Central credentials configured via nmcp")
    } else {
        println("[KMP-Publish] Maven Central credentials not found (nmcp configured but may fail)")
    }
}

// Lazy providers for git info to avoid configuration cache issues
private fun Project.gitRepoUrlProvider(): Provider<String> = providers.exec {
    commandLine("git", "config", "--get", "remote.origin.url")
}.standardOutput.asText.map { it.trim().ifEmpty { "" } }

private fun Project.gitUserNameProvider(): Provider<String> = providers.exec {
    commandLine("git", "config", "--get", "user.name")
}.standardOutput.asText.map { it.trim().ifEmpty { "Unknown" } }

private fun Project.gitUserEmailProvider(): Provider<String> = providers.exec {
    commandLine("git", "config", "--get", "user.email")
}.standardOutput.asText.map { it.trim().ifEmpty { "" } }

private fun Project.hasGpgAgentProvider(): Provider<Boolean> = providers.exec {
    commandLine("gpg", "--list-secret-keys")
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.contains("sec") }

/**
 * Prepares and validates a PGP key for signing.
 */
private fun prepareKmpPgpKey(key: String): String? {
    var cleanKey = key.trim().removeSurrounding("\"").removeSurrounding("'")

    // Convert escaped newlines to actual newlines
    if (cleanKey.contains("\\n")) {
        cleanKey = cleanKey.replace("\\n", "\n")
    }

    // Validate key format
    if (!cleanKey.startsWith("-----BEGIN PGP PRIVATE KEY BLOCK-----")) {
        println("[KMP-Signing] ERROR: Key does not start with '-----BEGIN PGP PRIVATE KEY BLOCK-----'")
        return null
    }

    if (!cleanKey.contains("-----END PGP PRIVATE KEY BLOCK-----")) {
        println("[KMP-Signing] ERROR: Key does not contain '-----END PGP PRIVATE KEY BLOCK-----'")
        return null
    }

    val lineCount = cleanKey.lines().size
    if (lineCount < 5) {
        println("[KMP-Signing] ERROR: Key appears to be malformed (only $lineCount lines)")
        return null
    }

    return cleanKey
}

/**
 * Configures signing for KMP publications.
 */
private fun Project.configureKmpSign() {
    // Check for in-memory signing keys
    val rawSigningKey = ConfigProvider.get(project, ConfigKey.SIGNING_KEY)
    val signingPassword = ConfigProvider.get(project, ConfigKey.SIGNING_KEY_PASSWORD)
    val hasSigningKeys = rawSigningKey.isNotEmpty() && signingPassword.isNotEmpty()

    // Use lazy provider for GPG check
    val hasGpgAgent = hasGpgAgentProvider()

    extensions.configure<SigningExtension> {
        isRequired = false

        when {
            hasSigningKeys -> {
                println("[KMP-Signing] Configuring with in-memory PGP key")
                val preparedKey = prepareKmpPgpKey(rawSigningKey)

                if (preparedKey != null) {
                    try {
                        useInMemoryPgpKeys(preparedKey, signingPassword)
                        println("[KMP-Signing] In-memory PGP key configured successfully")
                        // Sign all KMP publications
                        val publishing = project.extensions.getByName("publishing") as PublishingExtension
                        publishing.publications.withType<MavenPublication>().configureEach {
                            sign(this)
                        }
                    } catch (e: Exception) {
                        println("[KMP-Signing] ERROR: Failed to configure PGP key: ${e.message}")
                    }
                }
            }
            hasGpgAgent.getOrElse(false) -> {
                println("[KMP-Signing] Configured with GPG command-line tool")
                useGpgCmd()
                val publishing = project.extensions.getByName("publishing") as PublishingExtension
                publishing.publications.withType<MavenPublication>().configureEach {
                    sign(this)
                }
            }
            else -> {
                println("[KMP-Signing] WARNING: No signing credentials configured")
                println("[KMP-Signing] For Maven Central publishing, configure signing credentials")
            }
        }
    }

    // Configure signing tasks to be skipped if no signing is configured
    tasks.withType(org.gradle.plugins.signing.Sign::class.java).configureEach {
        onlyIf {
            hasSigningKeys || hasGpgAgent.getOrElse(false)
        }
    }
}

/**
 * Configures Maven repositories for KMP publishing.
 */
private fun Project.configureKmpMaven() {
    // Get lazy providers for git info
    val gitUrl = gitRepoUrlProvider()
    val gitUserName = gitUserNameProvider()
    val gitUserEmail = gitUserEmailProvider()

    extensions.configure<PublishingExtension> {
        repositories {
            // 1. Local Maven repository
            val configuredPath = ConfigProvider.getLocalMavenPath(project)
            val localPath = if (!configuredPath.isNullOrBlank()) {
                if (configuredPath.startsWith("~")) {
                    configuredPath.replaceFirst("~", System.getProperty("user.home"))
                } else {
                    configuredPath
                }
            } else {
                "${System.getProperty("user.home")}/.m2/repository"
            }
            maven {
                name = REPO_NAME_LOCAL
                url = uri(localPath)
                println("[KMP-Publish] Added Local Maven repository: $localPath")
            }

            // 2. Custom Maven repositories
            val customRepos = ConfigProvider.getCustomMavenRepos(project)
            customRepos.forEach { repo ->
                if (repo.url.contains(DEFAULT_CENTRAL_DOMAIN)) {
                    return@forEach
                }
                val repoName = "${REPO_NAME_CUSTOM}${repo.index}"
                maven {
                    name = repoName
                    url = uri(repo.url)
                    if (repo.username.isNotEmpty() && repo.password.isNotEmpty()) {
                        credentials {
                            username = repo.username
                            password = repo.password
                        }
                    }
                    println("[KMP-Publish] Added Custom Maven repository: $repoName -> ${repo.url}")
                }
            }
        }

        // Configure POM for all publications using lazy providers
        publications.withType<MavenPublication>().configureEach {
            pom {
                url.set(gitUrl)

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        id.set(gitUserName)
                        name.set(gitUserName)
                        email.set(gitUserEmail)
                    }
                }
                scm {
                    url.set(gitUrl)
                    connection.set(gitUrl.map { "scm:git:$it" })
                    developerConnection.set(gitUrl.map { "scm:git:$it" })
                }
            }
        }
    }

    // Register task aliases
    registerKmpPublishTaskAliases()
}

/**
 * Registers convenient task aliases for KMP publishing.
 */
private fun Project.registerKmpPublishTaskAliases() {
    // Register alias for Maven Local
    tasks.register("ccgoPublishTo${REPO_NAME_LOCAL}") {
        group = "publishing"
        description = "Publishes all KMP publications to the local Maven repository"
        dependsOn(tasks.matching { it.name.contains("PublicationTo${REPO_NAME_LOCAL}Repository") })

        val groupId = project.group.toString()
        val artifactId = project.name
        val versionName = project.version.toString()

        val configuredPath = ConfigProvider.getLocalMavenPath(project)
        val expandedLocalPath = if (!configuredPath.isNullOrBlank()) {
            if (configuredPath.startsWith("~")) {
                configuredPath.replaceFirst("~", System.getProperty("user.home"))
            } else {
                configuredPath
            }
        } else {
            "${System.getProperty("user.home")}/.m2/repository"
        }

        doLast {
            println("")
            println("=".repeat(80))
            println("[KMP-Publish] SUCCESS: Published to Maven Local")
            println("[KMP-Publish] Repository: $expandedLocalPath")
            println("[KMP-Publish] Coordinates: $groupId:$artifactId:$versionName")
            println("=".repeat(80))
        }
    }
    println("[KMP-Publish] Command: ./gradlew ccgoPublishTo${REPO_NAME_LOCAL}")

    // Register alias for Maven Central (uses nmcp plugin which is applied by ccgo-gradle-plugins)
    // Maven Central now uses Central Portal (central.sonatype.com) which requires nmcp plugin
    val centralUsername = ConfigProvider.get(project, ConfigKey.MAVEN_CENTRAL_USERNAME)
    val centralPassword = ConfigProvider.get(project, ConfigKey.MAVEN_CENTRAL_PASSWORD)
    val hasCentralCredentials = centralUsername.isNotEmpty() && centralPassword.isNotEmpty()

    tasks.register("ccgoPublishTo${REPO_NAME_CENTRAL}") {
        group = "publishing"
        description = "Publishes all KMP publications to Maven Central via Central Portal"

        // nmcp plugin is applied by ccgo-gradle-plugins, depend on its task
        dependsOn(tasks.matching { it.name == "publishAllPublicationsToCentralPortal" })

        val groupId = project.group.toString()
        val artifactId = project.name
        val versionName = project.version.toString()

        doFirst {
            if (!hasCentralCredentials) {
                throw org.gradle.api.GradleException("""
                    |
                    |[KMP-Publish] ERROR: Maven Central credentials not configured
                    |
                    |To publish to Maven Central, configure credentials in one of:
                    |
                    |1. Environment variables:
                    |   MAVEN_CENTRAL_USERNAME=your-username
                    |   MAVEN_CENTRAL_PASSWORD=your-password
                    |
                    |2. CCGO.toml [publish.maven] section:
                    |   central_username = "your-username"
                    |   central_password = "your-password"
                    |
                    |3. gradle.properties:
                    |   mavenCentralUsername=your-username
                    |   mavenCentralPassword=your-password
                    |
                    |Get your Central Portal token:
                    |   - Go to https://central.sonatype.com/
                    |   - Login → View Account → Generate User Token
                    |
                    |For local publishing, use: ./gradlew ccgoPublishToMavenLocal
                    |""".trimMargin())
            }
        }

        doLast {
            println("")
            println("=".repeat(80))
            println("[KMP-Publish] SUCCESS: Published to Maven Central")
            println("[KMP-Publish] URL: https://central.sonatype.com")
            println("[KMP-Publish] Coordinates: $groupId:$artifactId:$versionName")
            println("=".repeat(80))
        }
    }
    println("[KMP-Publish] Command: ./gradlew ccgoPublishTo${REPO_NAME_CENTRAL}")

    // Register alias for Maven Custom (always register, show error if not configured)
    val customRepos = ConfigProvider.getCustomMavenRepos(project)
    tasks.register("ccgoPublishTo${REPO_NAME_CUSTOM}") {
        group = "publishing"
        description = "Publishes all KMP publications to custom Maven repositories"

        if (customRepos.isNotEmpty()) {
            for (i in 0..9) {
                dependsOn(tasks.matching { it.name.contains("PublicationTo${REPO_NAME_CUSTOM}${i}Repository") })
            }

            val groupId = project.group.toString()
            val artifactId = project.name
            val versionName = project.version.toString()

            doLast {
                println("")
                println("=".repeat(80))
                println("[KMP-Publish] SUCCESS: Published to Maven Custom repositories")
                customRepos.forEach { repo ->
                    println("[KMP-Publish] Repository ${repo.index}: ${repo.url}")
                }
                println("[KMP-Publish] Coordinates: $groupId:$artifactId:$versionName")
                println("=".repeat(80))
            }
        } else {
            doFirst {
                throw org.gradle.api.GradleException("""
                    |
                    |[KMP-Publish] ERROR: No custom Maven repositories configured
                    |
                    |To publish to custom Maven repositories, configure one of:
                    |
                    |1. Environment variables:
                    |   MAVEN_CUSTOM_URLS=https://your-repo.com/maven
                    |   MAVEN_CUSTOM_USERNAMES=your-username
                    |   MAVEN_CUSTOM_PASSWORDS=your-password
                    |
                    |2. CCGO.toml [publish.maven] section:
                    |   custom_urls = ["https://your-repo.com/maven"]
                    |   custom_usernames = ["your-username"]
                    |   custom_passwords = ["your-password"]
                    |
                    |3. gradle.properties:
                    |   mavenCustomUrls=https://your-repo.com/maven
                    |   mavenCustomUsernames=your-username
                    |   mavenCustomPasswords=your-password
                    |
                    |For local publishing, use: ./gradlew ccgoPublishToMavenLocal
                    |""".trimMargin())
            }
        }
    }
    if (customRepos.isNotEmpty()) {
        println("[KMP-Publish] Command: ./gradlew ccgoPublishTo${REPO_NAME_CUSTOM}")
    }
}

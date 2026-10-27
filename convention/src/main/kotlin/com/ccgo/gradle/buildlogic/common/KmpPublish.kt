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
 * - CCGO_MAVEN_URL / mavenCustomUrls (comma-separated)
 * - CCGO_MAVEN_USERNAME / mavenCustomUsernames (comma-separated)
 * - CCGO_MAVEN_PASSWORD / mavenCustomPasswords (comma-separated)
 */
internal fun Project.configureKmpPublish() {
    // Configure nmcp for Maven Central publishing
    configureKmpNmcp()

    project.afterEvaluate {
        configureKmpMaven()
        configureKmpSign()
    }

    providers.gradleProperty("ccgoKmpSdkManifest").orNull?.let { manifest ->
        // Kotlin creates publications in afterEvaluate. Replace them only once
        // every project has finished configuring, before the task graph is built.
        gradle.projectsEvaluated {
            configureKmpSdkPublications(file(manifest))
        }
    }
}

/** Publish only the prebuilt platform modules present in a CCGO SDK. */
private fun Project.configureKmpSdkPublications(manifestFile: java.io.File) {
    @Suppress("UNCHECKED_CAST")
    val manifest = groovy.json.JsonSlurper().parse(manifestFile) as Map<String, Any>
    val config = CCGOConfig.getDefault(this)
    require(manifest["project"] == config.projectName) {
        "KMP SDK project '${manifest["project"]}' does not match '${config.projectName}'"
    }
    val sdkVersion = manifest["version"] as String
    @Suppress("UNCHECKED_CAST")
    val artifacts = manifest["artifacts"] as List<Map<String, Any?>>
    require(artifacts.isNotEmpty()) { "KMP SDK contains no artifacts" }
    val publishing = extensions.getByType(PublishingExtension::class.java)
    val previous = publishing.publications.toSet()
    previous.filterIsInstance<MavenPublication>().forEach {
        it.setArtifacts(emptyList<Any>())
    }

    // Removing publications leaves their publish tasks registered in Gradle.
    // Remove those tasks' dependencies too: disabling a task alone still builds
    // its inputs, which could silently rebuild the SDK from current sources.
    tasks.withType(org.gradle.api.publish.maven.tasks.AbstractPublishToMaven::class.java).forEach { task ->
        if (task.publication in previous) {
            task.enabled = false
            task.setDependsOn(emptyList<Any>())
        }
    }
    publishing.publications.clear()
    artifacts.groupBy { it["target"] as String }.forEach { (target, files) ->
        publishing.publications.create("ccgoSdk${target.replaceFirstChar { it.uppercaseChar() }}", MavenPublication::class.java) {
            groupId = config.kmpGroupId
            artifactId = "${config.kmpArtifactId}-$target"
            version = sdkVersion
            files.forEach { entry ->
                artifact(file(entry["path"] as String)) {
                    classifier = entry["classifier"] as String?
                }
            }
            pom {
                name.set("${config.projectName} KMP - $target")
                description.set("Prebuilt ${config.projectName} KMP SDK ($target)")
                // SDK archives do not contain POM/module metadata. Preserve the
                // dependencies explicitly declared by the project configuration.
                withXml {
                    if (config.kmpDependencies.isNotEmpty()) {
                        val dependencies = asNode().appendNode("dependencies")
                        config.kmpDependencies.forEach { dep ->
                            val node = dependencies.appendNode("dependency")
                            node.appendNode("groupId", dep.group)
                            node.appendNode("artifactId", dep.artifact)
                            node.appendNode("version", dep.version)
                            node.appendNode("scope", "compile")
                        }
                    }
                }
            }
            println("[KMP-SDK] Publishing $groupId:$artifactId:$version from ${files.size} prebuilt file(s)")
        }
    }
    // Publish aggregators retain tasks for removed publications. Filter the
    // resolved direct dependencies, preserving nmcp's own preparation tasks.
    tasks.matching {
        it.name == "publish" || it.name == "publishToMavenLocal" ||
            it.name.startsWith("publishAllPublicationsTo") || it.name.startsWith("ccgoPublishTo")
    }.forEach { aggregate ->
        aggregate.setDependsOn(aggregate.taskDependencies.getDependencies(aggregate).filterNot {
            it is org.gradle.api.publish.maven.tasks.AbstractPublishToMaven && it.publication in previous
        })
    }
    extensions.extraProperties.set("ccgoKmpSdkSupported", true)
    println("[KMP-SDK] Only bundled platform modules are published; no root KMP module is synthesized")
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
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim().ifEmpty { "" } }

private fun Project.gitUserNameProvider(): Provider<String> = providers.exec {
    commandLine("git", "config", "--get", "user.name")
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim().ifEmpty { "Unknown" } }

private fun Project.gitUserEmailProvider(): Provider<String> = providers.exec {
    commandLine("git", "config", "--get", "user.email")
    isIgnoreExitValue = true
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
    // A machine publishing locally may not have gpg installed at all.
    val hasGpgAgent = runCatching { hasGpgAgentProvider().getOrElse(false) }.getOrDefault(false)

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
            hasGpgAgent -> {
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
            hasSigningKeys || hasGpgAgent
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
            printKmpCoordinates("$groupId:$artifactId:$versionName")
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
                // NOTE: the TOML placeholders below are "..." on purpose. The
                // secrets scanner flags `password = "<6+ chars>"` even inside this
                // help text, and an inline linthis:ignore can't be used — it would
                // land inside the raw string and be printed to the user.
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
                    |   central_username = "..."
                    |   central_password = "..."
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
            printKmpCoordinates("$groupId:$artifactId:$versionName")
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
                printKmpCoordinates("$groupId:$artifactId:$versionName")
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
                    |   CCGO_MAVEN_URL=https://your-repo.com/maven
                    |   CCGO_MAVEN_USERNAME=your-username
                    |   CCGO_MAVEN_PASSWORD=your-password
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

private fun Project.printKmpCoordinates(fallback: String) {
    if (providers.gradleProperty("ccgoKmpSdkManifest").isPresent) {
        extensions.getByType(PublishingExtension::class.java).publications.withType(MavenPublication::class.java).forEach {
            println("[KMP-Publish] Coordinates: ${it.groupId}:${it.artifactId}:${it.version}")
        }
    } else {
        println("[KMP-Publish] Coordinates: $fallback")
    }
}

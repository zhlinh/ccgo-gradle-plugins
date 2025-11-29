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
import com.ccgo.gradle.buildlogic.common.utils.getGitRepoUrl
import com.ccgo.gradle.buildlogic.common.utils.getGitRepoUserEmail
import com.ccgo.gradle.buildlogic.common.utils.getGitRepoUserName
import com.ccgo.gradle.buildlogic.common.utils.getGpgKeyFromKeyId
import com.ccgo.gradle.buildlogic.common.utils.getGpgKeyFromKeyRingFile
import com.ccgo.gradle.buildlogic.common.utils.getLocalProperties
import com.vanniktech.maven.publish.SonatypeHost
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.api.tasks.Exec
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.signing.SigningExtension

private const val DEFAULT_CENTRAL_DOMAIN = "central.sonatype.com"

// Repository names for unified command naming
// Local:  ./gradlew publishToMavenLocal --no-daemon
private const val REPO_NAME_LOCAL = "MavenLocal"
// Custom: ./gradlew publishToMavenCustom --no-daemon (publishes to all custom repos)
private const val REPO_NAME_CUSTOM = "MavenCustom"

// Publication names
// Release: ./gradlew publishAllPublicationsToMavenLocalRepository --no-daemon
private const val RELEASE_PUBLICATION_NAME = "release"
// Test:    ./gradlew publishTestPublicationToMavenLocalRepository --no-daemon
private const val TEST_PUBLICATION_NAME = "test"
// Maven Central (vanniktech): ./gradlew publishAllPublicationsToMavenCentralRepository --no-daemon
private const val MAVEN_PUBLICATION_NAME = "maven"

/**
 * Configures the publishing for the project.
 *
 * Publish commands (unified naming):
 * - ./gradlew publishToMavenCentral  # Publish to Maven Central
 * - ./gradlew publishToMavenLocal    # Publish to local Maven repository
 * - ./gradlew publishToMavenCustom   # Publish to custom Maven repository
 *
 * Configuration sources (priority from high to low):
 * 1. Environment variables
 * 2. gradle.properties (project level or user level ~/.gradle/gradle.properties)
 * 3. CCGO.toml (project root)
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
internal fun Project.configurePublish() {
    project.afterEvaluate {
        configureSourcesAndJavaDoc()
        setSystemEnv()
        configureMaven()
        if (cfgs.commIsSignEnabled) {
            configureSign()
        }
    }
}

/**
 * Configures the signing for the project.
 */
private fun Project.configureSign() {
    extensions.configure<SigningExtension> {
        sign {
            val publishing = project.extensions.getByName("publishing") as PublishingExtension
            val signingKey = getSigningInMemoryKey()
            if (signingKey.isEmpty()) {
                println("sign signingKey is empty, skip sign publication")
                return@sign
            }
            val signingPassword = ConfigProvider.get(project, ConfigKey.SIGNING_KEY_PASSWORD)
            useInMemoryPgpKeys(signingKey, signingPassword)
            publishing.publications.asMap.filter { it.key == RELEASE_PUBLICATION_NAME }.forEach { (_, publication) ->
                println("sign publication: ${publication.name}")
                sign(publication as MavenPublication)
            }
        }
    }
}

/**
 * Configures the maven publishing for the project.
 *
 * Supports three types of Maven repositories:
 * 1. Maven Central - via vanniktech plugin
 * 2. Local Maven - local directory path
 * 3. Custom Maven - custom URL with optional credentials
 */
private fun Project.configureMaven() {
    // configure custom maven repositories (local + custom URL)
    configureCustomMaven()
    // configure central maven repository
    configureCentralMaven()
}

private fun Project.configureCentralMaven() {
    configureVanniktechCentralMaven()
}

private fun Project.configureVanniktechCentralMaven() {
    // https://github.com/vanniktech/gradle-maven-publish-plugin
    extensions.configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
        coordinates(
            groupId = cfgs.commGroupId,
            artifactId = getProjectArtifactId(),
            version = cfgs.versionName
        )
        publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
        if (cfgs.commIsSignEnabled) {
            // sign after call publish
            signAllPublications()
        }
    }
}

/**
 * Configures custom Maven repositories including local and remote.
 *
 * Unified command naming:
 * - Maven Central: ./gradlew publishAllPublicationsToMavenCentralRepository
 * - Local Maven:   ./gradlew publishToMavenLocal
 * - Custom Maven:  ./gradlew publishToMavenCustom (or publishToMavenCustom0, publishToMavenCustom1, ...)
 *
 * Configuration via environment variables or gradle.properties:
 * - Local: MAVEN_LOCAL_PATH / mavenLocalPath
 * - Custom: MAVEN_CUSTOM_URLS / mavenCustomUrls (comma-separated)
 *           MAVEN_CUSTOM_USERNAMES / mavenCustomUsernames (comma-separated)
 *           MAVEN_CUSTOM_PASSWORDS / mavenCustomPasswords (comma-separated)
 */
private fun Project.configureCustomMaven() {
    extensions.configure<PublishingExtension> {
        repositories {
            var validRepoCount = 0

            // 1. Configure local Maven repository
            // Command: ./gradlew publishToMavenLocal
            val localPath = ConfigProvider.getLocalMavenPath(project)
            if (!localPath.isNullOrBlank()) {
                maven {
                    name = REPO_NAME_LOCAL
                    url = uri(localPath)
                    println("[Publish] Added Local Maven repository: $localPath")
                    println("[Publish] Command: ./gradlew publishTo${REPO_NAME_LOCAL}")
                }
                validRepoCount++
            }

            // 2. Configure custom Maven repositories (comma-separated)
            // Command: ./gradlew publishToMavenCustom (publishes to all custom repos)
            val customRepos = ConfigProvider.getCustomMavenRepos(project)
            customRepos.forEach { repo ->
                // Skip Maven Central URLs (handled by vanniktech plugin)
                if (repo.url.contains(DEFAULT_CENTRAL_DOMAIN)) {
                    println("[Publish] Skipping Maven Central URL in custom repos: ${repo.url}")
                    return@forEach
                }

                // Always use indexed name (MavenCustom0, MavenCustom1, ...)
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
                    println("[Publish] Added Custom Maven repository: $repoName -> ${repo.url}")
                }
                validRepoCount++
            }
            if (customRepos.isNotEmpty()) {
                println("[Publish] Command: ./gradlew publishToMavenCustom (publishes to all ${customRepos.size} custom repos)")
            }

            // 3. Check if we have at least one repository configured
            val taskName = project.gradle.startParameter.taskNames.firstOrNull()
            if (validRepoCount == 0) {
                val isPublishTask = taskName?.contains("publish") == true &&
                    !taskName.contains("MavenCentral")
                if (isPublishTask) {
                    throw Exception(getConfigurationHint())
                } else {
                    println(getConfigurationHint())
                }
            }
        }

        publications {
            val publishConfig = mutableMapOf(
                // release always use release build
                RELEASE_PUBLICATION_NAME to "target/${cfgs.getMainArchiveAarName("release")}",
            )
            if (!cfgs.isRelease) {
                // if not release, add test publication
                publishConfig[TEST_PUBLICATION_NAME] = "target/${cfgs.mainProjectArchiveAarName}"
            }
            (publications.getByName(MAVEN_PUBLICATION_NAME) as? MavenPublication)?.apply {
                configurePublication(this, MAVEN_PUBLICATION_NAME,
                    publishConfig[RELEASE_PUBLICATION_NAME]!!, false)
            }

            for ((publishName, artifactName) in publishConfig) {
                register(publishName, MavenPublication::class) {
                    configurePublication(this, publishName,
                        artifactName, true)
                }  // MavenPublication
            }  // for
        }  // publications
    }  // PublishingExtension

    // Register convenient task aliases
    registerPublishTaskAliases()
}

/**
 * Registers convenient task aliases for easier command usage.
 * - publishToMavenLocal -> publishAllPublicationsToMavenLocalRepository
 * - publishToMavenCustom -> publishes to ALL custom Maven repositories
 * - publishToMavenCentral -> publishAllPublicationsToMavenCentralRepository
 */
private fun Project.registerPublishTaskAliases() {
    afterEvaluate {
        // Alias for Maven Local
        val localTask = tasks.findByName("publishAllPublicationsTo${REPO_NAME_LOCAL}Repository")
        if (localTask != null) {
            tasks.register("publishTo${REPO_NAME_LOCAL}") {
                group = "publishing"
                description = "Publishes all publications to the local Maven repository"
                dependsOn(localTask)
            }
        }

        // Alias for Maven Custom - publishes to ALL custom repositories
        val customTasks = mutableListOf<Any>()
        // Find all custom repository tasks (MavenCustom0, MavenCustom1, ...)
        for (i in 0..9) {
            val task = tasks.findByName("publishAllPublicationsTo${REPO_NAME_CUSTOM}${i}Repository")
            if (task != null) {
                customTasks.add(task)
            }
        }
        if (customTasks.isNotEmpty()) {
            tasks.register("publishTo${REPO_NAME_CUSTOM}") {
                group = "publishing"
                description = "Publishes all publications to all custom Maven repositories"
                dependsOn(customTasks)
            }
        }

        // Alias for Maven Central
        val centralTask = tasks.findByName("publishAllPublicationsToMavenCentralRepository")
        if (centralTask != null) {
            tasks.register("publishToMavenCentral") {
                group = "publishing"
                description = "Publishes all publications to Maven Central"
                dependsOn(centralTask)
            }
        }
    }
}

private fun Project.configurePublication(
    mavenPublication: MavenPublication,
    publishName: String,
    artifactName: String,
    addFromComponent: Boolean = true
) {
    with(mavenPublication) {
        if (addFromComponent) {
            groupId = cfgs.commGroupId
            artifactId = getProjectArtifactId()
            if (publishName != TEST_PUBLICATION_NAME) {
                version = cfgs.versionName
            } else {
                version = "${cfgs.versionName}-TEST"
            }
            from(components["java"])
        }
        val arts = artifacts.filter {
            it.file.name.endsWith("javadoc.jar")
                || it.file.name.endsWith("sources.jar")
                || it.file.name.endsWith(".module")
        }
        components["java"].apply {
            setArtifacts(arts)
            artifact(artifactName)
        }
        setArtifacts(arts)
        artifact(artifactName)
        tasks.withType<GenerateModuleMetadata> {
            enabled = false
        }
        println("PublishName: $publishName")
        artifacts.forEach {
            println("FilteredArtifact: ${it.file.name}")
        }
        println("------------")

        // add pom
        pom { configurePom(this, artifactName, mavenPublication.version) }  // pom
    }
}

private fun Project.configurePom(config: MavenPom,
                                 artifactName: String = "",
                                 versionName: String = "") {
    with(config) {
        val gitUrl = getGitRepoUrl()
        name = cfgs.projectName
        description = "The ${cfgs.projectName} SDK"
        url = gitUrl
        version = versionName
        packaging = if (artifactName.contains(".")) artifactName.split(".").last() else "aar"

        licenses {
            license {
                name = "MIT License"
                url = "https://opensource.org/licenses/MIT"
            }
        }
        developers {
            developer {
                id = getGitRepoUserName()
                name = getGitRepoUserName()
                email = getGitRepoUserEmail()
            }
        }
        scm {
            url = gitUrl
            connection = "scm:git:${gitUrl}"
            developerConnection = "scm:git:${gitUrl}"
        }

        withXml {
            println("groupId: ${cfgs.commGroupId}")
            println("artifactId: ${getProjectArtifactId()}")
            println("version: $versionName")
            println("artifactName: $artifactName")
            println("------------")
            val commDependencies = cfgs.commDependenciesAsList
            if (commDependencies.isEmpty()) {
                return@withXml
            }
            val dependenciesNode = asNode().appendNode("dependencies")
            for (dependency in commDependencies) {
                val dependencyNode = dependenciesNode.appendNode("dependency")
                val parts = dependency.split(":")
                println("add dependency: $parts")
                dependencyNode.appendNode("groupId", parts[0])
                dependencyNode.appendNode("artifactId", parts[1])
                dependencyNode.appendNode("version", parts[2])
            }  // dependencies
        }  // withXml
    }  // MavenPom
}

/**
 * Returns configuration hint for Maven repository setup.
 */
private fun getConfigurationHint(): String {
    return """
        |[Publish-Configuration] No Maven repository configured.
        |
        |Configure via environment variables:
        |  # Local Maven repository
        |  export MAVEN_LOCAL_PATH=/path/to/local/repo
        |
        |  # Custom Maven repositories (comma-separated for multiple)
        |  export MAVEN_CUSTOM_URLS=https://maven.example.com/releases,https://maven2.example.com
        |  export MAVEN_CUSTOM_USERNAMES=user1,user2
        |  export MAVEN_CUSTOM_PASSWORDS=pass1,pass2
        |
        |Or configure via ~/.gradle/gradle.properties:
        |  mavenLocalPath=/path/to/local/repo
        |  mavenCustomUrls=https://maven.example.com/releases
        |  mavenCustomUsernames=user1
        |  mavenCustomPasswords=pass1
        |
        |Or configure via CCGO.toml:
        |  [publish.maven]
        |  local_path = "~/.m2/repository"
        |  custom_urls = "https://maven.example.com"
        |  custom_usernames = "user1"
        |  custom_passwords = "pass1"
        |
        |Available publish commands:
        |  ./gradlew publishToMavenCentral  # Publish to Maven Central
        |  ./gradlew publishToMavenLocal    # Publish to local Maven repository
        |  ./gradlew publishToMavenCustom   # Publish to custom Maven repository
    """.trimMargin()
}

/**
 * Gets the signing key from multiple sources.
 * Priority: ConfigProvider (env/gradle.properties/CCGO.toml) > local.properties legacy
 */
private fun Project.getSigningInMemoryKey(): String {
    // 1. Try ConfigProvider (env > gradle.properties > CCGO.toml)
    val signingKey = ConfigProvider.get(project, ConfigKey.SIGNING_KEY)
    if (signingKey.isNotEmpty()) {
        return signingKey
    }

    // 2. Fallback to legacy local.properties for backward compatibility
    var legacyKey = getLocalProperties("signing.key", "")
    if (legacyKey.isNotEmpty()) {
        println("[Publish] Using legacy signing.key from local.properties (consider migrating to gradle.properties)")
        return legacyKey
    }

    // 3. Try to get key from keyId (legacy)
    val signingPassword = ConfigProvider.get(project, ConfigKey.SIGNING_KEY_PASSWORD)
        .ifEmpty { getLocalProperties("signing.password", "") }
    val signingKeyId = getLocalProperties("signing.keyId", "")
    if (signingKeyId.isNotEmpty()) {
        legacyKey = getGpgKeyFromKeyId(signingKeyId, signingPassword)
        println("signing.keyId:$signingKeyId to signing.key:$legacyKey")
        if (legacyKey.isNotEmpty()) {
            return legacyKey
        }
    }

    // 4. Try to get key from keyRingFile (legacy)
    val secretKeyRingFile = getLocalProperties("signing.secretKeyRingFile", "")
    if (secretKeyRingFile.isNotEmpty()) {
        legacyKey = getGpgKeyFromKeyRingFile(secretKeyRingFile, signingPassword)
        println("signing.secretKeyRingFile:$secretKeyRingFile to signing.key:$legacyKey")
        return legacyKey
    }

    return ""
}

private fun Project.configureSourcesAndJavaDoc() {
    if (project.plugins.hasPlugin("com.android.library")) {
        val androidLibrary = project.extensions.findByType(com.android.build.api.dsl.LibraryExtension::class.java)!!
        androidLibrary.publishing {
            singleVariant(ProjectFlavor.prod.name) {
                withSourcesJar()
                withJavadocJar()
            }
        }
    } else if (project.plugins.hasPlugin("java")) {
        val javaLibrary = project.extensions.findByType(org.gradle.api.plugins.JavaPluginExtension::class.java)!!
        javaLibrary.withSourcesJar()
        javaLibrary.withJavadocJar()
    }
}

/**
 * Sets up system environment for vanniktech plugin.
 * Uses ConfigProvider to get credentials from env/gradle.properties/CCGO.toml.
 */
private fun Project.setSystemEnv() {
    val envMap = mapOf(
        "ORG_GRADLE_PROJECT_mavenCentralUsername" to ConfigProvider.get(project, ConfigKey.MAVEN_CENTRAL_USERNAME),
        "ORG_GRADLE_PROJECT_mavenCentralPassword" to ConfigProvider.get(project, ConfigKey.MAVEN_CENTRAL_PASSWORD),
        "ORG_GRADLE_PROJECT_signingInMemoryKey" to getSigningInMemoryKey(),
        "ORG_GRADLE_PROJECT_signingInMemoryKeyId" to getLocalProperties("signing.keyId", ""),
        "ORG_GRADLE_PROJECT_signingInMemoryKeyPassword" to ConfigProvider.get(project, ConfigKey.SIGNING_KEY_PASSWORD)
    )
    // detail in https://vanniktech.github.io/gradle-maven-publish-plugin/central/#configuring-the-pom
    tasks.withType(Exec::class.java).configureEach {
        // set env
        environment(envMap)
        envMap.forEach { (key, value) ->
            if (value.isNotEmpty()) {
                System.setProperty(key, value)
            }
        }
    }
}

private fun Project.getProjectArtifactId(): String {
    val combinedSuffix = arrayOf(cfgs.commPublishChannelDesc.lowercase(), cfgs.androidStlSuffix.lowercase())
        .filter { it.isNotEmpty() }
        .joinToString("-") {
            it.removePrefix("-")
        }
    val appendChar = if (combinedSuffix.isNotEmpty()) "-" else ""
    return "${cfgs.projectNameLowercase}${appendChar}${combinedSuffix}"
}

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

package com.ccgo.gradle.buildlogic.common.utils

import org.gradle.api.Project

/**
 * Configuration keys for publish settings.
 * Priority (from high to low):
 * 1. Environment Variable (for CI/CD override)
 * 2. CCGO.toml (project root)
 * 3. Project-level gradle.properties
 * 4. User-level ~/.gradle/gradle.properties
 */
enum class ConfigKey(
    val envKey: String,
    val gradleKey: String,
    val tomlPath: String
) {
    // Maven Central credentials
    MAVEN_CENTRAL_USERNAME("MAVEN_CENTRAL_USERNAME", "mavenCentralUsername", "publish.maven.central_username"),
    MAVEN_CENTRAL_PASSWORD("MAVEN_CENTRAL_PASSWORD", "mavenCentralPassword", "publish.maven.central_password"),

    // Signing credentials
    SIGNING_KEY("SIGNING_IN_MEMORY_KEY", "signingInMemoryKey", "publish.signing_key"),
    SIGNING_KEY_PASSWORD("SIGNING_IN_MEMORY_KEY_PASSWORD", "signingInMemoryKeyPassword", "publish.signing_key_password"),

    // Local maven repository path
    MAVEN_LOCAL_PATH("MAVEN_LOCAL_PATH", "mavenLocalPath", "publish.maven.local_path"),

    // Custom maven repositories (comma-separated for multiple)
    MAVEN_CUSTOM_URLS("MAVEN_CUSTOM_URLS", "mavenCustomUrls", "publish.maven.custom_urls"),
    MAVEN_CUSTOM_USERNAMES("MAVEN_CUSTOM_USERNAMES", "mavenCustomUsernames", "publish.maven.custom_usernames"),
    MAVEN_CUSTOM_PASSWORDS("MAVEN_CUSTOM_PASSWORDS", "mavenCustomPasswords", "publish.maven.custom_passwords"),

    // Sign enabled flag
    SIGN_ENABLED("SIGN_ENABLED", "signEnabled", "publish.sign_enabled")
}

/**
 * Data class representing a custom Maven repository configuration.
 */
data class CustomMavenRepo(
    val index: Int,
    val url: String,
    val username: String,
    val password: String
)

/**
 * Configuration provider that reads settings from multiple sources.
 * Priority (from high to low):
 * 1. Environment Variable (for CI/CD override)
 * 2. CCGO.toml (project root)
 * 3. Project-level gradle.properties
 * 4. User-level ~/.gradle/gradle.properties
 */
object ConfigProvider {

    /**
     * Get a single configuration value by key.
     * Priority (from high to low):
     * 1. Environment Variable (for CI/CD override)
     * 2. CCGO.toml (project root)
     * 3. Project-level gradle.properties
     * 4. User-level ~/.gradle/gradle.properties
     */
    fun get(project: Project, key: ConfigKey, defaultValue: String = ""): String {
        // 1. Try environment variable (highest priority, for CI/CD override)
        val envValue = System.getenv(key.envKey)
        if (!envValue.isNullOrBlank()) {
            println("[ConfigProvider] ${key.name} from ENV: ${key.envKey}")
            return envValue
        }

        // 2. Try CCGO.toml (project root)
        val tomlValue = TomlConfigReader.getValue(project, key.tomlPath)
        if (!tomlValue.isNullOrBlank()) {
            println("[ConfigProvider] ${key.name} from CCGO.toml: ${key.tomlPath}")
            return tomlValue
        }

        // 3. Try project-level gradle.properties
        val projectPropsFile = project.rootProject.file("gradle.properties")
        if (projectPropsFile.exists()) {
            val projectProps = java.util.Properties()
            projectPropsFile.inputStream().use { projectProps.load(it) }
            val projectValue = projectProps.getProperty(key.gradleKey)
            if (!projectValue.isNullOrBlank()) {
                println("[ConfigProvider] ${key.name} from project gradle.properties: ${key.gradleKey}")
                return projectValue
            }
        }

        // 4. Try user-level ~/.gradle/gradle.properties (lowest priority)
        val userGradlePropsFile = java.io.File(System.getProperty("user.home"), ".gradle/gradle.properties")
        if (userGradlePropsFile.exists()) {
            val userProps = java.util.Properties()
            userGradlePropsFile.inputStream().use { userProps.load(it) }
            val userValue = userProps.getProperty(key.gradleKey)
            if (!userValue.isNullOrBlank()) {
                println("[ConfigProvider] ${key.name} from user gradle.properties: ${key.gradleKey}")
                return userValue
            }
        }

        if (defaultValue.isNotEmpty()) {
            println("[ConfigProvider] ${key.name} using default: $defaultValue")
        }
        return defaultValue
    }

    /**
     * Get boolean configuration value.
     */
    fun getBoolean(project: Project, key: ConfigKey, defaultValue: Boolean = false): Boolean {
        val value = get(project, key, defaultValue.toString())
        return value.lowercase() in listOf("true", "1", "yes", "on")
    }

    /**
     * Get all custom Maven repository configurations.
     * URLs, usernames, and passwords are comma-separated and correspond by index.
     */
    fun getCustomMavenRepos(project: Project): List<CustomMavenRepo> {
        val urls = get(project, ConfigKey.MAVEN_CUSTOM_URLS)
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (urls.isEmpty()) {
            // Try to get from CCGO.toml array format
            return TomlConfigReader.getCustomMavenRepos(project)
        }

        val usernames = get(project, ConfigKey.MAVEN_CUSTOM_USERNAMES)
            .split(",")
            .map { it.trim() }

        val passwords = get(project, ConfigKey.MAVEN_CUSTOM_PASSWORDS)
            .split(",")
            .map { it.trim() }

        return urls.mapIndexed { index, url ->
            CustomMavenRepo(
                index = index,
                url = url,
                username = usernames.getOrElse(index) { "" },
                password = passwords.getOrElse(index) { "" }
            )
        }
    }

    /**
     * Get local Maven repository path, expanding ~ to user home.
     */
    fun getLocalMavenPath(project: Project): String? {
        val path = get(project, ConfigKey.MAVEN_LOCAL_PATH)
        if (path.isBlank()) return null
        return path.expandTilde()
    }

    /**
     * Expand ~ to user home directory.
     */
    private fun String.expandTilde(): String {
        return if (this.startsWith("~")) {
            this.replaceFirst("~", System.getProperty("user.home"))
        } else {
            this
        }
    }
}

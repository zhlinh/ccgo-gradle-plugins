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
import org.tomlj.Toml
import org.tomlj.TomlParseResult
import org.tomlj.TomlTable
import java.io.File

/**
 * Reader for CCGO.toml configuration file.
 * Caches the parsed TOML for efficiency.
 */
object TomlConfigReader {

    private var cachedToml: TomlParseResult? = null
    private var cachedPath: String? = null

    /**
     * Get a value from CCGO.toml by dot-separated path.
     * Example: "publish.maven.central_username"
     */
    fun getValue(project: Project, path: String): String? {
        val toml = getToml(project) ?: return null
        return try {
            getValueByPath(toml, path)
        } catch (e: Exception) {
            println("[TomlConfigReader] Error reading path '$path': ${e.message}")
            null
        }
    }

    /**
     * Get boolean value from CCGO.toml.
     */
    fun getBoolean(project: Project, path: String, defaultValue: Boolean = false): Boolean {
        val toml = getToml(project) ?: return defaultValue
        return try {
            val value = getValueByPath(toml, path)
            value?.lowercase() in listOf("true", "1", "yes", "on")
        } catch (e: Exception) {
            defaultValue
        }
    }

    /**
     * Get custom Maven repositories from CCGO.toml array format.
     * Reads [[publish.maven.custom]] array entries.
     */
    fun getCustomMavenRepos(project: Project): List<CustomMavenRepo> {
        val toml = getToml(project) ?: return emptyList()
        val repos = mutableListOf<CustomMavenRepo>()

        try {
            val publishTable = toml.getTable("publish") ?: return emptyList()
            val mavenTable = publishTable.getTable("maven") ?: return emptyList()
            val customArray = mavenTable.getArray("custom") ?: return emptyList()

            for (i in 0 until customArray.size()) {
                val repoTable = customArray.getTable(i)
                val url = repoTable.getString("url") ?: continue
                repos.add(
                    CustomMavenRepo(
                        index = i,
                        url = url,
                        username = repoTable.getString("username") ?: "",
                        password = repoTable.getString("password") ?: ""
                    )
                )
            }
        } catch (e: Exception) {
            println("[TomlConfigReader] Error reading custom maven repos: ${e.message}")
        }

        return repos
    }

    /**
     * Parse and cache the CCGO.toml file.
     */
    private fun getToml(project: Project): TomlParseResult? {
        val tomlFile = findTomlFile(project)
        if (tomlFile == null) {
            println("[TomlConfigReader] CCGO.toml not found")
            return null
        }

        val path = tomlFile.absolutePath
        if (cachedPath == path && cachedToml != null) {
            return cachedToml
        }

        return try {
            println("[TomlConfigReader] Loading CCGO.toml from: $path")
            val result = Toml.parse(tomlFile.toPath())
            if (result.hasErrors()) {
                result.errors().forEach { error ->
                    println("[TomlConfigReader] Parse error: $error")
                }
                null
            } else {
                cachedToml = result
                cachedPath = path
                result
            }
        } catch (e: Exception) {
            println("[TomlConfigReader] Error parsing CCGO.toml: ${e.message}")
            null
        }
    }

    /**
     * Find CCGO.toml file, searching from project root upwards.
     */
    private fun findTomlFile(project: Project): File? {
        // First check project root
        var dir: File? = project.rootDir
        while (dir != null) {
            val tomlFile = File(dir, "CCGO.toml")
            if (tomlFile.exists()) {
                return tomlFile
            }
            // Check parent directory (for nested projects)
            dir = dir.parentFile
        }
        return null
    }

    /**
     * Get value by dot-separated path from TOML.
     */
    private fun getValueByPath(toml: TomlParseResult, path: String): String? {
        val parts = path.split(".")
        if (parts.isEmpty()) return null

        var current: Any? = toml
        for (i in 0 until parts.size - 1) {
            current = when (current) {
                is TomlParseResult -> current.getTable(parts[i])
                is TomlTable -> current.getTable(parts[i])
                else -> null
            }
            if (current == null) return null
        }

        val lastKey = parts.last()
        return when (current) {
            is TomlParseResult -> current.getString(lastKey)
            is TomlTable -> current.getString(lastKey)
            else -> null
        }
    }

    /**
     * Clear the cache (useful for testing).
     */
    fun clearCache() {
        cachedToml = null
        cachedPath = null
    }
}

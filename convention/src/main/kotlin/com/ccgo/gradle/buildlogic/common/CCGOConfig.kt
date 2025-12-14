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

import org.gradle.api.Project
import org.tomlj.Toml
import org.tomlj.TomlParseResult
import java.io.File

/**
 * Configuration reader for CCGO.toml file.
 *
 * CCGO.toml is the main configuration file for CCGO projects, similar to Cargo.toml for Rust.
 * It contains project metadata, build settings, and publish configuration.
 *
 * The CCGO.toml file should be placed in the parent directory of the android/kmp project.
 * For example:
 * - projectRoot/
 *   - CCGO.toml
 *   - android/
 *     - build.gradle.kts
 *     - gradle/libs.versions.toml
 */
class CCGOConfig(private val project: Project) {
    companion object {
        private var ccgoConfig: CCGOConfig? = null

        fun getDefault(project: Project): CCGOConfig {
            return ccgoConfig ?: CCGOConfig(project).also {
                ccgoConfig = it
            }
        }

        // Reset for testing
        fun reset() {
            ccgoConfig = null
        }
    }

    private val tomlResult: TomlParseResult? by lazy {
        findAndParseCCGOToml()
    }

    /**
     * Find CCGO.toml file by searching up from the project root directory.
     * Looks for CCGO.toml in parent directories.
     */
    private fun findAndParseCCGOToml(): TomlParseResult? {
        var searchDir: File? = project.rootDir

        // Search up to 3 levels up from the project root
        for (i in 0..3) {
            if (searchDir == null) break

            val ccgoToml = File(searchDir, "CCGO.toml")
            if (ccgoToml.exists() && ccgoToml.isFile) {
                println("[CCGOConfig] Found CCGO.toml at: ${ccgoToml.absolutePath}")
                return try {
                    val result = Toml.parse(ccgoToml.toPath())
                    if (result.hasErrors()) {
                        println("[CCGOConfig] WARNING: CCGO.toml has parse errors:")
                        result.errors().forEach { error ->
                            println("[CCGOConfig]   ${error.toString()}")
                        }
                    }
                    result
                } catch (e: Exception) {
                    println("[CCGOConfig] ERROR: Failed to parse CCGO.toml: ${e.message}")
                    null
                }
            }
            searchDir = searchDir.parentFile
        }

        println("[CCGOConfig] WARNING: CCGO.toml not found. Using default values.")
        return null
    }

    /**
     * Project version (e.g., "1.0.0")
     * Read from [project].version in CCGO.toml
     */
    val version: String by lazy {
        tomlResult?.getString("project.version") ?: "1.0.0"
    }

    /**
     * Project group ID for Maven publishing (e.g., "com.example.project")
     * Read from [publish.maven].group_id in CCGO.toml
     */
    val groupId: String by lazy {
        tomlResult?.getString("publish.maven.group_id") ?: "com.example"
    }

    /**
     * Android STL type (e.g., "c++_shared" or "c++_static")
     * Read from [android].stl in CCGO.toml
     */
    val androidStl: String by lazy {
        tomlResult?.getString("android.stl") ?: "c++_shared"
    }

    /**
     * Android compile SDK version (e.g., 34)
     * Read from [android].compile_sdk in CCGO.toml
     */
    val compileSdk: Int by lazy {
        tomlResult?.getLong("android.compile_sdk")?.toInt() ?: 34
    }

    /**
     * Android build tools version (e.g., "34.0.0")
     * Read from [android].build_tools in CCGO.toml
     */
    val buildTools: String by lazy {
        tomlResult?.getString("android.build_tools") ?: "34.0.0"
    }

    /**
     * Android minimum SDK version (e.g., 19)
     * Read from [android].min_sdk in CCGO.toml
     */
    val minSdk: Int by lazy {
        tomlResult?.getLong("android.min_sdk")?.toInt() ?: 19
    }

    /**
     * Android app minimum SDK version (e.g., 21)
     * Read from [android].app_min_sdk in CCGO.toml
     */
    val appMinSdk: Int by lazy {
        tomlResult?.getLong("android.app_min_sdk")?.toInt() ?: 21
    }

    /**
     * Android target SDK version (e.g., 34)
     * Read from [android].target_sdk in CCGO.toml
     */
    val targetSdk: Int by lazy {
        tomlResult?.getLong("android.target_sdk")?.toInt() ?: 34
    }

    /**
     * Android NDK version (e.g., "25.2.9519653")
     * Read from [android].ndk_version in CCGO.toml
     */
    val ndkVersion: String by lazy {
        tomlResult?.getString("android.ndk_version") ?: "25.2.9519653"
    }

    /**
     * CMake version (e.g., "3.22.1")
     * Read from [build].cmake_version in CCGO.toml
     */
    val cmakeVersion: String by lazy {
        tomlResult?.getString("build.cmake_version") ?: "3.22.1"
    }

    /**
     * CMake ABI filters (e.g., ["armeabi-v7a", "arm64-v8a", "x86_64"])
     * Read from [android].default_archs in CCGO.toml
     */
    val cmakeAbiFilters: List<String> by lazy {
        tomlResult?.getArray("android.default_archs")
            ?.toList()
            ?.mapNotNull { it as? String }
            ?.filter { it.isNotBlank() }
            ?: listOf("armeabi-v7a", "arm64-v8a", "x86_64")
    }

    /**
     * CMake ABI filters as a comma-separated string
     */
    val cmakeAbiFiltersAsString: String by lazy {
        cmakeAbiFilters.joinToString(",")
    }

    /**
     * Project name (e.g., "ccgonow")
     * Read from [project].name in CCGO.toml
     */
    val projectName: String by lazy {
        tomlResult?.getString("project.name") ?: ""
    }

    /**
     * Maven artifact ID for publishing (e.g., "ccgonow")
     * Read from [publish.maven].artifact_id in CCGO.toml
     * Defaults to project name if not specified
     */
    val artifactId: String by lazy {
        tomlResult?.getString("publish.maven.artifact_id")
            ?.takeIf { it.isNotBlank() }
            ?: projectName
    }

    /**
     * Publish channel description (e.g., "beta", "release", "")
     * Read from [publish.maven].channel_desc in CCGO.toml
     * Falls back to [publish].channel_desc for backward compatibility
     */
    val publishChannelDesc: String by lazy {
        // Try new location first, then fallback to old location
        tomlResult?.getString("publish.maven.channel_desc")?.takeUnless { it == "EMPTY" }
            ?: tomlResult?.getString("publish.channel_desc")?.takeUnless { it == "EMPTY" }
            ?: ""
    }

    /**
     * Maven dependencies for POM generation (e.g., ["com.example:lib:1.0.0"])
     * Read from [publish.maven].dependencies in CCGO.toml
     */
    val mavenDependencies: List<String> by lazy {
        tomlResult?.getArray("publish.maven.dependencies")
            ?.toList()
            ?.mapNotNull { it as? String }
            ?.filter { it.isNotBlank() && it.contains(":") }
            ?: emptyList()
    }

    /**
     * Maven dependencies as a comma-separated string
     */
    val mavenDependenciesAsString: String by lazy {
        mavenDependencies.joinToString(",")
    }

    /**
     * Check if CCGO.toml was found and loaded successfully
     */
    val isLoaded: Boolean
        get() = tomlResult != null

    /**
     * Print configuration summary
     */
    fun print() {
        println("===================CCGO Config===================")
        println("CCGO.toml loaded: $isLoaded")
        println("version: $version")
        println("groupId: $groupId")
        println("androidStl: $androidStl")
        println("publishChannelDesc: $publishChannelDesc")
        println("mavenDependencies: $mavenDependencies")
        println("=================================================")
    }
}

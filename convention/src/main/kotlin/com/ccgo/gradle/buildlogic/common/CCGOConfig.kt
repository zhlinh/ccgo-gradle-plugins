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
     * Falls back to [publish.android.maven].group_id or [project].group_id
     *
     * The fallback chain matters: `ccgo` generates `[publish.android.maven]`, not
     * `[publish.maven]`, so without it every generated project silently published
     * under the "com.example" placeholder. kmpGroupId below already had the chain.
     */
    val groupId: String by lazy {
        tomlResult?.getString("publish.maven.group_id")
            ?: tomlResult?.getString("publish.android.maven.group_id")
            ?: tomlResult?.getString("project.group_id")
            ?: "com.example"
    }

    /**
     * Android STL type (e.g., "c++_shared" or "c++_static")
     * Read from [android].stl in CCGO.toml
     */
    val androidStl: String by lazy {
        // CCGO_ANDROID_STL is what `ccgo build android --stl ...` just handed CMake.
        // It has to win here: this value decides the `-stdembed` artifact name, and a
        // name that disagrees with the runtime the .so actually links is worse than
        // either choice on its own.
        System.getenv("CCGO_ANDROID_STL")?.takeIf { it.isNotBlank() }
            ?: tomlResult?.getString("android.stl")
            ?: "c++_shared"
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
            ?: tomlResult?.getString("publish.android.maven.artifact_id")
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
            ?: tomlResult?.getString("publish.android.maven.channel_desc")?.takeUnless { it == "EMPTY" }
            ?: tomlResult?.getString("publish.channel_desc")?.takeUnless { it == "EMPTY" }
            ?: ""
    }

    /**
     * Maven dependencies for POM generation (e.g., ["com.example:lib:1.0.0"])
     * Read from [publish.maven].dependencies in CCGO.toml
     */
    val mavenDependencies: List<String> by lazy {
        (tomlResult?.getArray("publish.maven.dependencies")
            ?: tomlResult?.getArray("publish.android.maven.dependencies"))
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

    // =========================================================================
    // KMP (Kotlin Multiplatform) Configuration
    // =========================================================================

    /**
     * KMP group ID for Maven publishing
     * Read from [publish.kmp.maven].group_id in CCGO.toml — the schema is
     * [publish.<target>.<registry>], which is why apple can carry both
     * [publish.apple.cocoapods] and [publish.apple.spm].
     * Falls back to [publish.kmp].group_id (the older spelling), then to
     * [publish.android.maven].group_id or [project].group_id
     */
    val kmpGroupId: String by lazy {
        tomlResult?.getString("publish.kmp.maven.group_id")
            ?: tomlResult?.getString("publish.kmp.group_id")
            ?: tomlResult?.getString("publish.android.maven.group_id")
            ?: tomlResult?.getString("project.group_id")
            ?: groupId
    }

    /**
     * KMP artifact ID for publishing
     * Read from [publish.kmp.maven].artifact_id in CCGO.toml
     * Falls back to [publish.kmp].artifact_id (the older spelling)
     * Defaults to project name + "-kmp" if not specified
     */
    val kmpArtifactId: String by lazy {
        tomlResult?.getString("publish.kmp.maven.artifact_id")
            ?.takeIf { it.isNotBlank() }
            ?: tomlResult?.getString("publish.kmp.artifact_id")
            ?.takeIf { it.isNotBlank() }
            ?: "${projectName}-kmp"
    }

    /**
     * Data class representing a KMP dependency
     */
    data class KmpDependency(
        val group: String,
        val artifact: String,
        val version: String
    ) {
        fun toMavenCoordinate(): String = "$group:$artifact:$version"
    }

    /**
     * KMP dependencies for commonMain sourceSet
     * Read from [publish.kmp.maven].dependencies in CCGO.toml, matching the
     * [publish.android.maven].dependencies spelling. [publish.kmp].dependencies
     * still works — the maven-coordinate keys moved under .maven, while
     * android_min_sdk and ios_deployment_target stay on [publish.kmp] because
     * they are build settings, not publication coordinates.
     * Each dependency should have group, artifact, and version fields
     */
    val kmpDependencies: List<KmpDependency> by lazy {
        (tomlResult?.getArray("publish.kmp.maven.dependencies")
            ?: tomlResult?.getArray("publish.kmp.dependencies"))
            ?.toList()
            ?.mapNotNull { item ->
                val table = item as? org.tomlj.TomlTable ?: return@mapNotNull null
                val group = table.getString("group") ?: return@mapNotNull null
                val artifact = table.getString("artifact") ?: return@mapNotNull null
                val version = table.getString("version") ?: return@mapNotNull null
                KmpDependency(group, artifact, version)
            }
            ?: emptyList()
    }

    /**
     * KMP dependencies as Maven coordinates list
     */
    val kmpDependenciesAsList: List<String> by lazy {
        kmpDependencies.map { it.toMavenCoordinate() }
    }

    /**
     * KMP Android minSdk (can be different from native Android lib)
     * Read from [publish.kmp].android_min_sdk in CCGO.toml
     * Falls back to [android].min_sdk
     */
    val kmpAndroidMinSdk: Int by lazy {
        tomlResult?.getLong("publish.kmp.android_min_sdk")?.toInt() ?: minSdk.coerceAtLeast(24)
    }

    /**
     * KMP iOS deployment target
     * Read from [publish.kmp].ios_deployment_target in CCGO.toml
     */
    val kmpIosDeploymentTarget: String by lazy {
        tomlResult?.getString("publish.kmp.ios_deployment_target") ?: "14.0"
    }

    /**
     * Git repository URL
     * Read from [project].repository in CCGO.toml
     */
    val repositoryUrl: String by lazy {
        tomlResult?.getString("project.repository") ?: ""
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
        println("projectName: $projectName")
        println("groupId: $groupId")
        println("androidStl: $androidStl")
        println("publishChannelDesc: $publishChannelDesc")
        println("mavenDependencies: $mavenDependencies")
        println("--- KMP Config ---")
        println("kmpGroupId: $kmpGroupId")
        println("kmpArtifactId: $kmpArtifactId")
        println("kmpDependencies: $kmpDependenciesAsList")
        println("kmpAndroidMinSdk: $kmpAndroidMinSdk")
        println("kmpIosDeploymentTarget: $kmpIosDeploymentTarget")
        println("=================================================")
    }
}

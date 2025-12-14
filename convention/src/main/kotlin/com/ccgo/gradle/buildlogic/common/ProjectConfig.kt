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

import com.ccgo.gradle.buildlogic.common.utils.getCurrentTag
import com.ccgo.gradle.buildlogic.common.utils.getGitBranchName
import com.ccgo.gradle.buildlogic.common.utils.getGitHeadTimeInfo
import com.ccgo.gradle.buildlogic.common.utils.getGitRevision
import com.ccgo.gradle.buildlogic.common.utils.getGitVersionCode
import com.ccgo.gradle.buildlogic.common.utils.getPublishSuffix
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.configurationcache.extensions.capitalized
import org.gradle.kotlin.dsl.extra

/**
 * Project config
 *
 * Configuration is read from CCGO.toml with fallback to libs.versions.toml for backward compatibility.
 * Priority: CCGO.toml > libs.versions.toml
 */
class ProjectConfig(val project: Project) {
    companion object {
        private var projectConfig: ProjectConfig? = null
        fun getDefault(project: Project): ProjectConfig {
            return projectConfig ?: ProjectConfig(project).also {
                projectConfig = it
                it.print()
            }
        }
    }

    // CCGO.toml configuration reader
    private val ccgoConfig: CCGOConfig = CCGOConfig.getDefault(project)

    val mainCommProject
        get(): Project = project.rootProject.subprojects.find { it.name.startsWith("main") && !it.name.startsWith("empty") }!!
    val javaCompatibilityVersion = JavaVersion.VERSION_11
    val gradlePluginJavaCompatibilityVersion = JavaVersion.VERSION_17

    // Read from CCGO.toml [project].version, fallback to libs.versions.toml
    val versionName: String = if (ccgoConfig.isLoaded) {
        ccgoConfig.version
    } else {
        project.libs.findVersion("commMainProject").get().toString()
    }

    /**
     * Determines if this is a release build.
     * Priority (high to low):
     * 1. Gradle property: -PisRelease=true
     * 2. Environment variable: CCGO_CI_BUILD_IS_RELEASE=true
     * 3. Default: false (beta/debug build)
     */
    val isRelease: Boolean = run {
        // 1. Check Gradle property first (highest priority)
        val gradleProp = project.findProperty("isRelease")?.toString()
        if (gradleProp != null) {
            println("[Config] isRelease from Gradle property (-PisRelease): $gradleProp")
            return@run gradleProp.toBoolean()
        }

        // 2. Check environment variable
        val envVar = System.getenv("CCGO_CI_BUILD_IS_RELEASE")
        if (envVar != null) {
            println("[Config] isRelease from environment variable (CCGO_CI_BUILD_IS_RELEASE): $envVar")
            return@run envVar.toBoolean()
        }

        // 3. Default to false (beta/debug build)
        println("[Config] isRelease defaults to false (use -PisRelease=true or buildAARRelease for release)")
        false
    }

    // Read from CCGO.toml [project].group_id, fallback to libs.versions.toml
    val commGroupId: String = if (ccgoConfig.isLoaded) {
        ccgoConfig.groupId
    } else {
        project.libs.findVersion("commGroupId").get().toString()
    }

    // Read from CCGO.toml [publish.maven].channel_desc, fallback to libs.versions.toml
    val commPublishChannelDesc: String = if (ccgoConfig.isLoaded) {
        ccgoConfig.publishChannelDesc
    } else {
        project.libs.findVersion("commPublishChannelDesc").get().toString()
            .takeUnless { it == "EMPTY" }
            ?: ""
    }

    // Read from CCGO.toml [publish.maven].artifact_id, defaults to project name
    val mavenArtifactId: String = if (ccgoConfig.isLoaded && ccgoConfig.artifactId.isNotBlank()) {
        ccgoConfig.artifactId
    } else {
        project.rootProject.name
    }

    // Read from CCGO.toml [android].stl, fallback to libs.versions.toml
    val commAndroidStl: String = if (ccgoConfig.isLoaded) {
        ccgoConfig.androidStl
    } else {
        project.libs.findVersion("commAndroidStl").get().toString()
    }
    private val commAndroidStlIsStatic: Boolean = commAndroidStl.endsWith("_static")
    val androidStlSuffix = if (commAndroidStlIsStatic) "-stdembed" else ""

    // Read from CCGO.toml [publish.maven].dependencies, fallback to libs.versions.toml
    val commDependencies: String = if (ccgoConfig.isLoaded) {
        ccgoConfig.mavenDependenciesAsString
    } else {
        project.libs.findVersion("commDependencies").get().toString().replace("\\s".toRegex(), "")
    }
    // remove blanks
    val commDependenciesAsList: List<String> = if (ccgoConfig.isLoaded) {
        ccgoConfig.mavenDependencies
    } else {
        commDependencies.split(",")
            .filter {
                it.isNotBlank() && it != "EMPTY" && it.contains(":")
            }
    }
    // commIsSignEnabled removed - signing is now auto-detected based on available credentials
    val versionCode: String by lazy { getGitVersionCode() }
    val revision: String by lazy { getGitRevision() }
    val branchName: String by lazy { getGitBranchName() }
    val timeInfo: String by lazy { getGitHeadTimeInfo() }
    val publishSuffix: String by lazy { getPublishSuffix(isRelease) }
    val currentTag: String = getCurrentTag(isRelease, versionName, publishSuffix)
    // Target subdirectory: "release" for release builds, "debug" for debug/beta builds
    val targetSubDir: String = if (isRelease) "release" else "debug"
    val projectName: String = project.rootProject.name
    val projectNameUppercase: String = projectName.uppercase()
    val projectNameLowercase: String = projectName.lowercase()
    val mainProjectArchiveAarName = getMainArchiveAarName(publishSuffix)
    val mainProjectArchiveZipName = "ARCHIVE_${mainProjectArchiveAarName.removeSuffix(".aar")}.zip"

    // flavor prod
    val mainProjectAssembleProdTaskName = "assemble${ProjectFlavor.prod.name.capitalized()}Release"
    val mainProjectMergeProdJniTaskName = "merge${ProjectFlavor.prod.name.capitalized()}ReleaseJniLibFolders"

    // flavor debug
    val mainProjectAssembleDemoTaskName = "assemble${ProjectFlavor.demo.name.capitalized()}Release"
    val mainProjectMergeDemoJniTaskName = "merge${ProjectFlavor.demo.name.capitalized()}ReleaseJniLibFolders"

    // flavor debug
    // all flavor
    val mainProjectAssembleAllTaskName = "assembleRelease"
    val mainProjectMergeJniTaskName = "mergeReleaseJniLibFolders"

    // Read from CCGO.toml [android].compile_sdk, fallback to libs.versions.toml
    val compileSdkVersion: Int = if (ccgoConfig.isLoaded) {
        ccgoConfig.compileSdk
    } else {
        project.libs.findVersion("compileSdkVersion").get().toString().toInt()
    }

    // Read from CCGO.toml [android].build_tools, fallback to libs.versions.toml
    val buildToolsVersion: String = if (ccgoConfig.isLoaded) {
        ccgoConfig.buildTools
    } else {
        project.libs.findVersion("buildToolsVersion").get().toString()
    }

    // Read from CCGO.toml [android].min_sdk, fallback to libs.versions.toml
    val minSdkVersion: Int = if (ccgoConfig.isLoaded) {
        ccgoConfig.minSdk
    } else {
        project.libs.findVersion("minSdkVersion").get().toString().toInt()
    }

    // Read from CCGO.toml [android].app_min_sdk, fallback to libs.versions.toml
    val appMinSdkVersion: Int = if (ccgoConfig.isLoaded) {
        ccgoConfig.appMinSdk
    } else {
        project.libs.findVersion("appMinSdkVersion").get().toString().toInt()
    }

    // Read from CCGO.toml [android].target_sdk, fallback to libs.versions.toml
    val targetSdkVersion: Int = if (ccgoConfig.isLoaded) {
        ccgoConfig.targetSdk
    } else {
        project.libs.findVersion("targetSdkVersion").get().toString().toInt()
    }

    // Read from CCGO.toml [android].default_archs, fallback to libs.versions.toml
    val cmakeAbiFilters: String = if (ccgoConfig.isLoaded) {
        ccgoConfig.cmakeAbiFiltersAsString
    } else {
        project.libs.findVersion("cmakeAbiFilters").get().toString().replace("\\s".toRegex(), "")
    }
    val cmakeAbiFiltersAsList: List<String> = if (ccgoConfig.isLoaded) {
        ccgoConfig.cmakeAbiFilters
    } else {
        cmakeAbiFilters.split(",").filter { it.isNotBlank() && it != "EMPTY" }
    }

    // Read from CCGO.toml [build].cmake_version, fallback to libs.versions.toml
    val cmakeVersion: String = if (ccgoConfig.isLoaded) {
        ccgoConfig.cmakeVersion
    } else {
        project.libs.findVersion("cmakeVersion").get().toString()
    }

    // Read from CCGO.toml [android].ndk_version, fallback to libs.versions.toml
    val ndkVersion: String = if (ccgoConfig.isLoaded) {
        ccgoConfig.ndkVersion
    } else {
        project.libs.findVersion("ndkVersion").get().toString()
    }
    val ndkPath: String = System.getenv("NDK_ROOT") ?: ""
    val taskPrintPrefixFilters = listOf("assemble", "bundle", "publish", "merge")

    fun getMainArchiveAarName(inputSuffix: String): String {
        val combinedSuffix = arrayOf(commPublishChannelDesc.lowercase(), inputSuffix.lowercase())
            .filter { it.isNotEmpty() }
            .joinToString("-") {
                it.removePrefix("-")
            }
        val appendChar = if (combinedSuffix.isNotEmpty()) "-" else ""
        return "${projectNameUppercase}_ANDROID${androidStlSuffix.uppercase()}_SDK-${versionName}${appendChar}${combinedSuffix}.aar"
    }

    fun print() {
        val configSource = if (ccgoConfig.isLoaded) "CCGO.toml" else "libs.versions.toml"
        println("===============CCGO Build System=================")
        println("Config source: $configSource")
        println("TASKS (which can be executed by './gradlew :taskName')")
        println(":archiveProject                           - Archive the project")
        println(":publishMainPublicationToMavenRepository  - Publish Release only to maven, set config in local.properties first")
        println(":publishTestPublicationToMavenRepository  - Publish Current(Release or Beta) to maven, set config in local.properties first")
        println(":pushSo                                   - Push so files to device")
        println(":rmSo                                     - Remove so files from device")
        println(":tagGit                                   - Make a tag to git")
        println(":tasks                                    - Show all the tasks names")
        println(":printModulePaths                         - Print all the module paths")
        println("===========================================")
        println("$projectName versionName:${versionName}")
        println("$projectName groupId:${commGroupId}")
        println("$projectName versionCode:${versionCode}")
        println("$projectName revision:${revision}")
        println("$projectName branchName:${branchName}")
        println("$projectName timeInfo:${timeInfo}")
        println("$projectName isRelease:${isRelease}")
        println("$projectName publishSuffix:${publishSuffix}")
        println("$projectName currentTag:${currentTag}")
        println("$projectName androidStl:${commAndroidStl}")
        println("$projectName dependencies:${commDependencies}")
        println("===========================================")
    }
}


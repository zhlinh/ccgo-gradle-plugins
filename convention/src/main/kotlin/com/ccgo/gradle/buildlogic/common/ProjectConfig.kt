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

    val mainCommProject
        get(): Project = project.rootProject.subprojects.find { it.name.startsWith("main") && !it.name.startsWith("empty") }!!
    val javaCompatibilityVersion = JavaVersion.VERSION_11
    val gradlePluginJavaCompatibilityVersion = JavaVersion.VERSION_17
    val versionName: String = project.libs.findVersion("commMainProject").get().toString()

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
    val commGroupId: String = project.libs.findVersion("commGroupId").get().toString()
    val commPublishChannelDesc: String = project.libs.findVersion("commPublishChannelDesc").get().toString()
        .takeUnless { it == "EMPTY" }
        ?: ""
    val commAndroidStl: String = project.libs.findVersion("commAndroidStl").get().toString()
    private val commAndroidStlIsStatic: Boolean = commAndroidStl.endsWith("_static")
    val androidStlSuffix = if (commAndroidStlIsStatic) "-stdembed" else ""
    val commDependencies: String =
        project.libs.findVersion("commDependencies").get().toString().replace("\\s".toRegex(), "")
    // remove blanks
    val commDependenciesAsList: List<String> = commDependencies.split(",")
            .filter {
                it.isNotBlank() && it != "EMPTY" && it.contains(":")
            }
    val commIsSignEnabled: Boolean = project.libs.findVersion("commIsSignEnabled").get().toString().toBoolean()
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

    val compileSdkVersion: Int = project.libs.findVersion("compileSdkVersion").get().toString().toInt()
    val buildToolsVersion: String = project.libs.findVersion("buildToolsVersion").get().toString()
    val minSdkVersion: Int = project.libs.findVersion("minSdkVersion").get().toString().toInt()
    val appMinSdkVersion: Int = project.libs.findVersion("appMinSdkVersion").get().toString().toInt()
    val targetSdkVersion: Int = project.libs.findVersion("targetSdkVersion").get().toString().toInt()

    // remove blanks
    val cmakeAbiFilters: String =
        project.libs.findVersion("cmakeAbiFilters").get().toString().replace("\\s".toRegex(), "")
    val cmakeAbiFiltersAsList: List<String> = cmakeAbiFilters.split(",")
            .filter { it.isNotBlank() && it != "EMPTY" }
    val cmakeVersion: String = project.libs.findVersion("cmakeVersion").get().toString()

    //  ndkVersion is r25c
    val ndkVersion: String = project.libs.findVersion("ndkVersion").get().toString()
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
        println("===============CCGO Build System=================")
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


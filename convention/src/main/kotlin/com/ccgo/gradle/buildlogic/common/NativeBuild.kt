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

import com.android.build.gradle.internal.tasks.factory.dependsOn
import org.apache.tools.ant.taskdefs.condition.Os
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import com.ccgo.gradle.buildlogic.common.utils.checkExecResult
import com.ccgo.gradle.buildlogic.common.utils.generateLocalProperties
import com.ccgo.gradle.buildlogic.common.utils.getLocalProperties
import org.gradle.kotlin.dsl.configure
import org.gradle.api.Project
import org.gradle.api.tasks.Exec
import org.gradle.kotlin.dsl.register


const val projectNameMain: String = "Main"

/**
 * Configures the root project for native builds.
 */
internal fun Project.configureRootNativeBuild() {
    generateLocalProperties()
}

/**
 * Configures the sub project for native builds by python.
 *
 * NOTE: the function name should not be too long, otherwise it will has not found error
 */
internal fun Project.configureSubNativeBuildPython() {
    createBuildLibrariesTask(projectNameMain)
    extensions.configure<LibraryExtension> {
        configureSubNativeBuildBase(this)
    }

    project.afterEvaluate {
        // taskProvider get from named function needs to include com.android.build.gradle.internal.tasks.factory.dependsOn
        // cleanTheTargetDir -> buildLibrariesForMain
        // -> mergeProdReleaseJniLibFolders -> assembleProdRelease
        val buildLibrariesForMain = tasks.named("buildLibrariesForMain")
        // buildLibrariesForMain will generate the libs dir including libs
        // for mergeProdReleaseJniLibFolders
        val mergeProdReleaseJniLibFolders = tasks.named(cfgs.mainProjectMergeProdJniTaskName)
        val cleanTheTargetDir = tasks.named("cleanTheTargetDir")
        buildLibrariesForMain.dependsOn(cleanTheTargetDir)

        mergeProdReleaseJniLibFolders.dependsOn(buildLibrariesForMain)
        mergeProdReleaseJniLibFolders.get().mustRunAfter(buildLibrariesForMain.get())
    }
}

/**
 * Configures the sub project for native builds by cmake.
 *
 * NOTE: the function name should not be too long, otherwise it will has not found error
 */
internal fun Project.configureSubNativeBuildCmake() {
    extensions.configure<LibraryExtension> {
        configureSubNativeBuildBase(this)
        externalNativeBuild {
            cmake {
                path("${rootDir.parentFile}/CMakeLists.txt")
            }
        }
    }
}

internal fun Project.configureSubNativeBuildBase(
    commonExtension: CommonExtension<*, *, *, *, *, *>
) {
    commonExtension.apply {
        defaultConfig {
            ndkVersion = cfgs.ndkVersion
            ndkPath = cfgs.ndkPath

            println("The ndkVersion:${ndkVersion}")
            println("The ndkPath:${ndkPath}")
            println("The cmakeVersion:${cfgs.cmakeVersion}")
            println("-------------------------------------------")
            externalNativeBuild {
                cmake {
                    cppFlags("-fpic", "-frtti", "-fexceptions", "-Wall")
                    version = cfgs.cmakeVersion
                    arguments("-GNinja") // to generate android_gradle_build.json
                    arguments("-DANDROID_PLATFORM=android-${cfgs.minSdkVersion}") // ndk platform
                    arguments("-DANDROID_TOOLCHAIN=clang") // use clang
                    arguments("-DANDROID_STL=${cfgs.commAndroidStl}") // NOTE: if use c++_shared.so, should be included in app
                }
            }
            ndk {
                abiFilters.addAll(cfgs.cmakeAbiFiltersAsList)
            }
        }
    }
}

internal fun Project.createBuildLibrariesTask(inputProjectName: String) {
    val taskName = "buildLibrariesFor$inputProjectName"
    val allTasks = getTasksByName(taskName, false)
    if (allTasks.isNotEmpty()) {
        println("createTask failed, $taskName already exists")
        return
    }
    tasks.register(taskName, Exec::class) {
        println("[${project.displayName}] configure subProject ${taskName}...")
        doFirst {
            println("[${project.displayName}] execute subProject ${taskName}...")
        }

        // set workingDir
        workingDir = project.rootDir.parentFile
        val sdkDir = getLocalProperties("sdk.dir", System.getenv("ANDROID_HOME"))
        val ndkDir = getLocalProperties("ndk.dir", System.getenv("NDK_ROOT"))
        val cmakeDir = getLocalProperties("cmake.dir", System.getenv("CMAKE_HOME"))
        var cmakeAbiFilters = cfgs.cmakeAbiFiltersAsList
        // to get the path
        var path = System.getenv("PATH")
        if (path == null || path.isEmpty()) {
            path = System.getenv("Path")
            if (path == null || path.isEmpty()) {
                path = System.getenv("path")
            }
        }
        var envMap = mutableMapOf(
            "ANDROID_HOME"    to sdkDir,
            "NDK_ROOT"        to ndkDir,
            "CMAKE_HOME"      to cmakeDir,
            "_ARCH_"          to (cmakeAbiFilters.firstOrNull() ?: "arm64-v8a"),
            "WORKING_DIR"     to workingDir
        )
        if (path != null && path.isNotEmpty()) {
            var delimiter = ":"
            if (Os.isFamily(Os.FAMILY_WINDOWS)) {
                delimiter = ";"
            }

            envMap["PATH"] = path + delimiter + ndkDir + delimiter + cmakeDir + "/bin"
            if (Os.isFamily(Os.FAMILY_WINDOWS)) {
                // add "make" bin path
                envMap["PATH"] = (envMap["PATH"] as String) + delimiter + "${ndkDir}/prebuilt/windows-x86_64/bin"
            }
        }

        environment(envMap)

        println("==============")
        println("envMap")
        println("==============")
        // print list
        envMap.forEach { (key, value) ->
            println("$key=$value")
        }
        println("--------------")

        // Use ccgo CLI command instead of calling Python module directly
        // --native-only flag indicates we only want to build native libraries (.so files)
        // without additional packaging (Gradle will handle the AAR packaging)
        var command = mutableListOf("ccgo", "build", "android", "--arch", cmakeAbiFilters.joinToString(","), "--native-only")
        println("[${inputProjectName}] command:${command}")
        commandLine(command)

        doLast {
            checkExecResult(executionResult)
        }
    }
}

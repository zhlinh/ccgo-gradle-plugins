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
import com.ccgo.gradle.buildlogic.common.utils.execCommand
import org.gradle.api.Project
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Exec
import org.gradle.kotlin.dsl.register

/**
 * Configure the root project to build AAR.
 * Note: ZIP archive creation is handled by Python's archive_android_project() for unified structure.
 */
internal fun Project.configureRootArchive() {
    println("[${project.displayName}] rootProject configureRootArchive...")
    // Task to print all the module paths in the project e.g. :core:data
    // Used by module graph generator script
    tasks.register("printModulePaths") {
        subprojects {
            if (subprojects.size == 0) {
                println(this.path)
            }
        }
    }

    // Task to clean the target/{debug|release}/android directory
    val cleanTheTargetDir = tasks.register("cleanTheTargetDir", Delete::class) {
        println("[${project.displayName}] configure rootProject clean...")
        doFirst {
            println("[${project.displayName}] execute rootProject clean...")
        }
        // Only clean target/{debug|release}/android/ to preserve other platform artifacts
        delete("${rootDir.parentFile}/target/${cfgs.targetSubDir}/android/")
    }

    // Task to copy the AAR file to target/{debug|release}/android/
    val copyProjectAAR = tasks.register("copyProjectAAR", Copy::class) {
        val mainAndroidSdk = cfgs.mainCommProject
        val chosenProject = mainAndroidSdk.name
        val androidProjectPath = mainAndroidSdk.projectDir.absolutePath
        println("[${project.displayName}] configure rootProject [${cfgs.projectNameUppercase}] copyProjectAAR...")
        doFirst {
            println("[${project.displayName}] execute rootProject [${cfgs.projectNameUppercase}] copyProjectAAR...")
            println("[${project.displayName}] get mainProjectName [${chosenProject}], path [${androidProjectPath}]...")
        }
        // Copy AAR directly to target/{debug|release}/android/ (Python will move to haars/ in unified archive)
        copyAARFileOnly(mainAndroidSdk, "${rootDir.parentFile}/target/${cfgs.targetSubDir}/android/")
    }

    // Task to build AAR (renamed from archiveProject)
    // This task only builds the AAR file, ZIP archive is created by Python's archive_android_project()
    val buildAAR = tasks.register("buildAAR") {
        println("[${project.displayName}] configure rootProject [${cfgs.projectNameUppercase}] buildAAR...")
        doFirst {
            println("[${project.displayName}] execute rootProject [${cfgs.projectNameUppercase}] buildAAR...")
        }
        doLast {
            val targetAndroidDir = "${rootDir.parentFile}/target/${cfgs.targetSubDir}/android"
            println("===================${cfgs.projectNameUppercase} Android AAR Build Complete===================")
            val result = execCommand("ls $targetAndroidDir/")
            println(result.trim())
            println("Note: Use Python's archive_android_project() to create unified ZIP archive")
        }
    }

    // Task to build Release AAR (invokes Gradle with -PisRelease=true)
    // Usage: ./gradlew buildAARRelease
    tasks.register("buildAARRelease", Exec::class) {
        group = "build"
        description = "Build AAR with release configuration"
        workingDir = rootDir
        val gradlewPath = if (System.getProperty("os.name").lowercase().contains("windows")) {
            "gradlew.bat"
        } else {
            "./gradlew"
        }
        commandLine(gradlewPath, "buildAAR", "-PisRelease=true", "--no-daemon")
        doFirst {
            println("[${project.displayName}] Building Release AAR...")
        }
    }

    // Task to build Debug/Beta AAR (invokes Gradle with -PisRelease=false)
    // Usage: ./gradlew buildAARDebug
    tasks.register("buildAARDebug", Exec::class) {
        group = "build"
        description = "Build AAR with debug/beta configuration"
        workingDir = rootDir
        val gradlewPath = if (System.getProperty("os.name").lowercase().contains("windows")) {
            "gradlew.bat"
        } else {
            "./gradlew"
        }
        commandLine(gradlewPath, "buildAAR", "-PisRelease=false", "--no-daemon")
        doFirst {
            println("[${project.displayName}] Building Debug/Beta AAR...")
        }
    }

    afterEvaluate {
        // dependencies
        buildAAR.dependsOn(copyProjectAAR)
    }

}

/**
 * Configure the sub project to build AAR.
 */
internal fun Project.configureSubArchive() {
    println("[${project.displayName}] execute subProject configureSubArchive...")
    // Task to clean the target directory
    val cleanTheTargetDir = tasks.register("cleanTheTargetDir", Delete::class) {
        println("[${project.displayName}] configure project clean...")
        doFirst {
            println("[${project.displayName}] execute project clean...")
        }
        delete("${project.projectDir}/target/")
    }

    // Task to generate the AAR file
    val genAAR = tasks.register("genAAR") {
        println("[${project.displayName}] configure project genAAR...")
        doFirst {
            println("[${project.displayName}] execute project genAAR...")
        }
    }

    // Task to copy the AAR file to the target directory
    val copyProjectAAR = tasks.register("copyProjectAAR", Copy::class) {
        println("[${project.displayName}] configure subProject copyProjectAAR...")
        doFirst {
            println("[${project.displayName}] execute subProject copyProjectAAR...")
        }
        copyAARFileOnly(project, "${project.projectDir}/target/")
    }

    project.afterEvaluate {
        tasks.filter {
            cfgs.taskPrintPrefixFilters.any { prefix -> it.name.startsWith(prefix) }
        }.forEach {task ->
            println("[${project.displayName}] task:${task.name}")
        }
        // dependencies
        // root CleanTheBinDir -> CleanTheBinDir
        // -> assembleProdRelease -> genAAR
        // -> copyProjectAAR -> root CopyProjectAAR
        // -> root buildAAR
        val rootCopyProjectAAR = rootProject.tasks.named("copyProjectAAR")
        rootCopyProjectAAR.dependsOn(copyProjectAAR)
        copyProjectAAR.dependsOn(genAAR)
        // can not use named, or dependsOn will occur error
        val assembleProdRelease = tasks.named(cfgs.mainProjectAssembleProdTaskName)
        genAAR.dependsOn(assembleProdRelease)
        assembleProdRelease.dependsOn(cleanTheTargetDir)
        val rootCleanTheBinDir = rootProject.tasks.named("cleanTheTargetDir")
        cleanTheTargetDir.dependsOn(rootCleanTheBinDir)
    }
}

// Copy only the AAR file to the destination directory (no libs, obj, java)
// ZIP archive creation with unified structure is handled by Python's archive_android_project()
fun Copy.copyAARFileOnly(project: Project, destDir: String) {
    val baseProjectDir = project.projectDir.absolutePath
    val fromDir = "${baseProjectDir}/build/outputs/aar/"
    from(fromDir) {
        val matchFile = "${project.name}-${ProjectFlavor.prod.name.lowercase()}-release.aar"
        include(matchFile)
        rename { fileName : String ->
            fileName.replace(matchFile, project.cfgs.mainProjectArchiveAarName)
        }
    }
    into(destDir)
}

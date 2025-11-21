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
import org.gradle.api.tasks.bundling.Zip
import org.gradle.kotlin.dsl.register
import java.io.File

/**
 * Configure the root project to archive the project.
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

    // Task to clean the bin directory
    val cleanTheBinDir = tasks.register("cleanTheBinDir", Delete::class) {
        println("[${project.displayName}] configure rootProject clean...")
        doFirst {
            println("[${project.displayName}] execute rootProject clean...")
        }
        delete("${rootDir.parentFile}/bin/")
    }

    // Task to copy the AAR file to the bin directory
    val copyProjectAAR = tasks.register("copyProjectAAR", Copy::class) {
        val mainAndroidSdk = cfgs.mainCommProject
        val chosenProject = mainAndroidSdk.name
        val androidProjectPath = mainAndroidSdk.projectDir.absolutePath
        println("[${project.displayName}] configure rootProject [${cfgs.projectNameUppercase}] copyProjectAAR...")
        doFirst {
            println("[${project.displayName}] execute rootProject [${cfgs.projectNameUppercase}] copyProjectAAR...")
            println("[${project.displayName}] get mainProjectName [${chosenProject}], path [${androidProjectPath}]...")
        }
        copyAARFile(mainAndroidSdk, "${rootDir.parentFile}/bin/")
    }

    // Task to archive the project
    val archiveProject = tasks.register("archiveProject", Zip::class) {
        println("[${project.displayName}] configure rootProject [${cfgs.projectNameUppercase}] archiveProject...")
        doFirst {
            println("[${project.displayName}] execute rootProject [${cfgs.projectNameUppercase}] archiveProject...")
        }
        zipFiles(project)
    }

    afterEvaluate {
        // dependencies
        archiveProject.dependsOn(copyProjectAAR)
    }

}

/**
 * Configure the sub project to archive the project.
 */
internal fun Project.configureSubArchive() {
    println("[${project.displayName}] execute subProject configureSubArchive...")
    // Task to clean the bin directory
    val cleanTheBinDir = tasks.register("cleanTheBinDir", Delete::class) {
        println("[${project.displayName}] configure project clean...")
        doFirst {
            println("[${project.displayName}] execute project clean...")
        }
        delete("${project.projectDir}/bin/")
    }

    // Task to generate the AAR file
    val genAAR = tasks.register("genAAR") {
        println("[${project.displayName}] configure project genAAR...")
        doFirst {
            println("[${project.displayName}] execute project genAAR...")
        }
    }

    // Task to copy the AAR file to the bin directory
    val copyProjectAAR = tasks.register("copyProjectAAR", Copy::class) {
        println("[${project.displayName}] configure subProject copyProjectAAR...")
        doFirst {
            println("[${project.displayName}] execute subProject copyProjectAAR...")
        }
        copyAARFile(project, "${project.projectDir}/bin/")
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
        // -> root archiveProject
        val rootCopyProjectAAR = rootProject.tasks.named("copyProjectAAR")
        rootCopyProjectAAR.dependsOn(copyProjectAAR)
        copyProjectAAR.dependsOn(genAAR)
        // can not use named, or dependsOn will occur error
        val assembleProdRelease = tasks.named(cfgs.mainProjectAssembleProdTaskName)
        genAAR.dependsOn(assembleProdRelease)
        assembleProdRelease.dependsOn(cleanTheBinDir)
        val rootCleanTheBinDir = rootProject.tasks.named("cleanTheBinDir")
        cleanTheBinDir.dependsOn(rootCleanTheBinDir)
    }
}

// Copy the AAR file to the destination directory
fun Copy.copyAARFile(project: Project, destDir: String) {
    val baseProjectDir = project.projectDir.absolutePath
    val fromDir = "${baseProjectDir}/build/outputs/aar/"
    from(fromDir) {
        val matchFile = "${project.name}-${ProjectFlavor.prod.name.lowercase()}-release.aar"
        include(matchFile)
        rename { fileName : String ->
            fileName.replace(matchFile, project.cfgs.mainProjectArchiveAarName)
        }
    }
    from ("${baseProjectDir}/") {
        include("libs/**")
        include("obj/**")
    }
    from ("${baseProjectDir}/src/main/") {
        include("java/**")
    }
    into(destDir)
}

// Zip the files
fun Zip.zipFiles(project: Project) {
    from ("${project.rootDir.parentFile}/bin") {
        include("java/**")
        include("libs/**")
        include("obj/**")
        include("*.aar")
    }
    // remove .zip suffix
    into(project.cfgs.mainProjectArchiveZipName.substringBeforeLast("."))
    archiveFileName.set(project.cfgs.mainProjectArchiveZipName)
    destinationDirectory.set(File("${project.rootDir.parentFile}/bin/"))
    doLast {
        project.delete("${project.rootDir.parentFile}/bin/java/")
        project.delete("${project.rootDir.parentFile}/bin/libs/")
        project.delete("${project.rootDir.parentFile}/bin/obj/")
        println("===================${project.cfgs.projectNameUppercase} output===================")
        val result = execCommand("ls ${project.rootDir.parentFile}/bin/")
        println(result.trim())
    }
}

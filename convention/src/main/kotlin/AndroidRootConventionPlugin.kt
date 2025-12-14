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

import com.ccgo.gradle.buildlogic.common.configureRootArchive
import com.ccgo.gradle.buildlogic.common.configureRootNativeBuild
import com.ccgo.gradle.buildlogic.common.configureNativePush
import com.ccgo.gradle.buildlogic.common.configurePublish
import com.ccgo.gradle.buildlogic.common.configureTag
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import java.io.File

/**
 * for root project
 */
class AndroidRootConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target.rootProject) {
            with(pluginManager) {
                apply("org.jetbrains.kotlin.jvm")
                apply("com.mojeter.ccgo.gradle.android.lint")
                apply("maven-publish")
                apply("signing")
                // Apply nmcp plugins for Maven Central publishing
                apply("com.gradleup.nmcp")
                apply("com.gradleup.nmcp.aggregation")
                // apply build_config.gradle.kts
                val buildConfigFile = "${rootDir.parentFile}/build_config.gradle.kts"
                if (File(buildConfigFile).exists()) {
                    apply(from = buildConfigFile)
                }
            }
            // for ndk path set to properties ndk.dir
            configureRootNativeBuild()
            // execute "./gradlew :pushSo" or "./gradlew :rmSo"
            configureNativePush()
            // execute "./gradlew :tagGit"
            configureTag()
            // execute "./gradlew :archiveProject"
            configureRootArchive()
            // execute "./gradlew publishMainPublicationToMavenRepository"
            configurePublish()
        }
    }
}

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

import com.ccgo.gradle.buildlogic.common.configureKmpAndroidLibrary
import com.ccgo.gradle.buildlogic.common.configureKmpCinterop
import com.ccgo.gradle.buildlogic.common.configureKmpLibrary
import com.ccgo.gradle.buildlogic.common.configureKmpPublishingMetadata
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * KMP Library Native Python plugin for Kotlin Multiplatform projects.
 *
 * This plugin configures a complete KMP library with:
 * - All standard targets (Android, iOS, macOS, Linux, Desktop JVM)
 * - CocoaPods configuration
 * - Dependencies from CCGO.toml
 * - cinterop for native targets
 * - Maven publishing support
 *
 * IMPORTANT: This plugin requires the following plugins to be applied BEFORE it:
 * - kotlin("multiplatform")
 * - kotlin("native.cocoapods")
 * - id("com.android.library")
 *
 * Usage in build.gradle.kts:
 * ```
 * plugins {
 *     kotlin("multiplatform")
 *     kotlin("native.cocoapods")
 *     id("com.android.library")
 *     id("com.mojeter.ccgo.gradle.kmp.library.native.python")
 * }
 * ```
 */
class KmpLibraryNativePythonConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            // Apply KMP publish plugin for Maven publishing
            pluginManager.apply("com.mojeter.ccgo.gradle.kmp.publish")

            // Configure when Kotlin Multiplatform plugin is applied
            plugins.withId("org.jetbrains.kotlin.multiplatform") {
                // Configure KMP library targets and sourceSets
                configureKmpLibrary()

                // Configure cinterop for native targets
                configureKmpCinterop()

                // Configure publishing metadata
                configureKmpPublishingMetadata()
            }
        }
    }
}

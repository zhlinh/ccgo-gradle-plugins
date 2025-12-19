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
import com.ccgo.gradle.buildlogic.common.configureKmpLibrary
import com.ccgo.gradle.buildlogic.common.configureKmpPublishingMetadata
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * KMP Library Native Empty plugin for Kotlin Multiplatform projects without native C++ code.
 *
 * This plugin is similar to KmpLibraryNativePythonConventionPlugin but without cinterop configuration.
 * Use this for pure Kotlin KMP libraries that don't need to interop with native C++ code.
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
 *     id("com.mojeter.ccgo.gradle.kmp.library.native.empty")
 * }
 * ```
 */
class KmpLibraryNativeEmptyConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            // Apply KMP publish plugin for Maven publishing
            pluginManager.apply("com.mojeter.ccgo.gradle.kmp.publish")

            // Configure when Kotlin Multiplatform plugin is applied
            plugins.withId("org.jetbrains.kotlin.multiplatform") {
                // Configure KMP library targets and sourceSets
                configureKmpLibrary()

                // Configure publishing metadata (no cinterop for empty plugin)
                configureKmpPublishingMetadata()
            }

            // Note: No native build configuration here
            // Native libraries should be placed in expected locations:
            // - Android: src/androidMain/jniLibs/
            // - iOS/macOS: linked via framework or static library
        }
    }
}

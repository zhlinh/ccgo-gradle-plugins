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

import com.ccgo.gradle.buildlogic.common.configureKmpPublish
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Publish plugin for Kotlin Multiplatform (KMP) projects.
 *
 * This plugin configures Maven publishing for KMP projects with:
 * - Credential reading from multiple sources (env, CCGO.toml, gradle.properties)
 * - Automatic signing configuration (in-memory PGP key or GPG agent)
 * - Unified task aliases (ccgoPublishToMavenLocal, ccgoPublishToMavenCentral, ccgoPublishToMavenCustom)
 * - Maven Central publishing via nmcp plugin (Central Portal)
 *
 * Usage in build.gradle.kts:
 * ```
 * plugins {
 *     id("com.mojeter.ccgo.gradle.kmp.publish")
 * }
 * ```
 */
class KmpPublishConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("maven-publish")
                apply("signing")
                // Apply nmcp plugin for Maven Central publishing via Central Portal
                apply("com.gradleup.nmcp")
            }
            configureKmpPublish()
        }
    }
}

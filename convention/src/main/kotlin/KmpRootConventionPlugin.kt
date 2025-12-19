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

import com.ccgo.gradle.buildlogic.common.CCGOConfig
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * KMP Root plugin for Kotlin Multiplatform root projects.
 *
 * This plugin configures the root project for KMP builds:
 * - Prints CCGO configuration
 * - Can be extended for additional root-level setup
 *
 * Note: KMP projects typically use settings.gradle.kts for repository
 * configuration, which is done at settings time, not project time.
 *
 * Usage in build.gradle.kts (root project):
 * ```
 * plugins {
 *     id("com.mojeter.ccgo.gradle.kmp.root")
 * }
 * ```
 *
 * Or with version catalog:
 * ```
 * plugins {
 *     alias(libs.plugins.ccgo.kmp.root)
 * }
 * ```
 */
class KmpRootConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target.rootProject) {
            // Read and print CCGO configuration
            val ccgoConfig = CCGOConfig.getDefault(this)

            println("[KMP-Root] Configuring KMP root project: ${ccgoConfig.projectName}")
            println("[KMP-Root] Version: ${ccgoConfig.version}")
            println("[KMP-Root] Group ID: ${ccgoConfig.kmpGroupId}")

            if (ccgoConfig.kmpDependencies.isNotEmpty()) {
                println("[KMP-Root] KMP Dependencies:")
                ccgoConfig.kmpDependencies.forEach { dep ->
                    println("[KMP-Root]   - ${dep.toMavenCoordinate()}")
                }
            }
        }
    }
}

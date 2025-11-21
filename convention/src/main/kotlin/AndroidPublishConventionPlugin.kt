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

import com.ccgo.gradle.buildlogic.common.configurePublish
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Publish plugin for Android projects.
 */
class AndroidPublishConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("org.jetbrains.kotlin.jvm")
                apply("com.mojeter.ccgo.gradle.android.lint")
                apply("com.vanniktech.maven.publish")
                apply("signing")
            }
            configurePublish()
        }
    }
}

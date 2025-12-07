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

import com.android.build.api.dsl.ApplicationExtension
import com.ccgo.gradle.buildlogic.common.configureAndroidCompose
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType

/**
 * Compose plugin for Android application projects.
 */
class AndroidApplicationComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")

            val extension = extensions.getByType<ApplicationExtension>()
            configureAndroidCompose(extension)
        }
    }

}

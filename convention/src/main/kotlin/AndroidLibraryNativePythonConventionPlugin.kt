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

import com.android.build.gradle.LibraryExtension
import com.ccgo.gradle.buildlogic.common.configureSubNativeBuildPython
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Native Python plugin for Android library projects.
 */
class AndroidLibraryNativePythonConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.mojeter.ccgo.gradle.android.library")
            }
            configureSubNativeBuildPython()
        }
    }
}

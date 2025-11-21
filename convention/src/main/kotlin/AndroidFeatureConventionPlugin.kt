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
import com.ccgo.gradle.buildlogic.common.configureGradleManagedDevices
import com.ccgo.gradle.buildlogic.common.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Android feature plugin for Android projects.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply {
                apply("com.mojeter.ccgo.gradle.android.library")
                apply("com.mojeter.ccgo.gradle.android.hilt")
            }
            extensions.configure<LibraryExtension> {
                defaultConfig {
                    testInstrumentationRunner =
                        "androidx.test.runner.AndroidJUnitRunner"
                }
                testOptions.animationsDisabled = true
                configureGradleManagedDevices(this)
            }

            configureAndroidFeatureDependencies()
        }
    }

    private fun Project.configureAndroidFeatureDependencies() {
        dependencies {
            add("implementation", libs.findLibrary("androidx.hilt.navigation.compose").get())
            add("implementation", libs.findLibrary("androidx.lifecycle.runtimeCompose").get())
            add("implementation", libs.findLibrary("androidx.lifecycle.viewModelCompose").get())
            add("implementation", libs.findLibrary("androidx.tracing.ktx").get())

            add("androidTestImplementation", libs.findLibrary("androidx.lifecycle.runtimeTesting").get())
        }
    }
}



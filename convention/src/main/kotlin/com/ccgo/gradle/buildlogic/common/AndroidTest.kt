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

import com.android.build.api.dsl.CommonExtension
import com.ccgo.gradle.buildlogic.common.utils.getThirdPartyXCommLibs
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.kotlin

/**
 * Configure Android test.
 */
internal fun Project.configureAndroidTest(commonExtension: CommonExtension<*, *, *, *, *, *>) {
    commonExtension.apply {
        testOptions {
            execution = "ANDROIDX_TEST_ORCHESTRATOR"
            animationsDisabled = true
        }
        // for android test source set
        sourceSets {
            maybeCreate("androidTest").apply {
                // there is no '=' here
                manifest.srcFile("src/androidTest/AndroidManifest.xml")
                jniLibs.srcDirs(listOf("libs/shared") + getThirdPartyXCommLibs())
            }
        }
        defaultConfig {
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            // The following argument makes the Android Test Orchestrator run its
            // "pm clear" command after each test invocation. This command ensures
            // that the app's state is completely cleared between tests.
            testInstrumentationRunnerArguments["clearPackageData"] = "true"
        }
    }
    configureAndroidTestDependencies()
}

/**
 * config Android test dependencies
 */
private fun Project.configureAndroidTestDependencies() {
    dependencies {
        add("testImplementation", kotlin("test"))
        add("testImplementation", libs.findLibrary("androidx.tracing.ktx").get())
        // Core library
        add("androidTestImplementation", libs.findLibrary("androidx.test.core").get())
        // AndroidJUnitRunner and JUnit Rules
        add("androidTestImplementation", libs.findLibrary("androidx.test.runner").get())
        add("androidTestImplementation", libs.findLibrary("androidx.test.rules").get())
        // Assertions
        add("androidTestImplementation", libs.findLibrary("androidx.test.ext").get())
        // Espresso dependencies
        add("androidTestImplementation", libs.findLibrary("androidx.test.espresso.core").get())
    }
}
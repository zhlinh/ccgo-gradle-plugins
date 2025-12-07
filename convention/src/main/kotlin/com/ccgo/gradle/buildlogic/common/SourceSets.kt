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
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Configures the source sets for the project.
 */
internal fun Project.configureSourceSets(commonExtension: CommonExtension<*, *, *, *, *, *>) {
    commonExtension.apply {
        sourceSets {
            maybeCreate("main").apply {
                jniLibs.srcDirs("libs/shared")
            }
        }
        // The resource prefix is derived from the module name,
        // so resources inside ":core:module1" must be prefixed with "core_module1_"
        resourcePrefix = path.split("""\W""".toRegex()).drop(1).distinct().joinToString(separator = "_").lowercase() + "_"
    }
}
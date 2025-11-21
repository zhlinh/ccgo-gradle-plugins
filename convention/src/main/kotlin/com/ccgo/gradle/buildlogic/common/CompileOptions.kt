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
import org.gradle.api.Project

/**
 * Configures the compile options for the project.
 */
internal fun Project.configureCompileOptions(commonExtension: CommonExtension<*, *, *, *, *, *>) {
    commonExtension.apply {
        compileOptions {
            // Up to Java 11 APIs are available through desugaring
            // https://developer.android.com/studio/write/java11-minimal-support-table
            sourceCompatibility = cfgs.javaCompatibilityVersion
            targetCompatibility = cfgs.javaCompatibilityVersion
            isCoreLibraryDesugaringEnabled = true
        }
    }
}
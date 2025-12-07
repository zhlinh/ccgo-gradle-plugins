//
// Copyright 2024 zhlinh and ccgo Project Authors. All rights reserved.
// Use of this source code is governed by a MIT-style
// license that can be found at
//
// https://opensource.org/license/MIT
//
// The above copyright notice and this permission
// notice shall be included in all copies or
// substantial portions of the Software

package com.ccgo.gradle.buildlogic.common

import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Configures the default config for the project.
 */
internal fun Project.configureDefaultConfig(commonExtension: CommonExtension<*, *, *, *, *, *>) {
    extensions.configure<LibraryExtension> {
        defaultConfig {
            multiDexEnabled = true
            // proguard config file to aar
            consumerProguardFiles( "proguard-rules.pro")
        }
    }
    commonExtension.apply {
        defaultConfig {
            minSdk = cfgs.minSdkVersion

            println("The minSdk:${minSdk}")
            println("-------------------------------------------")
        }
    }
}
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
import org.gradle.api.Project

/**
 * Configures the build types for the project.
 */
internal fun Project.configureBuildTypes(commonExtension: CommonExtension<*, *, *, *, *, *>) {
    commonExtension.apply {
        packaging {
            jniLibs.pickFirsts.add("**/*.so")
        }

        buildTypes {
            getByName("release") {
                isMinifyEnabled = false
                proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
            }

            getByName("debug") {
                isMinifyEnabled = false
                proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")

                externalNativeBuild {
                    cmake {
                        cppFlags("-D${cfgs.projectNameUppercase}_USE_LEAKTRACER=TRUE -DLEAKTRACER_SUPPORT=ON")
                    }
                }

                packaging {
                    jniLibs.keepDebugSymbols.add("**/*.so")
                }
            }
        }
    }
}
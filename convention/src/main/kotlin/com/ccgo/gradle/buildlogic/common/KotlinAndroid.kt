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
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.provideDelegate
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * Configure base Kotlin with Android options
 */
internal fun Project.configureKotlinAndroid(
    commonExtension: CommonExtension<*, *, *, *, *, *>,
) {
    commonExtension.apply {
        // base
        configureKotlinAndroidBase(this)
        // others
        configureDefaultConfig(this)
        configureAndroidTest(this)
        configureSourceSets(this)
        configureLint(this)
        configureBuildTypes(this)
        configureCompileOptions(this)
    }

    configureKotlin()

    configureKotlinAndroidDependencies()
}

/**
 * Configure base Kotlin base options
 */
private fun Project.configureKotlinAndroidBase(commonExtension: CommonExtension<*, *, *, *, *, *>) {
    commonExtension.apply {
        compileSdk = cfgs.compileSdkVersion
        buildToolsVersion = cfgs.buildToolsVersion
        namespace = cfgs.commGroupId
        println("The rootDir:${rootDir}")
        println("The compileSdk:${compileSdk}")
        println("The buildToolsVersion:${buildToolsVersion}")
        println("The namespace:${namespace}")
        println("-------------------------------------------")
    }
}

/**
 * Configure base Kotlin options for JVM (non-Android)
 */
internal fun Project.configureKotlinJvm() {
    extensions.configure<JavaPluginExtension> {
        // Up to Java 11 APIs are available through desugaring
        // https://developer.android.com/studio/write/java11-minimal-support-table
        sourceCompatibility = cfgs.javaCompatibilityVersion
        targetCompatibility = cfgs.javaCompatibilityVersion
    }

    configureKotlin()
}

/**
 * Configure base Kotlin options
 */
private fun Project.configureKotlin() {
    // Use withType to workaround https://youtrack.jetbrains.com/issue/KT-55947
    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions {
            // Set JVM target to 11
            jvmTarget = cfgs.javaCompatibilityVersion.toString()
            // Treat all Kotlin warnings as errors (disabled by default)
            // Override by setting warningsAsErrors=true in your ~/.gradle/gradle.properties
            val warningsAsErrors: String? by project
            allWarningsAsErrors = warningsAsErrors.toBoolean()
            freeCompilerArgs = freeCompilerArgs + listOf(
                // Enable experimental coroutines APIs, including Flow
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            )
        }
    }
}

/**
 * Configure dependencies
 */
private fun Project.configureKotlinAndroidDependencies() {
    dependencies {
        // for libs
        add("implementation", fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
        // for annotation symbol @IntDef
        add("implementation", libs.findLibrary("androidx.annotation").get())
        // for Cannot fit requested classes in a single dex file
        add("implementation", libs.findLibrary("androidx.multidex").get())
        // for desugaring
        add("coreLibraryDesugaring", libs.findLibrary("android.desugarJdkLibs").get())
    }
}

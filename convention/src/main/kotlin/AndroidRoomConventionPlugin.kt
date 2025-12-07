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

import androidx.room.gradle.RoomExtension
import com.google.devtools.ksp.gradle.KspExtension
import com.ccgo.gradle.buildlogic.common.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Room plugin for Android projects.
 */
class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("androidx.room")
            pluginManager.apply("com.google.devtools.ksp")

            extensions.configure<KspExtension> {
                arg("room.generateKotlin", "true")
            }

            extensions.configure<RoomExtension> {
                // The schemas directory contains a schema file for each version of the Room database.
                // This is required to enable Room auto migrations.
                // See https://developer.android.com/reference/kotlin/androidx/room/AutoMigration.
                schemaDirectory("$projectDir/schemas")
            }

            configureAndroidRoomDependencies()
        }
    }

    private fun Project.configureAndroidRoomDependencies() {
        dependencies {
            add("implementation", libs.findLibrary("room.runtime").get())
            add("implementation", libs.findLibrary("room.ktx").get())
            add("ksp", libs.findLibrary("room.compiler").get())
        }
    }
}
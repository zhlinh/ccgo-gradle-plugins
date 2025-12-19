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

import com.android.build.gradle.LibraryExtension
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.cocoapods.CocoapodsExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import java.io.File

/**
 * Configures Kotlin Multiplatform library with all standard targets.
 *
 * This sets up:
 * - Android target with release publishing
 * - iOS targets (x64, arm64, simulator arm64)
 * - macOS targets (x64, arm64)
 * - Linux targets (x64, arm64)
 * - Desktop JVM target (for Windows)
 * - CocoaPods configuration
 */
internal fun Project.configureKmpLibrary() {
    val ccgoConfig = CCGOConfig.getDefault(this)

    extensions.configure<KotlinMultiplatformExtension> {
        // Apply default hierarchy template for proper cinterop commonization
        // This automatically creates intermediate source sets (nativeMain, appleMain, linuxMain, etc.)
        // that properly support cinterop binding sharing across targets
        applyDefaultHierarchyTemplate()

        // Android target
        androidTarget {
            publishLibraryVariants("release")
            compilations.all {
                kotlinOptions {
                    jvmTarget = "17"
                }
            }
        }

        // iOS targets
        iosX64()
        iosArm64()
        iosSimulatorArm64()

        // Desktop Native targets for macOS and Linux
        macosX64()
        macosArm64()

        linuxX64()
        linuxArm64()

        // Desktop JVM target for Windows
        // Windows uses JVM because CCGO builds with MSVC, while Kotlin/Native uses MinGW (incompatible)
        jvm("desktop") {
            compilations.all {
                kotlinOptions {
                    jvmTarget = "17"
                }
            }
        }

        // CocoaPods configuration for iOS - accessed via extension
        extensions.findByType<CocoapodsExtension>()?.apply {
            summary = "${ccgoConfig.projectName.replaceFirstChar { it.uppercase() }} Kotlin Multiplatform Library"
            homepage = ccgoConfig.repositoryUrl.ifEmpty { "https://github.com/example/project" }
            ios.deploymentTarget = ccgoConfig.kmpIosDeploymentTarget

            framework {
                baseName = "${ccgoConfig.projectName.replaceFirstChar { it.uppercase() }}KMP"
                isStatic = true
            }
        }

        // Configure sourceSets - use default hierarchy names
        // The applyDefaultHierarchyTemplate() creates: nativeMain, appleMain, iosMain, macosMain, linuxMain
        sourceSets.apply {
            val commonMain = getByName("commonMain")
            val commonTest = getByName("commonTest")

            // Add dependencies from CCGO.toml to commonMain
            commonMain.dependencies {
                ccgoConfig.kmpDependencies.forEach { dep ->
                    api("${dep.group}:${dep.artifact}:${dep.version}")
                }
            }

            commonTest.dependencies {
                implementation(kotlin("test"))
            }

            // Android source set
            getByName("androidMain").dependencies {
                implementation("androidx.core:core-ktx:1.12.0")
            }

            getByName("androidUnitTest").dependencies {
                implementation(kotlin("test-junit"))
                implementation("junit:junit:4.13.2")
            }

            // Desktop JVM source set (for Windows)
            getByName("desktopMain").dependencies {
                // Desktop-specific dependencies
            }

            getByName("desktopTest").dependencies {
                implementation(kotlin("test-junit"))
                implementation("junit:junit:4.13.2")
            }

            // Note: iosMain, macosMain, linuxMain are auto-created by applyDefaultHierarchyTemplate()
            // They automatically get cinterop bindings through commonization
        }
    }

    // Configure Android library block
    configureKmpAndroidLibrary()
}

/**
 * Configures the Android library block for KMP projects.
 */
internal fun Project.configureKmpAndroidLibrary() {
    val ccgoConfig = CCGOConfig.getDefault(this)

    extensions.configure<LibraryExtension> {
        namespace = "${ccgoConfig.kmpGroupId}.${ccgoConfig.projectName.lowercase()}.kmp"
        compileSdk = ccgoConfig.compileSdk

        defaultConfig {
            minSdk = ccgoConfig.kmpAndroidMinSdk
            consumerProguardFiles("consumer-rules.pro")
        }

        buildTypes {
            release {
                isMinifyEnabled = false
                proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
                )
            }
        }

        compileOptions {
            sourceCompatibility = org.gradle.api.JavaVersion.VERSION_17
            targetCompatibility = org.gradle.api.JavaVersion.VERSION_17
        }
    }
}

/**
 * Configures cinterop for all Kotlin/Native targets.
 *
 * This allows direct C function calls from Kotlin/Native without JNI overhead.
 */
internal fun Project.configureKmpCinterop() {
    val ccgoConfig = CCGOConfig.getDefault(this)
    val projectNameCapitalized = ccgoConfig.projectName.replaceFirstChar { it.uppercase() }
    val projectNameLower = ccgoConfig.projectName.lowercase()

    extensions.configure<KotlinMultiplatformExtension> {
        targets.withType<KotlinNativeTarget> {
            compilations.getByName("main") {
                cinterops {
                    create(projectNameCapitalized) {
                        // Definition file path
                        defFile(project.file("src/nativeInterop/cinterop/${projectNameCapitalized}.def"))

                        // Package name for generated Kotlin code
                        packageName(projectNameLower)

                        // Include directories for header files
                        includeDirs.allHeaders(
                            project.file("../include/${projectNameLower}/api/apple"),
                            project.file("../include/${projectNameLower}/api/native")
                        )

                        // Compiler options - add include paths
                        val opts = mutableListOf(
                            "-I${project.file("../include/${projectNameLower}/api/apple").absolutePath}",
                            "-I${project.file("../include/${projectNameLower}/api/native").absolutePath}"
                        )

                        // Disable _Float16 and other unsupported features for Apple targets
                        if (this@withType.konanTarget.family.isAppleFamily) {
                            opts.addAll(listOf(
                                // Disable _Float16 type (not supported by cinterop)
                                "-D__FLT16_MANT_DIG__=0",
                                "-D__STDC_WANT_IEC_60559_TYPES_EXT__=0"
                            ))
                        }

                        compilerOpts(*opts.toTypedArray())
                    }
                }
            }
        }
    }
}

/**
 * Configures KMP publishing metadata.
 */
internal fun Project.configureKmpPublishingMetadata() {
    val ccgoConfig = CCGOConfig.getDefault(this)

    group = ccgoConfig.kmpGroupId
    version = ccgoConfig.version

    // Register javadoc jar task for Maven Central compliance
    val javadocJar = tasks.register<Jar>("javadocJar") {
        archiveClassifier.set("javadoc")
        // Empty javadoc jar is acceptable for Kotlin libraries
    }

    // Configure POM metadata for all publications
    afterEvaluate {
        extensions.configure<org.gradle.api.publish.PublishingExtension> {
            publications {
                withType<org.gradle.api.publish.maven.MavenPublication> {
                    pom {
                        name.set("${ccgoConfig.projectName.replaceFirstChar { it.uppercase() }} KMP - $artifactId")
                        description.set("${ccgoConfig.projectName.replaceFirstChar { it.uppercase() }} Kotlin Multiplatform Library")
                    }
                }
                // Add javadoc jar to desktop publication
                findByName("desktop")?.let { publication ->
                    (publication as org.gradle.api.publish.maven.MavenPublication).artifact(javadocJar)
                }
            }
        }
    }
}

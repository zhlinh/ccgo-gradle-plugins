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

        listOf("windows", "linux", "macos").forEach { platform ->
            project.kmpNativeSdk(platform)?.let { sdk ->
                project.tasks.withType<Jar>().configureEach {
                    if (name == "desktopJar") {
                        from(sdk.resolve("lib/$platform/shared")) {
                            include("**/*.dll", "**/*.so", "**/*.so.*", "**/*.dylib")
                            into("native/$platform")
                        }
                    }
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
        project.kmpNativeSdk("android")?.let { sdk ->
            val shared = sdk.resolve("lib/android/shared")
            require(shared.isDirectory) { "Android SDK must contain shared JNI libraries: $shared" }
            sourceSets.getByName("main").jniLibs.setSrcDirs(listOf(shared))
        }
    }
}

/** Returns the ccgo platform directory name for a Kotlin/Native target. */
private fun nativePlatformName(targetName: String): String = when {
    targetName.contains("ios") -> "ios"
    targetName.contains("macos") -> "macos"
    targetName.contains("linux") -> "linux"
    else -> "unknown"
}

private fun nativeArchName(targetName: String): String = when (targetName) {
    "ios_arm64" -> "arm64"
    "ios_simulator_arm64" -> "arm64"
    "ios_x64" -> "x86_64"
    "macos_arm64" -> "arm64"
    "macos_x64" -> "x86_64"
    "linux_arm64" -> "arm64"
    "linux_x64" -> "x86_64"
    else -> "arm64"
}

private fun nativeDeviceType(targetName: String): String = when {
    targetName.contains("simulator") -> "simulator"
    targetName == "ios_x64" -> "simulator"
    targetName == "ios_arm64" -> "device"
    else -> ""
}

/**
 * ccgo 3.x writes to ccgo_build/; keep cmake_build/ for older projects.
 */
private fun Project.nativeLibraryPath(
    buildMode: String,
    platformName: String,
    deviceType: String,
    archName: String
): File {
    val root = if (file("../ccgo_build").exists()) {
        file("../ccgo_build")
    } else {
        file("../cmake_build")
    }
    val relative = if (deviceType.isNotEmpty()) {
        "$buildMode/$platformName/static/$deviceType/$archName/out"
    } else {
        "$buildMode/$platformName/static/$archName/out"
    }
    return root.resolve(relative)
}

private fun nativeCompilerOpts(
    headers: File,
    projectNameLower: String,
    isApple: Boolean
): Array<String> {
    val opts = mutableListOf(
        "-I${headers.absolutePath}",
        "-I${headers.resolve("$projectNameLower/api/apple").absolutePath}",
        "-I${headers.resolve("$projectNameLower/api/native").absolutePath}"
    )
    if (isApple) {
        opts.addAll(
            listOf(
                "-D__FLT16_MANT_DIG__=0",
                "-D__STDC_WANT_IEC_60559_TYPES_EXT__=0"
            )
        )
    }
    return opts.toTypedArray()
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
                        val target = this@withType
                        val targetName = target.konanTarget.name
                        val buildMode = if (ProjectConfig.getDefault(project).isRelease) {
                            "release"
                        } else {
                            "debug"
                        }
                        val libPath = project.nativeLibraryPath(
                            buildMode,
                            nativePlatformName(targetName),
                            nativeDeviceType(targetName),
                            nativeArchName(targetName)
                        )
                        val sdk = project.kmpNativeSdk(nativePlatformName(targetName))
                        val headers = sdk?.resolve("include") ?: project.file("../include")

                        defFile(
                            project.file(
                                "src/nativeInterop/cinterop/$projectNameCapitalized.def"
                            )
                        )
                        packageName(projectNameLower)
                        includeDirs.allHeaders(
                            headers,
                            headers.resolve("$projectNameLower/api/apple"),
                            headers.resolve("$projectNameLower/api/native")
                        )
                        compilerOpts(
                            *nativeCompilerOpts(
                                headers,
                                projectNameLower,
                                target.konanTarget.family.isAppleFamily
                            )
                        )

                        // Configure static library to link. This explicitly specifies
                        // the merged library name to avoid cinterop auto-discovering
                        // module libraries that no longer exist after ccgo's merge.
                        if (sdk != null) {
                            val library = nativeSdkStaticLibrary(
                                sdk, nativePlatformName(targetName), nativeArchName(targetName),
                                nativeDeviceType(targetName), projectNameLower
                            )
                            extraOpts("-libraryPath", library.parentFile.absolutePath)
                            extraOpts("-staticLibrary", library.name)
                        } else if (libPath.exists()) {
                            extraOpts("-libraryPath", libPath.absolutePath)
                            extraOpts("-staticLibrary", "lib$projectNameLower.a")
                        }
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

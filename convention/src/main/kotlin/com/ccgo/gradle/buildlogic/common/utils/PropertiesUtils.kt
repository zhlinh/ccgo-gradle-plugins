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

package com.ccgo.gradle.buildlogic.common.utils

import org.gradle.api.Project
import java.io.File
import java.util.Properties

/**
 * Generate local.properties file with the SDK and CMake paths.
 */
internal fun Project.generateLocalProperties() {
    generateLocalProperties(rootDir.absolutePath)
}

/**
 * Generate local.properties file with the SDK and CMake paths.
 */
internal fun generateLocalProperties(rootDirPath: String) {
    val rootDir = File(rootDirPath)
    val localPropertiesFile = File(rootDir, "local.properties")
    val localProperties = Properties()

    if (localPropertiesFile.exists()) {
        localProperties.load(localPropertiesFile.reader())
    }

    val sdkDir = System.getenv("ANDROID_HOME") ?: localProperties.getProperty("sdk.dir")
    val cmakeDir = System.getenv("CMAKE_HOME") ?: localProperties.getProperty("cmake.dir")
    // ndkDir set by build.gradle.kts from environment variable NDK_ROOT

    sdkDir?.let { localProperties.setProperty("sdk.dir", it) }
    cmakeDir?.let { localProperties.setProperty("cmake.dir", it) }

    localPropertiesFile.writer().use { writer ->
        localProperties.store(writer, null)
    }
}

/**
 * Get a property from the local.properties file.
 */
internal fun Project.getLocalProperties(key: String, defaultValue:  String) : String {
    return getLocalProperties(rootDir.absolutePath, key, defaultValue)
}

/**
 * Get a property from the local.properties file.
 */
internal fun getLocalProperties(rootDirPath: String, key: String, defaultValue:  String) : String {
    val rootDir = File(rootDirPath)
    val file = File(rootDir, "local.properties")
    var ret = defaultValue
    try {
        val properties = Properties()
        if (!file.exists()) {
            return ret
        }
        java.io.InputStreamReader(java.io.FileInputStream(file), Charsets.UTF_8).use { reader ->
            properties.load(reader)
        }
        val debugStr = properties.getProperty(key, defaultValue) as String
        if (debugStr.isNotEmpty()) {
            ret = debugStr
        }
    } catch (throwable: Throwable) {
        throwable.printStackTrace()
    }
    println("[${file.name}] ${key}:${ret}")
    return ret
}

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

internal fun getFileParent(fileName: String, separator: String): String {
    val index = fileName.lastIndexOf(separator)
    if (index < separator.length) {
        return separator
    }
    return fileName.substring(0, index)
}

internal fun findFiles(directoryPath: String, filePattern: String): List<File> {
    val directory = File(directoryPath)
    val matchingFiles = mutableListOf<File>()

    // Ensure the directory exists
    if (directory.exists() && directory.isDirectory) {
        // List all files matching the filePattern
        directory.listFiles { _, name ->
            name.matches(Regex(filePattern))
        }?.let { files ->
            matchingFiles.addAll(files)
        }
    } else {
        println("The directory $directoryPath does not exist.")
    }

    return matchingFiles
}

internal fun Project.getThirdPartyXCommLibs() : List<String> {
    // $rootDir.parentFile/third_party/**/lib/android
    val thirdPartyLibs = mutableListOf<String>()
    val thirdPartyDir = File("${rootDir.parentFile}/third_party/")
    if (thirdPartyDir.exists()) {
        val thirdPartyDirs = thirdPartyDir.listFiles()
        for (thirdParty in thirdPartyDirs) {
            if (!thirdParty.name.endsWith("comm")) {
                continue
            }
            if (thirdParty.isDirectory) {
                val libDir = File(thirdParty, "lib/android")
                if (libDir.exists()) {
                    thirdPartyLibs.add(libDir.absolutePath)
                }
            }
        }
    }
    return thirdPartyLibs
}
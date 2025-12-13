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

import com.ccgo.gradle.buildlogic.common.utils.execCommand
import com.ccgo.gradle.buildlogic.common.utils.getFileParent
import com.ccgo.gradle.buildlogic.common.utils.getLocalProperties
import org.gradle.api.Project

/**
 * Configures the native push for the project.
 */
internal fun Project.configureNativePush() {
    tasks.register("rmSo") {
        doLast {
            var commLoadAbi = getCommLoadAbi()
            var commLoadPkg = getCommLoadPkg()
            val serialMap = getSerialMap() ?: return@doLast
            for(entry in serialMap.entries) {
                execCommand("adb -s ${entry.key} shell settings put system system_ccgo_comm_loadlib 0")
            }
            if (commLoadPkg.isEmpty()) {
                throw IllegalArgumentException("[ERROR]: please set comm.load.abi and comm.load.pkg in local.properties")
            }
            commLoadPkg = commLoadPkg.replace('.', '_')
            val soList = mutableListOf("lib${cfgs.projectNameLowercase}.so")
            for (pkg in commLoadPkg.split(',')) {
                for (soFile in soList) {
                    removeAndroidDeviceFile("/sdcard/system_ccgo_comm_loadlib/common_load/${pkg}/${commLoadAbi}/${soFile}")
                }
            }
        }
    }

    // NOTE: local.properties should include comm.load.pkg and comm.load.abi
    tasks.register("pushSo") {
        val rmSo = tasks.getByName("rmSo")
        subprojects.find { it.name == "main_android_sdk" }?.let {
            val buildLibrariesForMain = it.tasks.getByName("buildLibrariesForMain")
            rmSo.dependsOn(buildLibrariesForMain)
            dependsOn(buildLibrariesForMain)
        }
        dependsOn(rmSo)

        doLast {
            val commLoadAbi = getCommLoadAbi()
            var commLoadPkg = getCommLoadPkg()
            val serialMap = getSerialMap() ?: return@doLast
            for(entry in serialMap.entries) {
                execCommand("adb -s ${entry.key} shell settings put system system_ccgo_comm_loadlib 1")
                // to get write permission
                for (pkg in commLoadPkg.split(',')) {
                    println("adb -s ${entry.key} shell pm grant $pkg android.permission.WRITE_EXTERNAL_STORAGE")
                    execCommand("adb -s ${entry.key} shell pm grant $pkg android.permission.WRITE_EXTERNAL_STORAGE")
                }
            }
            if (commLoadPkg.isEmpty()) {
                throw IllegalArgumentException("[ERROR]: please set comm.load.abi and comm.load.pkg in local.properties")
            }
            commLoadPkg = commLoadPkg.replace('.', '_')
            println("[pushSo] comm.load.pkg: $commLoadPkg \ncomm.load.abi: $commLoadAbi")
            val androidProjectPath = cfgs.mainCommProject.projectDir.absolutePath
            println("[pushSo] androidProjectPath:$androidProjectPath")
            val soList = mutableListOf("lib${cfgs.projectNameLowercase}.so")
            for (pkg in commLoadPkg.split(',')) {
                for (soFile in soList) {
                    pushAndroidDeviceFile("${androidProjectPath}/libs/${commLoadAbi}/${soFile}",
                        "/sdcard/system_ccgo_comm_loadlib/common_load/${pkg}/${commLoadAbi}/${soFile}")
                }
            }
        }
    }
}

private fun getSerialMap() : Map<String, String>? {
    val content = execCommand("adb devices -l")
    if (content.isEmpty()) {
        return null
    }
    val lines = content.split("\\s*\\n\\s*")
    val serialMap = mutableMapOf<String, String>()
    for (line in lines) {
        if (line.isEmpty() || line.contains("List")) {
            continue
        }
        val ss = line.split("\\s+")
        if (ss.size > 3) {
            serialMap[ss[0]] = ss[3]
        }
    }
    return serialMap
}

internal fun removeAndroidDeviceFile(fileName: String) {
    println("remove $fileName On Phone..")
    val serialMap = getSerialMap() ?: return
    for(entry in serialMap.entries) {
        var res = execCommand("adb -s ${entry.key} shell rm $fileName")
        if(res.isEmpty()) {
            res = "success"
        }
        println(">>>Dev(${entry.value})-${entry.key} Result: " + res)
    }
}

internal fun pushAndroidDeviceFile(frosystem_ccgo_comm_loadlibme: String, toName: String) {
    println("copy $frosystem_ccgo_comm_loadlibme To Phone(${toName})..")
    val serialMap = getSerialMap() ?: return
    for(entry in serialMap.entries) {
        execCommand("adb -s ${entry.key} shell mkdir -p ${getFileParent(toName, "/")}")
        val content = execCommand("adb -s ${entry.key} push $frosystem_ccgo_comm_loadlibme $toName")
        val lines = content.split('\n')
        val lastSecLine = if (lines.size > 1) lines[-2] else ""
        val lastLine = if (lines.isNotEmpty()) lines[-1] else ""
        println(">>>Dev(${entry.value})-${entry.key} Result:\n" + lastSecLine + "\n" + lastLine)
    }
}

internal fun getCurrentPackageName(): String {
    val serialMap = getSerialMap() ?: return ""
    for(entry in serialMap.entries) {
        // only change the first one
        return execCommand("adb -s ${entry.key} shell dumpsys window windows | grep mCurrentFocus | awk -F '[ /]' '{print \$5}'")
    }
    return ""
}

// NOTE: local.properties should include comm.load.abi and comm.load.abi
internal fun Project.getCommLoadAbi(): String {
    return getLocalProperties("comm.load.abi", "arm64-v8a")
}

// NOTE: local.properties should inlucde comm.load.abi and comm.load.abi
internal fun Project.getCommLoadPkg(): String {
    return getLocalProperties("comm.load.abi", getCurrentPackageName())
}
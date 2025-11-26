//
// Copyright 2024 zhlinh and ccgo Project Authors. All rights reserved.
// Use of this source code is governed by a MIT-style
// license that can be found at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// The above copyright notice and this permission
// notice shall be included in all copies or
// substantial portions of the Software.

package com.ccgo.gradle.buildlogic.common

import com.ccgo.gradle.buildlogic.common.utils.execCommand
import org.gradle.api.Project
import java.io.File

/**
 * Tag the current commit with the current version.
 */
internal fun Project.configureTag() {
    tasks.register("tagGit") {
        doLast {
            val file = File("${rootDir.parentFile}/bin")
            if (!file.exists()) {
                file.mkdirs()
            }
            val currentTag = cfgs.currentTag
            File("${rootDir.parentFile}/target/${currentTag}").bufferedWriter().use { writer ->
                writer.write(
                    "${currentTag} info：\n" +
                            "-----------------------------\n\n" +
                            "VERSION_NAME=${cfgs.versionName}\n " +
                            "VERSION_CODE=${cfgs.versionCode}\n " +
                            "REVISION=${cfgs.revision}\n " +
                            "BRANCH_NAME=${cfgs.branchName}\n " +
                            "TAG=${currentTag}\n " +
                            "DATETIME=${cfgs.timeInfo}\n\n" +
                            "-----------------------------"
                )
            }
            if (currentTag.contains("dirty")) {
                return@doLast
            }
            execCommand("git tag -a ${currentTag} -F ${rootDir.parentFile}/target/${currentTag}")
            val head = execCommand("git show ${currentTag} | head")
            println(head)
            println("********${cfgs.projectNameUppercase} build start push tag *******")
            execCommand("git push origin ${currentTag}")
        }
    }
}

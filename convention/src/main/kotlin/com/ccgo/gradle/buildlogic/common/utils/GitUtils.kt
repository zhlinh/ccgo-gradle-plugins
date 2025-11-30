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

import java.text.SimpleDateFormat
import java.util.Date

internal fun getGitBranchName(): String {
    val result = execCommand("git rev-parse --abbrev-ref HEAD")
    return result.ifEmpty { "unknown" }
}

internal fun getGitVersionCode(): String {
    val result = execCommand("git rev-list HEAD --count")
    return result.ifEmpty { "0" }
}

internal fun getGitRevision(): String {
    val result = execCommand("git rev-parse --short HEAD")
    return result.ifEmpty { "unknown" }
}

internal fun getGitHeadTimeInfo(): String {
    val log = execCommand("git log -n1 --format=%at")
    return if (log.isNotEmpty()) {
        try {
            val timeStampOfHead = log.toLong() * 1000
            val date = Date(timeStampOfHead)
            val sdf = SimpleDateFormat("yyyy-MM-dd")
            sdf.format(date)
        } catch (e: NumberFormatException) {
            SimpleDateFormat("yyyy-MM-dd").format(Date())
        }
    } else {
        SimpleDateFormat("yyyy-MM-dd").format(Date())
    }
}

internal fun getPublishSuffix(release: Boolean): String {
    if (release) {
        return "release"
    }
    val latestTag = execCommand("git rev-list --tags --no-walk --max-count=1")
    if (latestTag.isEmpty()) {
        // No git or no tags, return default beta suffix
        return "beta.0"
    }
    val countFromLatestTag = execCommand("git rev-list ${latestTag}..HEAD --count")
    val count = countFromLatestTag.ifEmpty { "0" }
    val stat = execCommand("git diff --stat")
    val workSpaceStatus = if (stat.isEmpty()) "" else "-dirty"
    return "beta.${count}${workSpaceStatus}"
}

internal fun getCurrentTag(release: Boolean, name: String, suffix: String): String {
    if (release) {
        return "v${name}"
    }
    return "v${name}-${suffix}"
}

internal fun getGitRepoUrl(): String {
    return execCommand("git config --get remote.origin.url")
}

internal fun getGitRepoUserName(): String {
    return execCommand("git config --get user.name")
}

internal fun getGitRepoUserEmail(): String {
    return execCommand("git config --get user.email")
}

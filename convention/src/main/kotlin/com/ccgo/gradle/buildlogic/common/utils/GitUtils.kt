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
    return execCommand("git rev-parse --abbrev-ref HEAD")
}

internal fun getGitVersionCode(): String {
    return execCommand("git rev-list HEAD --count")
}

internal fun getGitRevision(): String {
    return execCommand("git rev-parse --short HEAD")
}

internal fun getGitHeadTimeInfo(): String {
    val log = execCommand("git log -n1 --format=%at")
    val timeStampOfHead = log.toLong() * 1000
    val date = Date(timeStampOfHead)
    val sdf = SimpleDateFormat("yyyy-MM-dd")
    return sdf.format(date)
}

internal fun getPublishSuffix(release: Boolean): String {
    if (release) {
        return "release"
    }
    var latestTag = execCommand("git rev-list --tags --no-walk --max-count=1")
    val countFromLatestTag = execCommand("git rev-list ${latestTag}..HEAD --count")
    val stat = execCommand("git diff --stat")
    val workSpaceStatus = if (stat.isEmpty()) "" else "-dirty"
    return "beta.${countFromLatestTag}${workSpaceStatus}"
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

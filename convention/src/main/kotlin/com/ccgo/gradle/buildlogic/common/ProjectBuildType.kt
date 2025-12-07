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

/**
 * This is shared between :app and :benchmarks module to provide configurations type safety.
 */
enum class ProjectBuildType(val applicationIdSuffix: String? = null) {
    DEBUG(".debug"),
    RELEASE,
}

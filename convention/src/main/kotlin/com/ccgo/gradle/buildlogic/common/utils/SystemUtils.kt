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

import java.lang.System
import java.lang.reflect.Field;

internal fun setSystemEnv(key: String, value: String) {
    getModifiableEnv().put(key, value)
}

internal fun setSystemEnv(map: Map<String, String>) {
    getModifiableEnv().putAll(map)
}

private fun getModifiableEnv() : MutableMap<String, String> {
    var unmodifiableEnv = System.getenv()
    val field = unmodifiableEnv.javaClass.getDeclaredField("m")
    field.setAccessible(true)
    return field.get(unmodifiableEnv) as MutableMap<String, String>
}

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

plugins {
    `kotlin-dsl` apply false
}

allprojects {
    group = property("GROUP").toString()
    version = property("VERSION_NAME").toString()
}

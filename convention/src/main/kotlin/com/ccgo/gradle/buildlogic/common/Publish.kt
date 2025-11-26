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

import com.ccgo.gradle.buildlogic.common.utils.getGitRepoUrl
import com.ccgo.gradle.buildlogic.common.utils.getGitRepoUserEmail
import com.ccgo.gradle.buildlogic.common.utils.getGitRepoUserName
import com.ccgo.gradle.buildlogic.common.utils.getGpgKeyFromKeyId
import com.ccgo.gradle.buildlogic.common.utils.getGpgKeyFromKeyRingFile
import com.ccgo.gradle.buildlogic.common.utils.getLocalProperties
import com.vanniktech.maven.publish.SonatypeHost
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.api.tasks.Exec
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.signing.SigningExtension

private const val DEFAULT_CENTRAL_DOMAIN = "central.sonatype.com"
// ./gradlew publishMainPublicationToMavenRepository --no-daemon
private const val MAIN_PUBLICATION_NAME = "main"
// ./gradlew publishTestPublicationToMavenRepository --no-daemon
private const val TEST_PUBLICATION_NAME = "test"
// ./gradlew publishMavenPublicationToMavenCentralRepository --no-daemon
private const val MAVEN_PUBLICATION_NAME = "maven"

/**
 * Configures the publishing for the project.
 */
internal fun Project.configurePublish() {
    project.afterEvaluate {
        configureSourcesAndJavaDoc()
        setSystemEnv()
        configureMaven()
        if (cfgs.commIsSignEnabled) {
            configureSign()
        }
    }
}

/**
 * Configures the signing for the project.
 */
private fun Project.configureSign() {
    extensions.configure<SigningExtension> {
        sign {
            val publishing = project.extensions.getByName("publishing") as PublishingExtension
            val signingKey = getSigningInMemoryKey()
            if (signingKey.isEmpty()) {
                println("sign signingKey is empty, skip sign publication")
                return@sign
            }
            val signingPassword = getLocalProperties("signing.password", "")
            useInMemoryPgpKeys(signingKey, signingPassword)
            publishing.publications.asMap.filter { it.key == MAIN_PUBLICATION_NAME }.forEach { (_, publication) ->
                println("sign publication: ${publication.name}")
                sign(publication as MavenPublication)
            }
        }
    }
}

/**
 * Configures the maven publishing for the project.
 *
 * config the url, username, password in local.properties, the format like
 * comm.maven.count=2
 * comm.maven.url0=https\://xxx.com/xx
 * comm.maven.username0=xxx
 * comm.maven.password0=xxx
 * comm.maven.url1=/Users/path/prebuilds/repository
 */
private fun Project.configureMaven() {
    // configure custom maven repository
    configureCustomMaven()
    // configure central maven repository
    configureCentralMaven()
}

private fun Project.configureCentralMaven() {
    configureVanniktechCentralMaven()
}


private fun Project.configureVanniktechCentralMaven() {
    // https://github.com/vanniktech/gradle-maven-publish-plugin
    extensions.configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
        coordinates(
            groupId = cfgs.commGroupId,
            artifactId = getProjectArtifactId(),
            version = cfgs.versionName
        )
        publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
        if (cfgs.commIsSignEnabled) {
            // sign after call publish
            signAllPublications()
        }
    }
}

private fun Project.configureCustomMaven() {
    // execute "./gradlew publishMainPublicationToMavenRepository"
    extensions.configure<PublishingExtension> {
        repositories {
            val mavenCount = getLocalProperties("comm.maven.count", "0").toInt()
            val taskName = project.gradle.startParameter.taskNames.firstOrNull()
            var validMavenCount = 0
            var lastUrl = ""
            if (mavenCount > 0) {
                for (i in 0 until mavenCount) {
                    val url = getLocalProperties("comm.maven.url${i}", "")
                    val username = getLocalProperties("comm.maven.username${i}", "")
                    val password = getLocalProperties("comm.maven.password${i}", "")
                    if (url.isEmpty()) {
                        // empty
                        continue
                    }
                    lastUrl = url
                    if (url.contains(DEFAULT_CENTRAL_DOMAIN)) {
                        // skip central.sonatype.com
                        continue
                    }
                    validMavenCount++
                    maven {
                        if (mavenCount > 1 && i != 0) {
                            this.name = "maven${i}"
                        }
                        this.url = uri(url)
                        if (username.isNotEmpty() && password.isNotEmpty()) {
                            credentials {
                                this.username = username
                                this.password = password
                            }
                        }
                    }
                }
            } else {
                if (taskName != null && taskName.contains("publish")) {
                    throw Exception( getErrorHint(hintCount = true) )
                } else {
                    println( getErrorHint(hintCount = false) )
                }
            }
            if (validMavenCount == 0 && !lastUrl.contains(DEFAULT_CENTRAL_DOMAIN)) {
                if (taskName != null && taskName.contains("publish")) {
                    throw Exception( getErrorHint(hintCount = false) )
                } else {
                    println( getErrorHint(hintCount = false) )
                }
            }
        }

        publications {
            val publishConfig = mutableMapOf(
                // main always use release
                MAIN_PUBLICATION_NAME to "target/${cfgs.getMainArchiveAarName("release")}",
            )
            if (!cfgs.isRelease) {
                // if not release, add test publication
                publishConfig[TEST_PUBLICATION_NAME] = "target/${cfgs.mainProjectArchiveAarName}"
            }
            (publications.getByName(MAVEN_PUBLICATION_NAME) as? MavenPublication)?.apply {
                configurePublication(this, MAVEN_PUBLICATION_NAME,
                    publishConfig[MAIN_PUBLICATION_NAME]!!, false)
            }

            for ((publishName, artifactName) in publishConfig) {
                register(publishName, MavenPublication::class) {
                    configurePublication(this, publishName,
                        artifactName, true)
                }  // MavenPublication
            }  // for
        }  // publications
    }  // PublishingExtension
}

private fun Project.configurePublication(
    mavenPublication: MavenPublication,
    publishName: String,
    artifactName: String,
    addFromComponent: Boolean = true
) {
    with(mavenPublication) {
        if (addFromComponent) {
            groupId = cfgs.commGroupId
            artifactId = getProjectArtifactId()
            if (publishName != TEST_PUBLICATION_NAME) {
                version = cfgs.versionName
            } else {
                version = "${cfgs.versionName}-TEST"
            }
            from(components["java"])
        }
        val arts = artifacts.filter {
            it.file.name.endsWith("javadoc.jar")
                || it.file.name.endsWith("sources.jar")
                || it.file.name.endsWith(".module")
        }
        components["java"].apply {
            setArtifacts(arts)
            artifact(artifactName)
        }
        setArtifacts(arts)
        artifact(artifactName)
        tasks.withType<GenerateModuleMetadata> {
            enabled = false
        }
        println("PublishName: $publishName")
        artifacts.forEach {
            println("FilteredArtifact: ${it.file.name}")
        }
        println("------------")

        // add pom
        pom { configurePom(this, artifactName, mavenPublication.version) }  // pom
    }
}

private fun Project.configurePom(config: MavenPom,
                                 artifactName: String = "",
                                 versionName: String = "") {
    with(config) {
        val gitUrl = getGitRepoUrl()
        name = cfgs.projectName
        description = "The ${cfgs.projectName} SDK"
        url = gitUrl
        version = versionName
        packaging = if (artifactName.contains(".")) artifactName.split(".").last() else "aar"

        licenses {
            license {
                name = "MIT License"
                url = "https://opensource.org/licenses/MIT"
            }
        }
        developers {
            developer {
                id = getGitRepoUserName()
                name = getGitRepoUserName()
                email = getGitRepoUserEmail()
            }
        }
        scm {
            url = gitUrl
            connection = "scm:git:${gitUrl}"
            developerConnection = "scm:git:${gitUrl}"
        }

        withXml {
            println("groupId: ${cfgs.commGroupId}")
            println("artifactId: ${getProjectArtifactId()}")
            println("version: $versionName")
            println("artifactName: $artifactName")
            println("------------")
            val commDependencies = cfgs.commDependenciesAsList
            if (commDependencies.isEmpty()) {
                return@withXml
            }
            val dependenciesNode = asNode().appendNode("dependencies")
            for (dependency in commDependencies) {
                val dependencyNode = dependenciesNode.appendNode("dependency")
                val parts = dependency.split(":")
                println("add dependency: $parts")
                dependencyNode.appendNode("groupId", parts[0])
                dependencyNode.appendNode("artifactId", parts[1])
                dependencyNode.appendNode("version", parts[2])
            }  // dependencies
        }  // withXml
    }  // MavenPom
}

private fun getErrorHint(hintCount: Boolean): String {
    val hintCountStr = if (hintCount) "\ncomm.maven.count=1" else ""
    return "【Error】[Publish-Configuration] failed to get comm.maven.count, you need to add" +
            " at least one maven config in local.properties" +
            hintCountStr +
            "\ncomm.maven.url0=<MAVEN_URL>" +
            "\ncomm.maven.username0=<USERNAME>" +
            "\ncomm.maven.password0=<PASSWORD>"
}

private fun Project.getSigningInMemoryKey() : String {
    var signingKey = getLocalProperties("signing.key", "")
    val signingPassword = getLocalProperties("signing.password", "")
    if (signingKey.isNotEmpty()) {
        return signingKey
    }
    // if empty, try another way
    var signingKeyId = getLocalProperties("signing.keyId", "")
    // 1. get key from keyId
    signingKey = getGpgKeyFromKeyId(signingKeyId, signingPassword)
    println("signing.keyId:$signingKeyId to signing.key:$signingKey")
    if (signingKey.isNotEmpty()) {
        return signingKey
    }
    val secretKeyRingFile = getLocalProperties("signing.secretKeyRingFile", "")
    if (secretKeyRingFile.isEmpty()) {
        return ""
    }
    // 2. get key from keyRingFile
    signingKey = getGpgKeyFromKeyRingFile(secretKeyRingFile, signingPassword)
    println("signing.secretKeyRingFile:$secretKeyRingFile to signing.key:$signingKey")
    return signingKey
}

private fun Project.configureSourcesAndJavaDoc() {
    if (project.plugins.hasPlugin("com.android.library")) {
        val androidLibrary = project.extensions.findByType(com.android.build.api.dsl.LibraryExtension::class.java)!!
        androidLibrary.publishing {
            singleVariant(ProjectFlavor.prod.name) {
                withSourcesJar()
                withJavadocJar()
            }
        }
    } else if (project.plugins.hasPlugin("java")) {
        val javaLibrary = project.extensions.findByType(org.gradle.api.plugins.JavaPluginExtension::class.java)!!
        javaLibrary.withSourcesJar()
        javaLibrary.withJavadocJar()
    }
}

private fun Project.setSystemEnv() {
    // set environment variable
    val envMap = mapOf(
        "ORG_GRADLE_PROJECT_mavenCentralUsername" to getLocalProperties("mavenCentralUsername", ""),
        "ORG_GRADLE_PROJECT_mavenCentralPassword" to getLocalProperties("mavenCentralPassword", ""),
        "ORG_GRADLE_PROJECT_signingInMemoryKey" to getSigningInMemoryKey(),
        "ORG_GRADLE_PROJECT_signingInMemoryKeyId" to getLocalProperties("signing.keyId", ""),
        "ORG_GRADLE_PROJECT_signingInMemoryKeyPassword" to getLocalProperties("signing.password", "")
    )
    // or set in ~/.gradle/gradle.properties
    // mavenCentralUsername
    // mavenCentralPassword
    // signing.keyId
    // signing.password
    // signing.secretKeyRingFile
    // detail in https://vanniktech.github.io/gradle-maven-publish-plugin/central/#configuring-the-pom
    tasks.withType(Exec::class.java).configureEach {
        // set env
        environment(envMap)
        envMap.forEach { (key, value) ->
            if (value.isNotEmpty()) {
                System.setProperty(key, value)
            }
        }
    }
}

private fun Project.getProjectArtifactId() : String {
    val combinedSuffix = arrayOf(cfgs.commPublishChannelDesc.lowercase(), cfgs.androidStlSuffix.lowercase())
        .filter { it.isNotEmpty() }
        .joinToString("-") {
            it.removePrefix("-")
        }
    val appendChar = if (combinedSuffix.isNotEmpty()) "-" else ""
    return "${cfgs.projectNameLowercase}${appendChar}${combinedSuffix}"
}

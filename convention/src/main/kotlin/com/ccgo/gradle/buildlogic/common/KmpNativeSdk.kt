package com.ccgo.gradle.buildlogic.common

import org.gradle.api.Project
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

internal fun Project.kmpNativeSdk(platform: String): File? =
    (findProperty("ccgoNativeSdk.$platform") as String?)?.let { path ->
        file(path).also { require(it.isDirectory) { "Native SDK directory does not exist: $it" } }
    }

private fun Element.elements(): List<Element> =
    (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }

private fun Element.dictionary(): Map<String, Element> = elements().chunked(2).associate {
    require(it.size == 2 && it[0].tagName == "key") { "Invalid XCFramework plist dictionary" }
    it[0].textContent to it[1]
}

/** Select an XCFramework slice using its manifest, including simulator/device and architecture. */
private fun xcframeworkLibrary(bundle: File, platform: String, arch: String, device: String): File? {
    val factory = DocumentBuilderFactory.newInstance().apply {
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        isExpandEntityReferences = false
    }
    val plist = factory.newDocumentBuilder().parse(bundle.resolve("Info.plist"))
    val entries = plist.documentElement.elements().single().dictionary()["AvailableLibraries"]
        ?: error("Missing AvailableLibraries in $bundle")
    for (entry in entries.elements()) {
        val slice = entry.dictionary()
        val arches = slice["SupportedArchitectures"]?.elements()?.map { it.textContent }.orEmpty()
        val variant = slice["SupportedPlatformVariant"]?.textContent.orEmpty()
        if (slice["SupportedPlatform"]?.textContent != platform || arch !in arches) continue
        if ((device == "simulator") != (variant == "simulator")) continue
        val identifier = slice["LibraryIdentifier"]?.textContent ?: error("Missing LibraryIdentifier")
        val library = slice["LibraryPath"]?.textContent ?: error("Missing LibraryPath")
        val path = bundle.resolve(identifier).resolve(library).canonicalFile
        require(path.toPath().startsWith(bundle.canonicalFile.toPath())) { "Unsafe XCFramework library path" }
        val binary = if (path.extension == "framework") path.resolve(path.nameWithoutExtension) else path
        require(binary.isFile) { "Missing native SDK library: $binary" }
        return binary
    }
    return null
}

internal fun nativeSdkStaticLibrary(
    sdk: File, platform: String, arch: String, device: String, projectName: String
): File {
    val root = sdk.resolve("lib/$platform/static")
    if (platform == "ios" || platform == "macos") {
        val bundle = root.resolve("$projectName.xcframework")
        require(bundle.isDirectory) { "Missing static XCFramework: $bundle" }
        return xcframeworkLibrary(bundle, platform, arch, device)
            ?: error("No $platform $device $arch slice in $bundle")
    }
    val arches = if (arch == "arm64") listOf("aarch64", "arm64") else listOf(arch)
    return arches.map { root.resolve("$it/lib$projectName.a") }.firstOrNull { it.isFile }
        ?: error("Missing static library for $platform $arch in $root")
}

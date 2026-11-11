#!/usr/bin/env python3
"""Test imported native SDK slice selection without an NDK or Apple toolchain."""

from pathlib import Path
import plistlib
import subprocess
import tempfile


def main():
    plugin = Path(__file__).resolve().parents[1]
    with tempfile.TemporaryDirectory(prefix="ccgo-native-sdk-") as temporary:
        root = Path(temporary)
        sdk = root / "sdk"
        linux = sdk / "lib/linux/static/aarch64/libdemo.a"
        linux.parent.mkdir(parents=True)
        linux.write_bytes(b"linux-arm64")
        bundle = sdk / "lib/ios/static/demo.xcframework"
        slices = []
        for identifier, variant, arches in [
            ("ios-arm64", None, ["arm64"]),
            ("ios-arm64_x86_64-simulator", "simulator", ["arm64", "x86_64"]),
        ]:
            library = bundle / identifier / "libdemo.a"
            library.parent.mkdir(parents=True)
            library.write_bytes(identifier.encode())
            entry = {"LibraryIdentifier": identifier, "LibraryPath": "libdemo.a",
                     "SupportedArchitectures": arches, "SupportedPlatform": "ios"}
            if variant:
                entry["SupportedPlatformVariant"] = variant
            slices.append(entry)
        (bundle / "Info.plist").write_bytes(plistlib.dumps({"AvailableLibraries": slices}))
        classes = str(plugin / "convention/build/classes/kotlin/main").replace("\\", "/")
        (root / "settings.gradle").write_text("rootProject.name = 'native-sdk-test'\n")
        (root / "build.gradle").write_text("""
buildscript { dependencies { classpath files('CLASSES') } }
tasks.register('checkNativeSdks') {
    doLast {
        def klass = Class.forName('com.ccgo.gradle.buildlogic.common.KmpNativeSdkKt', true,
            project.buildscript.classLoader)
        def select = klass.getDeclaredMethod('nativeSdkStaticLibrary', File, String, String, String, String)
        def sdk = file('sdk')
        def library = { platform, arch, device -> select.invoke(null, sdk, platform, arch, device, 'demo') }
        assert library('linux', 'arm64', '').text == 'linux-arm64'
        assert library('ios', 'arm64', 'device').text == 'ios-arm64'
        assert library('ios', 'arm64', 'simulator').text == 'ios-arm64_x86_64-simulator'
        assert library('ios', 'x86_64', 'simulator').text == 'ios-arm64_x86_64-simulator'
        try {
            library('ios', 'x86_64', 'device')
            throw new GradleException('Missing device slice was accepted')
        } catch (java.lang.reflect.InvocationTargetException expected) {
            assert expected.cause.message.contains('No ios device x86_64 slice')
        }
        println('Native SDK selection passed: Linux arch alias, Apple device/simulator, missing slice')
    }
}
""".replace("CLASSES", classes.replace("'", "\\'")))
        subprocess.run([str(plugin / "gradlew"), ":convention:compileKotlin", "--offline",
                        "--no-daemon", "--console=plain"], cwd=plugin, check=True)
        subprocess.run([str(plugin / "gradlew"), "-p", str(root), "checkNativeSdks", "--offline",
                        "--no-daemon", "--console=plain"], cwd=plugin, check=True)


if __name__ == "__main__":
    main()

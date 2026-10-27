#!/usr/bin/env python3
"""Smoke-test SDK publishing without building sources or uploading remotely.

Run: python3 scripts/test-kmp-sdk-publish.py [--offline]
"""
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile


def main():
    plugin_root = Path(__file__).resolve().parents[1]
    with tempfile.TemporaryDirectory(prefix="ccgo-kmp-sdk-test-") as temporary:
        root = Path(temporary)
        project = root / "kmp"
        project.mkdir()
        groovy_path = "'" + str(plugin_root).replace("\\", "\\\\").replace("'", "\\'") + "'"
        (project / "settings.gradle").write_text(
            "pluginManagement { repositories { mavenCentral(); gradlePluginPortal() }; "
            "includeBuild(" + groovy_path + ") }\n"
            "rootProject.name = 'sample-kmp'\n"
        )
        (root / "CCGO.toml").write_text(
            '[project]\nname="sample"\nversion="9.9.9"\n'
            '[publish.kmp.maven]\ngroup_id="test.ccgo.sdk"\nartifact_id="sample-kmp"\n'
        )
        (project / "build.gradle").write_text("""
plugins { id 'com.mojeter.ccgo.gradle.kmp.publish' }
group = 'test.ccgo.sdk'
version = '9.9.9'
tasks.register('mustNotBuild') {
    doLast { throw new GradleException('SDK publishing triggered a source build') }
}
tasks.register('sourceJar', Jar) { dependsOn('mustNotBuild') }
publishing {
    publications {
        old(MavenPublication) {
            groupId = 'test.ccgo.sdk'; artifactId = 'wrong'; version = '9.9.9'
            artifact(tasks.named('sourceJar'))
        }
    }
}
tasks.withType(Sign).configureEach { enabled = false }
""")
        artifacts = []
        for target, extension in [("android", "aar"), ("desktop", "jar"), ("iosarm64", "klib")]:
            artifact = root / (target + "." + extension)
            artifact.write_bytes(("prebuilt-" + target).encode())
            artifacts.append({"target": target, "path": str(artifact), "classifier": None})
        manifest = root / "manifest.json"
        manifest.write_text(json.dumps({"project": "sample", "version": "0.0.5", "artifacts": artifacts}))
        environment = os.environ.copy()
        environment.update({
            "MAVEN_LOCAL_PATH": str(root / "local"),
            "CCGO_MAVEN_URL": (root / "custom").as_uri(),
            "SIGNING_IN_MEMORY_KEY": "",
            "SIGNING_IN_MEMORY_KEY_PASSWORD": "",
        })
        command = [str(plugin_root / "gradlew"), "--no-daemon", "--no-configuration-cache"]
        if "--offline" in sys.argv[1:]:
            command.append("--offline")
        command += ["-p", str(project), "-PccgoKmpSdkManifest=" + str(manifest),
                    "ccgoPublishToMavenLocal", "ccgoPublishToMavenCustom",
                    "publishAllPublicationsToNmcpRepository"]
        subprocess.run(command, cwd=plugin_root, env=environment, check=True)
        for repository in [root / "local", root / "custom", project / "build/nmcp/m2"]:
            for artifact in artifacts:
                name = "sample-kmp-" + artifact["target"]
                location = repository / "test/ccgo/sdk" / name / "0.0.5"
                extension = Path(artifact["path"]).suffix
                assert (location / (name + "-0.0.5" + extension)).read_bytes() == Path(artifact["path"]).read_bytes()
                pom = (location / (name + "-0.0.5.pom")).read_text()
                assert "<version>0.0.5</version>" in pom
            assert not (repository / "test/ccgo/sdk/wrong").exists()
        print("SDK smoke test passed: local/custom/Central staging, exact prebuilt bytes, no source build")


if __name__ == "__main__":
    main()

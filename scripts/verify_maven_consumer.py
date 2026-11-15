"""Compile an Android app using only the published dependency and its transitive dependencies."""
import argparse
import json
from pathlib import Path
import re
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repository', required=True, help='Maven directory or HTTPS repository URL')
    parser.add_argument('--coordinate', required=True, help='group:artifact:version')
    args = parser.parse_args()
    group, artifact, version = args.coordinate.split(':')
    repository = args.repository if args.repository.startswith('https://') else Path(args.repository).resolve().as_uri()
    versions = (ROOT/'gradle/libs.versions.toml').read_text()
    agp = re.search(r'^agp = "([^"]+)"', versions, re.M).group(1)
    kotlin = re.search(r'^kotlin = "([^"]+)"', versions, re.M).group(1)
    min_sdk = int(re.search(r'^minSdk = "(\d+)"', versions, re.M).group(1))
    with tempfile.TemporaryDirectory(prefix='seven-digits-consumer-') as temporary:
        target = Path(temporary)
        shutil.copyfile(ROOT/'gradlew', target/'gradlew')
        (target/'gradlew').chmod(0o755)
        shutil.copytree(ROOT/'gradle/wrapper', target/'gradle/wrapper')
        if (ROOT/'local.properties').exists():
            shutil.copyfile(ROOT/'local.properties', target/'local.properties')
        (target/'settings.gradle.kts').write_text(f'''
pluginManagement {{ repositories {{ google(); mavenCentral(); gradlePluginPortal() }} }}
dependencyResolutionManagement {{
    repositories {{
        google()
        mavenCentral()
        maven {{
            url = uri({json.dumps(repository)})
            content {{ includeGroup({json.dumps(group)}) }}
        }}
    }}
}}
rootProject.name = "published-scanner-consumer"
''')
        (target/'gradle.properties').write_text('android.useAndroidX=true\norg.gradle.jvmargs=-Xmx2g\n')
        (target/'build.gradle.kts').write_text(f'''
buildscript {{ dependencies {{ classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:{kotlin}") }} }}
plugins {{ id("com.android.application") version "{agp}" }}
android {{
    namespace = "dev.sevendigits.consumercheck"
    compileSdk = 37
    defaultConfig {{ applicationId = "dev.sevendigits.consumercheck"; minSdk = {min_sdk}; targetSdk = 37 }}
    compileOptions {{ sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }}
}}
dependencies {{ implementation({json.dumps(args.coordinate)}) }}
tasks.register("verifyScannerDependencies") {{
    doLast {{
        val modules = configurations.getByName("debugRuntimeClasspath").resolvedConfiguration
            .resolvedArtifacts.map {{ it.moduleVersion.id.name }}.toSet()
        check(modules.containsAll(setOf("litert", "text-recognition", "kotlinx-coroutines-core-jvm"))) {{
            "Missing runtime dependencies: $modules"
        }}
        println("Verified published scanner and transitive LiteRT, ML Kit, and coroutines dependencies")
    }}
}}
''')
        (target/'src/main/kotlin/dev/sevendigits/consumercheck').mkdir(parents=True)
        (target/'src/main/AndroidManifest.xml').write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android"><application /></manifest>')
        (target/'src/main/kotlin/dev/sevendigits/consumercheck/ScannerClient.kt').write_text('''
package dev.sevendigits.consumercheck
import android.content.Context
import android.graphics.Bitmap
import dev.sevendigits.Lines
import dev.sevendigits.RecognitionOptions
import dev.sevendigits.RecognitionSource
import dev.sevendigits.SevenDigitsRecognizer
class ScannerClient {
    suspend fun read(context: Context, bitmap: Bitmap): Lines {
        val scanner = SevenDigitsRecognizer.create(context)
        val visual = scanner.recognizeVisual(bitmap, RecognitionOptions(recognizePrintedText = true))
        visual.regions.flatMap { it.characters }.filter { it.source == RecognitionSource.GENERAL_TEXT }
        return visual.toLines()
    }
}
''')
        subprocess.run(['./gradlew', 'assembleDebug', 'verifyScannerDependencies', '--no-daemon', '--console=plain'],
                       cwd=target, check=True)
        print(f'Android consumer built successfully from {args.coordinate} at {repository}')


if __name__ == '__main__':
    main()

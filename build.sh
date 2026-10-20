#!/usr/bin/env bash
# Builds Void Web gently (niced), without writing to ~/.gradle or ~/.android. The first build downloads Gradle 9,
# the Android plugin and GeckoView (~100 MB) into .gradle-home; after that, pass --offline to stay off the network.
# Usage: ./build.sh [assembleRelease|assembleDebug|testDebugUnitTest|...] [-Pabi=x86_64 for the emulator] [--offline]
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk}
export JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=${TMPDIR:-/tmp}"
export ANDROID_USER_HOME="$PWD/.android-home"
export GRADLE_USER_HOME="$PWD/.gradle-home"
args=("$@")
[ ${#args[@]} -eq 0 ] && args=(assembleRelease)
nice -n 19 ./gradlew --no-daemon -Pkotlin.compiler.execution.strategy=in-process "${args[@]}"
# Copy a finished release APK next to the project, like the other VOID apps.
apk=app/build/outputs/apk/release/app-release.apk
if [[ " ${args[*]} " == *" assembleRelease "* && -f $apk ]]; then cp "$apk" VoidWeb.apk; echo "VoidWeb.apk ($(du -h VoidWeb.apk | cut -f1))"; fi

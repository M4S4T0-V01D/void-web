#!/usr/bin/env bash
# Releases app changes: bumps versionCode and the patch version, commits, and pushes. GitHub Actions then builds,
# signs and publishes the release, and phones see it in Settings › Updates.
# (New Firefox engines need nothing: the workflow checks Mozilla every day and releases those by itself.)
# Usage: ./release.sh "What changed"
set -euo pipefail
cd "$(dirname "$0")"
notes=${1:?Usage: ./release.sh \"What changed\"}
git pull --ff-only
code=$(sed -n 's/^versionCode=//p' version.properties)
name=$(sed -n 's/^versionName=//p' version.properties)
newname="${name%.*}.$(( ${name##*.} + 1 ))"
sed -i "s/^versionCode=.*/versionCode=$((code + 1))/; s/^versionName=.*/versionName=$newname/" version.properties
git add -A
git commit -m "$notes" -m "Void Web $newname"
git push
echo "Pushed Void Web $newname; GitHub Actions is building it now."

#!/bin/bash
# Run from targets/<target>: publishes the compile classpath on the branch mc-libs-<target>, once
# (for compiling locally, where the Maven repositories cannot be reached). Usage: export_libs.sh <target> <token> <repo>
T=$1; TOKEN=$2; REPO=$3
URL="https://x-access-token:${TOKEN}@github.com/${REPO}.git"
if git ls-remote --exit-code --heads "$URL" "mc-libs-$T" >/dev/null 2>&1; then echo "mc-libs-$T already exported"; exit 0; fi
./gradlew exportLibs --no-daemon -Porg.gradle.java.installations.fromEnv=JAVA_HOME_17_X64,JAVA_HOME_21_X64,JAVA_HOME_25_X64 || exit 1
cd build/export || exit 1
find . -size +95M -print -delete
git init -q -b "mc-libs-$T"
git config user.email "actions@github.com"
git config user.name "github-actions"
git add -A && git commit -qm "libs $T"
git push -q -f "$URL" "mc-libs-$T"
rm -rf .git
echo "exported mc-libs-$T"

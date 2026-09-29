#!/usr/bin/env bash

set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"

# Force Java 21 environment for this script execution
force_java21() {
    if [ -n "${JAVA21_HOME:-}" ]; then
        export JAVA_HOME="$JAVA21_HOME"
    elif command -v /usr/libexec/java_home &>/dev/null; then
        export JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
    elif [ -d "/usr/lib/jvm/java-21-openjdk" ]; then
        export JAVA_HOME="/usr/lib/jvm/java-21-openjdk"
    elif [ -d "/usr/lib/jvm/java-21-openjdk-amd64" ]; then
        export JAVA_HOME="/usr/lib/jvm/java-21-openjdk-amd64"
    fi

    if [ -n "${JAVA_HOME:-}" ]; then
        export PATH="$JAVA_HOME/bin:$PATH"
    fi
}

# The wrapper loses its exec bit on some checkouts (notably fresh Windows clones).
[ -x ./gradlew ] || chmod +x gradlew

version() { grep -E '^project_base_version=' gradle.properties | cut -d= -f2 | tr -d '[:space:]'; }

task_build() {
    ./gradlew packageJar
    echo
    echo "Jar written to build/libs/$(version)/"
}

# JUnit + JaCoCo over both modules; no packageJar, no reobf, and compileJava is up to date on a warm tree.
# --continue keeps the second module running when the first has failures, so the summary is complete.
task_test() {
    ./gradlew --continue test jacocoTestReport || true
    echo
    python3 tools/test-summary.py "$@"
}

# Removes every build output, not just the root build/ directory. `gradlew clean` only owns the root
# project's build/, so bin/, run/, the Gradle caches and the buildSrc/common outputs all survive it -
# which is why this is the only clean offered.
task_clean() {
    local dirs=(build bin run .gradle
                common/build common/.gradle
                buildSrc/build buildSrc/.gradle buildSrc/.kotlin)

    if [ "${1:-}" != "--force" ]; then
        echo "Removes: ${dirs[*]}"
        echo "The next build re-decompiles Minecraft and will take several minutes."
        read -r -p "Continue? [y/N] " reply
        case "$reply" in
            [yY]*) ;;
            *) echo "Aborted."; return 0 ;;
        esac
    fi

    rm -rf "${dirs[@]}"
    echo "Done."
}

menu() {
    cat <<'MENU'

  Impetus - Minecraft 1.12.2 (Forge)

    1) Build
    2) Test
    3) Clean
    4) Quit

MENU
}

dispatch() {
    case "$1" in
        1|build) task_build ;;
        2|test) task_test ;;
        3|clean) task_clean "${2-}" ;;
        4|q|quit|exit) return 1 ;;
        *) echo "Unknown option: $1" ;;
    esac
    return 0
}

force_java21

# Non-interactive form, e.g. ./run.sh build or ./run.sh test --all - keeps the script usable from CI and aliases.
# Such callers have already stated their intent, so clean skips the confirmation there.
if [ $# -gt 0 ]; then
    case "$1" in
        2|test) shift; task_test "$@"; exit $? ;;
    esac
    dispatch "$1" --force
    exit $?
fi

while true; do
    menu
    read -r -p "  Select: " choice
    echo
    dispatch "$choice" || break
done
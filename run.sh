#!/usr/bin/env bash
# ./run.sh opens the menu; ./run.sh build | test [--all] | clean runs one task and exits with its status
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

# The wrapper loses its exec bit on some checkouts (notably fresh Windows clones); Gradle provisions JDK 25 itself
[ -x ./gradlew ] || chmod +x gradlew

build() {
    local version
    version=$(grep -E '^mod_version=' gradle.properties | cut -d= -f2 | tr -d '[:space:]')
    if ! ./gradlew packageJar; then
        echo -e "\nBuild failed; no jar was written."
        return 1
    fi
    echo -e "\nJar written to build/libs/$version/"
}

# JUnit + JaCoCo over both modules; --continue keeps the second module running when the first fails, so the summary is complete
test_all() {
    ./gradlew --continue test jacocoTestReport || true
    echo
    python3 tools/test-summary.py "$@"
}

# Every build output, not just build/: `gradlew clean` leaves run/, the Unimined caches and common/ behind
clean() {
    local dirs=(build bin run .gradle common/build common/.gradle)
    if [ "${1:-}" != "--force" ]; then
        echo "Removes: ${dirs[*]}"
        echo "The next build re-runs Unimined's Cleanroom setup and will take several minutes."
        read -r -p "Continue? [y/N] " reply
        [[ "$reply" == [yY]* ]] || { echo "Aborted."; return 0; }
    fi
    rm -rf "${dirs[@]}"
    echo "Done."
}

# Non-interactive callers have already stated their intent, so clean skips its confirmation there
if [ $# -gt 0 ]; then
    task=$1
    shift
    case "$task" in
        build) build ;;
        test) test_all "$@" ;;
        clean) clean --force ;;
        *) echo "Usage: ./run.sh [build | test [--all] | clean]"; exit 2 ;;
    esac
    exit
fi

# Tasks run inside `||` here, where bash ignores set -e, so a failure is reported and the menu stays up
while true; do
    echo -e "\n  Impetus - Cleanroom 1.12.2 (Java 25, LWJGL3)\n\n    1) Build\n    2) Test\n    3) Clean\n    4) Quit\n"
    read -r -p "  Select: " choice
    echo
    case "$choice" in
        1) build || true ;;
        2) test_all || true ;;
        3) clean || true ;;
        4|q) exit 0 ;;
        *) echo "Unknown option: $choice" ;;
    esac
done

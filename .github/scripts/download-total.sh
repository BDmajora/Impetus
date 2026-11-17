#!/usr/bin/env bash
# Adds Modrinth, CurseForge and GitHub release downloads into one Shields.io endpoint file
# Usage: download-total.sh <output dir>
# Env: GITHUB_REPOSITORY (owner/name, required), GITHUB_TOKEN (optional, lifts the API rate limit),
#      MODRINTH_PROJECT (slug or id), CURSEFORGE_PROJECT_ID (numeric), CURSEFORGE_API_KEY (optional)
# An unset Modrinth or CurseForge id skips that site; a configured site that fails stops the run,
# so the published badge keeps its last good total instead of dropping
set -euo pipefail

out=${1:?usage: download-total.sh <output dir>}
repo=${GITHUB_REPOSITORY:?GITHUB_REPOSITORY must be owner/name}
mkdir -p "$out"

# Modrinth asks API clients to name themselves in the User-Agent
fetch() { curl -fsSL --retry 3 --retry-delay 5 --max-time 30 -A "$repo download badge" "$@"; }

modrinth=0
if [[ -n "${MODRINTH_PROJECT:-}" ]]; then
    modrinth=$(fetch "https://api.modrinth.com/v2/project/${MODRINTH_PROJECT}" | jq -e '.downloads')
fi

curseforge=0
if [[ -n "${CURSEFORGE_PROJECT_ID:-}" ]]; then
    if [[ -n "${CURSEFORGE_API_KEY:-}" ]]; then
        # Official API; downloadCount can arrive as a float
        curseforge=$(fetch -H "x-api-key: ${CURSEFORGE_API_KEY}" "https://api.curseforge.com/v1/mods/${CURSEFORGE_PROJECT_ID}" \
            | jq -e '.data.downloadCount | floor')
    else
        # Keyless mirror; a project it hasn't seen yet answers "queued" with no total, which fails this run
        # and is picked up by the next one
        curseforge=$(fetch "https://api.cfwidget.com/${CURSEFORGE_PROJECT_ID}" | jq -e '.downloads.total')
    fi
fi

# Release asset downloads only; clones and the automatic source archives are not counted by GitHub
github=0
auth=()
[[ -n "${GITHUB_TOKEN:-}" ]] && auth=(-H "Authorization: Bearer ${GITHUB_TOKEN}")
page=1
while :; do
    body=$(fetch "${auth[@]}" -H 'Accept: application/vnd.github+json' \
        "https://api.github.com/repos/${repo}/releases?per_page=100&page=${page}")
    count=$(jq 'length' <<<"$body")
    (( count == 0 )) && break
    github=$(( github + $(jq '[.[].assets[].download_count] | add // 0' <<<"$body") ))
    (( count < 100 )) && break
    page=$(( page + 1 ))
done

total=$(( modrinth + curseforge + github ))

# 950 stays 950, 12345 reads 12.3k; truncated rather than rounded so the badge never overstates
message=$(awk -v n="$total" '
    function short(d, unit) { return int(n * 10 / d) / 10 unit }
    BEGIN {
        if (n < 1e3) print n
        else if (n < 1e6) print short(1e3, "k")
        else if (n < 1e9) print short(1e6, "M")
        else print short(1e9, "B")
    }')

# Shields rejects unknown keys in an endpoint file, so the per-site breakdown goes in its own file
jq -n --arg message "$message" \
    '{schemaVersion: 1, label: "downloads", message: $message, color: "blue", cacheSeconds: 3600}' \
    > "$out/downloads.json"
jq -n --argjson total "$total" --argjson modrinth "$modrinth" --argjson curseforge "$curseforge" --argjson github "$github" \
    '{total: $total, modrinth: $modrinth, curseforge: $curseforge, github: $github}' \
    > "$out/downloads-detail.json"

echo "modrinth=$modrinth curseforge=$curseforge github=$github total=$total ($message)"

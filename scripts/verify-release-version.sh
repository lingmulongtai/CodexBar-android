#!/usr/bin/env bash
set -euo pipefail

version_name="$(sed -nE 's/^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/p' app/build.gradle.kts)"
if ! [[ "$version_name" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || [ "${GITHUB_REF_NAME:-}" != "v$version_name" ]; then
  printf 'Release tag must match the Android versionName (expected v%s).\n' "$version_name" >&2
  exit 1
fi
printf 'Verified Android release version %s.\n' "$version_name"

#!/usr/bin/env bash
# Reproducible-build check (F-Droid Verified route): two clean builds from the
# same commit must produce byte-identical APK SHA-256s.
#
# Why unsigned: AGP 8.x signs with RSA-PSS, whose random salt makes the APK
# Signing Block differ on every build even when every zip entry is identical
# (measured: 164/164 entries CRC-equal, whole-file SHA still differed). So the
# comparison is done on app-release-unsigned.apk, which is also how F-Droid's
# own check works (signature-stripped comparison via apksigcopier).
#
# Usage: scripts/verify-reproducible.sh   (run anywhere; cd's to repo root)
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

git diff --quiet --exit-code || { echo "FAIL: working tree is dirty"; exit 1; }

export SOURCE_DATE_EPOCH="$(git log -1 --format=%ct)"
echo "SOURCE_DATE_EPOCH=$SOURCE_DATE_EPOCH"

build_once() {
  local tag="$1"
  ./gradlew clean assembleRelease -PunsignedRelease --no-daemon \
      > "/tmp/pt-rb-build-$tag.log" 2>&1 || {
    echo "FAIL: build $tag failed"; tail -30 "/tmp/pt-rb-build-$tag.log"; exit 1
  }
  local apk=app/build/outputs/apk/release/app-release-unsigned.apk
  [ -f "$apk" ] || { echo "FAIL: $apk not produced (is -PunsignedRelease wired?)"; exit 1; }
  # Guard: the APK must actually carry the app, not be an empty shell.
  # No pipe here: pipefail + grep -q + unzip SIGPIPE would false-negative.
  unzip -l "$apk" > "/tmp/pt-rb-apk-$tag.txt" 2>&1 || true
  # NB: release builds shorten resource paths (res/mipmap-anydpi-v26/... becomes
  # res/xx.png), so never grep for a res/<dir> prefix here. resources.arsc is the
  # stable place to assert the app's resources actually shipped.
  for want in classes.dex AndroidManifest.xml resources.arsc; do
    if ! grep -q "$want" "/tmp/pt-rb-apk-$tag.txt"; then
      echo "FAIL: APK missing '$want'"; exit 1
    fi
  done
  sha256sum "$apk" > "/tmp/pt-rb-hash-$tag.txt"
}

build_once one
build_once two

if diff -u "/tmp/pt-rb-hash-one.txt" "/tmp/pt-rb-hash-two.txt"; then
  echo "OK: reproducible — both builds produce identical unsigned APKs:"
  cat "/tmp/pt-rb-hash-one.txt"
else
  echo "FAIL: APK hashes differ (non-reproducible build)"
  exit 1
fi

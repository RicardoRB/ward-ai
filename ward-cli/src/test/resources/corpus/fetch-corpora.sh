#!/bin/sh
# Usage: fetch-corpora.sh <manifest> <cache-dir> <out-dir>
# A manifest line is "<group> <name> <sha256> <url>". Each archive is cached as <sha256>.tar.gz,
# its sha-256 is checked on every run (a cached copy too) before anything is unpacked into
# <out-dir>/<group>/, and it is linked as <out-dir>/archives/<group>/<name>.tar.gz so that the
# smoke tests check the hash again. The sources are only unpacked, never installed or run.
set -eu
manifest=$1
cache=$2
out=$3
mkdir -p "$cache" "$out/archives"

sha256_of() {
  if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | cut -d' ' -f1; else shasum -a 256 "$1" | cut -d' ' -f1; fi
}

while read -r group name sha url; do
  file="$cache/$sha.tar.gz"
  if [ ! -f "$file" ]; then
    curl -sSfL --retry 5 --retry-all-errors -o "$file.part" "$url"
    mv "$file.part" "$file"
  fi
  actual=$(sha256_of "$file")
  if [ "$actual" != "$sha" ]; then
    rm -f "$file"
    echo "sha-256 mismatch for $name: pinned $sha, got $actual" >&2
    exit 1
  fi
  mkdir -p "$out/$group" "$out/archives/$group"
  tar -xzf "$file" -C "$out/$group"
  ln -sf "$file" "$out/archives/$group/$name.tar.gz"
done < "$manifest"

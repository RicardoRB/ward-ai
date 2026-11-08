#!/usr/bin/env bash
# Resolves the class paths of the tools that JavaParitySmokeTest runs, with the pinned versions.
# Usage: resolve-tools.sh <folder>   then run the test with -Dward.parity.tools=<folder>.
# It needs Maven (the repository's ./mvnw) and network access to Maven Central.
set -euo pipefail

folder="${1:?usage: resolve-tools.sh <folder>}"
mvnw="$(git rev-parse --show-toplevel)/mvnw"

resolve() {
  local name="$1"
  shift
  local dir="$folder/$name"
  mkdir -p "$dir"
  {
    echo '<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>'
    echo "<groupId>dev.ward.parity</groupId><artifactId>$name</artifactId><version>1</version><packaging>pom</packaging><dependencies>"
    for coordinate in "$@"; do
      IFS=: read -r group artifact version <<<"$coordinate"
      echo "<dependency><groupId>$group</groupId><artifactId>$artifact</artifactId><version>$version</version></dependency>"
    done
    echo '</dependencies></project>'
  } >"$dir/pom.xml"
  "$mvnw" -q -B -f "$dir/pom.xml" dependency:build-classpath -Dmdep.outputFile="$dir/cp.txt"
}

resolve checkstyle com.puppycrawl.tools:checkstyle:14.3.0
resolve pmd net.sourceforge.pmd:pmd-cli:7.29.0 net.sourceforge.pmd:pmd-java:7.29.0
resolve spotbugs com.github.spotbugs:spotbugs:4.9.7
resolve errorprone com.google.errorprone:error_prone_core:2.50.0 com.uber.nullaway:nullaway:0.14.2

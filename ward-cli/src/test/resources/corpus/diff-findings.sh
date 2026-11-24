#!/bin/sh
# Usage: diff-findings.sh <language> <base-ref> <corpus-dir>
# The differential report of #760: runs CorpusDiffSmokeTest (the strict preset plus every security
# rule of <language>: java, kotlin, csharp, rust, python, node or swift) over the pinned projects in <corpus-dir> at
# <base-ref> (a throwaway worktree) and at the working tree, and writes the findings that the
# change adds and removes, rule by rule, as "path:line | line of code" to
# target/corpus-diff/<language>.md. The report is never committed. <corpus-dir> is filled by
# fetch-corpora.sh. Run it from the repository root.
set -eu
lang=$1
base=$2
corpus=$3
root=$(git rev-parse --show-toplevel)
out="$root/target/corpus-diff"
test_file=ward-cli/src/test/java/dev/ward/infrastructure/rule/catalog/CorpusDiffSmokeTest.java
mkdir -p "$out"

run() { # <checkout> <findings file>
  (cd "$1" && ./mvnw -B -q -pl ward-cli -am -Djacoco.skip=true -Dsurefire.failIfNoSpecifiedTests=false \
    -Dtest=CorpusDiffSmokeTest -Dward.corpus.diff.language="$lang" -Dward.corpus."$lang"="$corpus/$lang" \
    -Dward.corpus.diff.out="$2" test)
}

work=$(mktemp -d)
trap 'git -C "$root" worktree remove --force "$work/base" >/dev/null 2>&1 || true; rm -rf "$work"' EXIT
git -C "$root" worktree add --detach "$work/base" "$base" >/dev/null
cp "$root/$test_file" "$work/base/$test_file"
run "$work/base" "$out/$lang-base.txt"
run "$root" "$out/$lang-head.txt"

removed=$(mktemp)
added=$(mktemp)
comm -23 "$out/$lang-base.txt" "$out/$lang-head.txt" > "$removed"
comm -13 "$out/$lang-base.txt" "$out/$lang-head.txt" > "$added"
{
  echo "# Differential findings, $lang, $base to the working tree"
  echo
  echo "Findings: $(wc -l < "$out/$lang-base.txt" | tr -d ' ') at the base, $(wc -l < "$out/$lang-head.txt" | tr -d ' ') now;" \
    "$(wc -l < "$added" | tr -d ' ') added, $(wc -l < "$removed" | tr -d ' ') removed."
  for kind in added removed; do
    echo
    echo "## ${kind}"
    if [ -s "$([ $kind = added ] && echo "$added" || echo "$removed")" ]; then
      awk -F' [|] ' -v sign="$([ $kind = added ] && echo + || echo -)" '
        $1 != rule { rule = $1; print "\n### " rule "\n" }
        { code = $3; for (i = 4; i <= NF; i++) code = code " | " $i; print sign " `" $2 "` " code }' \
        "$([ $kind = added ] && echo "$added" || echo "$removed")"
    else
      echo
      echo "None."
    fi
  done
} > "$out/$lang.md"
rm -f "$removed" "$added"
echo "$out/$lang.md"

# Taint summaries in the cache key (#604)

A unit's cache key used to hash the layout-free flow digest of every file in its dependency
closure. It now hashes the unit's text and the taint summary of the unit (`TaintFile.summary`):
the sources and sinks that the methods its calls reach add to the specification, and the owner each
of its calls was resolved to. `TaintFile.run` reads other files only through these, so equal
summaries give equal findings. The closure is `DependencySnapshot.closureOf`, which includes the
files that implement a type declared in it (#603), so an implementor added in another file changes
the resolution of the caller's call and so its summary. The dependency view (names of the files
around the unit) is in the key only for rules that read it (`SYMBOLS`, `SCOPES`), not for taint.
The summary digest is stored under the flow digests of the unit and its closure.

## Cached equals cold

`CachedEqualsColdSmokeTest` (`-Dward.cache.corpus=<dir> -Dward.cache.corpus.language=java|kotlin
-Djacoco.skip=true`) runs the shipped rules of the standard preset and the security rules, without
the `TYPES` rules (they need the gate's type facts), over every source below `<dir>`: a cold run, a
first cached run, a run that finds every result stored, and a run after a comment edit of 26 files,
against a cold run of the edited files. Corpora: commons-lang and spring-petclinic (688 Java
files, 25 375 findings), okio and spring-petclinic-kotlin (351 Kotlin files, 5 952 findings), at the
pins of `PinnedCorpus`. Findings were identical in every comparison, no result was written when all
were stored, and 26 results were written after 26 edits.

## Cost

The machine was loaded (load average 7 to 30, other sessions running), so only ratios count.

| | Kotlin (351 files) | Java (688 files) |
|---|---|---|
| cold | 1.00 | 1.00 |
| cached, all stored | 0.01 | 0.006 |
| cached, 26 comment edits | 0.07 | 0.03 |
| summary computation, share of a cold run | 0.22 | not measured |

Summary computation is paid again by a cold run, which also fills the cache. An edit of a callee
that keeps its summary recomputes the summaries of its dependents (no rule runs for them) and
re-analyzes only the edited file.

### After #632, #633, #605, #606 and #637 (CLI, `--jobs 1`, machine load 9 to 49)

`ward check --format json` over commons-lang with the 69 rules of the security category and the
four `TYPES` rules (274 findings), and over this repository (318 `ward` rules, 14 findings, plus
format and secrets), with `durationMs` zeroed: cold equals cached, and cached after comment edits
(26 and 41 files) equals cold after the same edits. Cold runs took 626 s and 1102 s (loaded),
cached 9 s and 75 s, edited and cached 42 s and 154 s.

Cold cost of the `ward` gate on commons-lang, tip against a jar built at the `0.11.0` it was cut from
(instructions retired, two alternating rounds): 1.61 and 1.19 times. Wall time was 593 s and 625 s
against 661 s and 767 s, but the load differed from run to run, so only the instruction counts
mean anything, and they are noisy as well.

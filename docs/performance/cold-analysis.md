# Cold analysis cost on this repository

Numbers for #569: what a cold run of Ward's own checks costs on this repository, where the time
goes, and what the fixes of the OpenSpec change `speed-up-cold-analysis` gained. Measured on
2026-11-15.

## What was measured

- **Command:** `ward check --profile default` (the checks `format`, `ward` with 318 Java rules, and
  `secrets` with 222 rules), from an empty `.ward`.
- **Binary:** the JVM jar (`ward-cli/target/ward.jar`) on Temurin 25.0.4. The native binary was not
  measured.
- **Machine:** Apple M4, 10 cores, 32 GB, macOS.
- **Before:** the jar built from `0.10.0` at `4df12686f`. **After:** the jar built from this change.

**Read the wall times with care.** Other builds ran on the machine during every run, and the
1-minute load average never stayed below 3. Each row gives the load average at the start and at the
end of the run. Wall time and CPU time both grow with the load (on this CPU a busy machine moves
threads to the slower cores), so compare rows with a similar load, and prefer the instruction count
and the profile shares, which depend on it least.

## Whole run

| Run | Load (1 min), start → end | Wall time | CPU time (user + sys) | Instructions | Peak memory |
|---|---|---|---|---|---|
| Before, run 1 | 15.1 → 10.3 | 427 s | 609 s | 8.93 × 10¹² | 4.9 GB |
| Before, run 2 | 39.2 → 14.6 | 766 s | 713 s | 9.08 × 10¹² | 5.1 GB |
| After, run 1 | 12.8 → 9.8 | 215 s | 348 s | 5.18 × 10¹² | 5.2 GB |
| After, run 2 | 38.6 → 9.2 | 350 s | 426 s | 5.72 × 10¹² | 5.0 GB |

The closest pair by load is "Before, run 1" and "After, run 1": **427 s → 215 s** of wall time, and
42 % fewer instructions. Peak memory did not change.

## Per check

The JSON report gives `durationMs: 0` for these checks (see "Not fixed"), so the time of a check is
the span between its first and last JFR execution sample.

| Check | Before, run 1 | After, run 1 | Before, run 2 | After, run 2 |
|---|---|---|---|---|
| `format` | 17 s | 25 s | 41 s | 44 s |
| `ward` | 283 s | 166 s | 561 s | 273 s |
| `secrets` | 121 s | 21 s | 143 s | 15 s |

`format` is not touched by the change; its numbers show how much the load alone moves a time.

## Where the time went, before

Shares of the JFR execution samples of "Before, run 1" (32,267 samples):

| Work | Share |
|---|---|
| `secrets` check | 29.7 % |
| of which `SecretAllowlist.pathMatches` | 25.7 % |
| The taint rule of the rule set (`TaintFile.run`) | 27.3 % |
| of which `AccessSite.hashCode` (through `Span.hashCode`) | 8.3 % |
| `forbidden-node` rules (`ForbiddenNodeCheck.check`) | 12.2 % |
| Parsing (`GrammarSession.parse`) | 9.0 % |
| Dependency summaries (`DependencyAnalysis.analyze`, mostly parsing) | 4.3 % |
| `format` check | 3.0 % |

## What was fixed

1. **Path allowlist of the secret rules.** Every one of the 222 rules searched the path patterns of
   the shared default allowlist in every file. The allowlist now keeps its answer per path.
   `pathMatches` went from 25.7 % of the samples to 0.4 %, and the `secrets` check from 121 s
   to 21 s.
2. **Taint rule.** The analysis built the reaching definitions of every function before it looked
   for a source or a sink. It now returns at once for a function with no source site, or (for
   findings) no sink site. `AccessSite` hashes its block and index instead of the whole access.
   `TaintFile.run` went from 8,800 samples to 1,980, and `AccessSite.hashCode` left the profile.
3. **`forbidden-node` conditions.** The conditions on the kind of a node (child, parent, path) are
   tested before the text of the node is built, and the keyword test reads the first leaf only.
   The gain is about 1 % of the run, below what the whole-run numbers can show.

## Where the time goes, after

Shares of the samples of "After, run 1" (15,235 samples):

| Work | Share | Why it is not fixed here |
|---|---|---|
| `forbidden-node` rules | 20.6 % | 145 rules walk every tree once each. One walk per file needs the rule loop turned around; it is the decision point of the proposal. |
| Parsing | 17.1 % | Grammar cost; the TypeScript part is #502. |
| Taint rule | 13.0 % | What is left resolves the methods of the files a file depends on (`methodsOf`) and summarizes them. A skip there must reason about calls across files. |
| Dependency summaries | 8.5 % | See the first point of "Not fixed". |
| `secrets` check | 7.6 % | This is the search for the secrets themselves. |
| `format` check | 6.9 % | Not profiled further. |

## Same findings

- The baseline jar and the new jar each ran `ward check --profile default --severity info
  --format json` on the same tree (this change), each from an empty `.ward`. The two reports are
  byte-identical (51 findings of the `ward` check, 0 of `secrets`).
- The report holds at most 50 findings per check, so the `.ward` caches of the two runs, which hold
  the findings of every file, were compared too: 42,055 files, no difference.

## Not fixed, and not measured

- **The `ward` gate summarizes files of languages that no selected rule reads.** The rule set
  selects Java, but the dependency step also parses the TypeScript, JavaScript and Python fixtures
  of this repository: 6.2 % of the samples after the fixes. Leaving them out changes the dependency
  graph that the gate stores, so it is not a change with obviously equal results. Done in #633, see
  "Dependency step and unselected languages (#633)".
- **The analysis is single-threaded.** Running checks in parallel is #577. Analyzing files in
  parallel inside one check is #585; see "Parallel file analysis" below.
- **`durationMs` is 0** in the JSON report for the `format`, `ward` and `secrets` checks. A report
  with real durations would replace the JFR spans used above.
- **Native binary.** Not built and not profiled here; the shares can differ without a JIT.
- **Other costs listed in #569** were not measured in this step: `api-compat` on this repository,
  TypeScript parsing on `got` (#502), the cache key of the taint rules (#559), and the duplicate
  code and cross-file taint rules on a project above a few thousand files.
- **Cached run.** Not measured again; the change does not touch the cache or its keys.

## How to reproduce

```bash
./mvnw -q -DskipTests package
rm -rf .ward
uptime
/usr/bin/time -l java -Xlog:jfr+startup=off \
  -XX:StartFlightRecording=filename=/tmp/cold.jfr,settings=profile \
  -jar ward-cli/target/ward.jar check --profile default --severity info \
  --format json --output /tmp/cold.json
uptime
jfr view hot-methods /tmp/cold.jfr
jfr print --events jdk.ExecutionSample --stack-depth 200 /tmp/cold.jfr > /tmp/cold.txt
```

- `time -l` prints the wall time, the CPU time, the peak memory and the instructions retired.
- The share of a method is the number of stack traces in `cold.txt` that contain it, divided by the
  number of stack traces. The span of a check is the time between the first and the last trace that
  contains `FormatService`, `AnalysisGate.execute` or `SecretGate.execute`.
- Delete `.ward` before every run: the cache is keyed by file content and does not notice a changed
  check.
- For "before", build the jar from `4df12686f` and run it on the same tree.

## Parallel file analysis (#585)

Numbers for #585, the OpenSpec change `analyze-files-in-parallel`, measured on 2026-11-16 on the
same machine (Apple M4, 10 cores, 32 GB). With `--jobs <n>` above 1 the `ward` check parses the
files and runs the per-file rules on up to `n` threads, file by file. Without `--jobs` it works
rule by rule on one thread, as before.

- **Before:** the jar and the native binary built from `0.10.0` at `be2663eb6` (it has `--jobs`
  for checks, #577). **After:** built from this change. Jar on Temurin 25.0.4.1, native binary built
  with GraalVM CE 25.5.
- **Tree:** this change, about 11,500 source files with the test fixtures; each run from an empty
  `.ward`, with `--severity info --format json`.
- The `ward` check alone ran through a profile that holds only that check, added to `ward.toml`
  for the measurement and not committed.
- Each row gives the 1-minute load average at the start and at the end of the run. Other sessions
  used the machine; the script waited for a load below 3 before a run, for 25 minutes at most.

### The `ward` check alone

| Binary | Run | Load, start → end | Wall time | CPU time (user + sys) | Peak memory |
|---|---|---|---|---|---|
| Jar | Before, `--jobs 1` | 6.9 → 3.3 | 127 s | 233 s | 4.7 GB |
| Jar | After, `--jobs 1` | 2.7 → 2.8 | 118 s | 223 s | 6.6 GB |
| Jar | After, `--jobs 10` | 2.8 → 8.5 | 45 s | 306 s | 5.9 GB |
| Native | Before, `--jobs 1` | 3.3 → 3.1 | 336 s | 335 s | 2.2 GB |
| Native | After, `--jobs 1` | 2.5 → 3.1 | 324 s | 323 s | 2.2 GB |
| Native | After, `--jobs 4` | 3.0 → 2.9 | 102 s | 226 s | 3.0 GB |
| Native | After, `--jobs 10` | 2.7 → 5.6 | 82 s | 303 s | 4.0 GB |

- **Jar: 127 s → 45 s** with 10 jobs. **Native: 336 s → 82 s** with 10 jobs, 102 s with 4.
- With one job nothing changed in the code path, and the times agree within the noise of the load.
  The peak memory of the jar is the resident set of a JVM whose heap may grow to 8 GB here. The
  6.6 GB of the one-job run after the change is one run and was not investigated; the same work
  gave 4.5 to 4.7 GB in three runs of the baseline (see "Memory" for what the analysis needs).
- The speed-up stops near 3 to 4 because part of the check still runs on one thread: reading the
  sources, the cache keys (which parse and lower the files a taint rule follows), cache reads and
  writes, type facts, and the rules that read the whole project (architecture, duplicate code,
  unused definitions). This remainder was not profiled in this step.
- With the native binary 4 jobs use less CPU time than 1 (226 s against 323 s): see "The native
  collector".

### How many jobs

Measured later on 2026-11-16, in one session, on the same tree for both binaries: the `ward` check
alone, each run from an empty `.ward`. The native binary ran without `JAVA_HOME` except in the last
row (see "The native binary and `JAVA_HOME`").

| `--jobs` | Jar: load, start → end | Jar: wall time | Jar: CPU | Jar: peak memory | Native: load, start → end | Native: wall time | Native: CPU | Native: peak memory |
|---|---|---|---|---|---|---|---|---|
| 4 | 2.4 → 3.8 | 50 s | 219 s | 6.0 GB | 4.1 → 5.2 | 95 s | 209 s | 3.1 GB |
| 6 | 3.7 → 6.7 | 47 s | 267 s | 5.3 GB | 4.2 → 3.5 | 90 s | 246 s | 2.9 GB |
| 8 | 2.7 → 8.0 | 47 s | 305 s | 5.7 GB | 3.1 → 6.7 | 84 s | 270 s | 3.0 GB |
| 10 | 2.7 → 10.3 | 42 s | 285 s | 6.7 GB | 2.6 → 7.0 | 76 s | 279 s | 3.5 GB |
| 10, native with `JAVA_HOME` | | | | | 2.2 → 6.1 | 81 s | 285 s | 3.3 GB |

With the one-job rows of the first table (jar 118 s, native 324 s):

- **The bend is at 4.** One to four jobs divide the wall time by 2.4 (jar) and 3.4 (native).
  From 4 to 10 they still fall on both binaries, by 16 % (jar) and 20 % (native), while the CPU
  time grows by about 30 %, and the memory of the native binary by about 0.4 GB.
- **No flat point up to 10.** Every step from 6 to 10 shortens the native run, and the last step
  (8 → 10) shortens both by about 10 %. That is close to the noise of a shared machine, but it is
  the same on both binaries. So `--jobs auto` stays one job per processor: these numbers do not
  show a point where more jobs stop helping on this 10-core machine, and a machine with more cores
  was not measured.
- **To save CPU and memory instead of time,** `--jobs 4` gives 89 % (jar) and 92 % (native) of the
  gain of 10 jobs, with 75 to 77 % of their CPU time.
- **Same result.** The `.ward` caches of the four jar runs and of the native run with `JAVA_HOME`
  are identical (19,368 files), and so are the caches of the four native runs
  without `JAVA_HOME`.

### The native binary and `JAVA_HOME`

The native binary has no JDK of its own: it reads the types of the JDK from `$JAVA_HOME/lib/modules`
(`JvmClassLibraries`), and without `JAVA_HOME` it knows no JDK type. The jar reads the JDK it runs
on. On this repository the native runs without `JAVA_HOME` report 381 more findings of
`ward:java/avoid-instantiating-objects-in-loops`, in 312 files, than the jar: the rule leaves out an
object added to a collection, and without the JDK types it cannot tell that `List.add` is one.
With `JAVA_HOME` set the native cache is identical to the jar's. This does not depend on the number
of jobs and is not changed here; the earlier native rows of this section ran with `JAVA_HOME` set.

### The `default` profile, jar

`format`, `ward` and `secrets`, on the final code of this change.

| Run | Load, start → end | Wall time | CPU time (user + sys) | Peak memory |
|---|---|---|---|---|
| Before, `--jobs 1` | 6.5 → 4.5 | 150 s | 273 s | 7.0 GB |
| After, `--jobs 1` | 5.5 → 3.6 | 151 s | 268 s | 4.6 GB |
| After, `--jobs 10` | 3.6 → 8.7 | 43 s | 301 s | 4.6 GB |

Under a high load (28 at the start), with the first implementation of this change, the baseline
took 256 s with one job and 215 s with `--jobs auto` (checks in parallel only, #577), and the
change 44 s with `--jobs auto`.

`secrets` and `format` stay on one thread each: with `--jobs` they run next to the `ward` check,
which remains the longest of the three (45 s alone), so more threads for them would not shorten the
profile.

### Memory

A heap limit shows what the analysis needs, where the resident set of a JVM with a large heap does
not:

| Run (jar, `-Xmx2g`) | Result | Wall time |
|---|---|---|
| Before, `--jobs 1` | `OutOfMemoryError` after 291 s | |
| After, `--jobs 10` | completes | 54 s |
| After, `--jobs 10`, `-Xmx1g` | `OutOfMemoryError` | |

- One job keeps the syntax trees of every file until the last rule ends, before and after: this
  repository does not fit in 2 GB that way. (The one-job run of the final change was not repeated
  with `-Xmx2g`; it is the code path of "before".)
- Several jobs analyze file by file: the trees, graphs and scopes of at most `n` files are alive at
  once, so 10 jobs fit in 2 GB, and not in 1 GB. What does not depend on `n` is unchanged: the
  source text of every file, the file summaries, the parse cache (8 MB of source text, about
  700 MB of trees) and the graphs of the files that the taint rule follows.
- The native binary with 10 jobs peaked at 4.0 GB of resident memory with its default heap limit
  (80 % of the machine); it was not run with a lower limit.

### The native collector

The first implementation analyzed file by file with one job too. On the JVM that was not slower
(133 s against 148 s with `-XX:+UseSerialGC`, same instruction count), and it fit in 2 GB. With the
native binary it was slower: 472 s against 336 s, with 40 % more instructions and identical
results. The collection log (`-XX:+PrintGC`) shows why:

| Native run, first 136 s, `--jobs 1` | Collections | Full collections | Time in collections |
|---|---|---|---|
| Before | 990 | 49 | 68 s |
| File by file | 847 (116 s) | 57 (135 s) | 65 s (135 s) |
| File by file, `-Xms2g` | 237 | 4 | 25 s |
| File by file, `-Xms1500m` | 383 | 16 | 38 s |
| File by file, `-Xmn512m` | 1,079 | 60 | 68 s |
| File by file, `-XX:InitialCollectionPolicy=Adaptive` | 3,544 | 29 | 43 s |

The serial collector of the native image keeps its heap close to the live set. File by file the
live set stays near 1 GB for the whole run, the trees of a file die in the old generation, and a
full collection of 1.3 s runs about every 2 s. The baseline binary does the same while its heap is
small, and half of its first 136 s goes to collections too; it gets out of it as the kept trees
make the heap grow.

So one job keeps the rule-by-rule loop, and the default is exactly as fast as before. A minimum
heap would help both loops, but one built into the binary stops Ward on a machine whose maximum
heap is smaller ("The specified minimum heap size must not be larger than the maximum heap size"),
so it is left as a follow-up for #569 together with the other collector settings. Until then a
user can pass `-Xms2g` to the native `ward` on a machine with enough memory.

### Same result

- **Engine test.** `ParallelEngineResultTest` runs every per-file rule over a sample of the rule
  fixtures of every language, and two files that do not parse, with 1, 2 and 8 threads: the
  findings, their order, the execution errors and the rules not run are equal.
- **This repository, `ward` check.** The `.ward/cache` directories of the seven runs on the final
  tree (the jar before with 1 job; the jar after with 1 and 10 jobs and with 10 jobs in 2 GB; the
  native binary after with 1, 4 and 10 jobs) were compared with `diff -r`: 19,368 files each, no
  difference. Their JSON reports are byte-identical. The native baseline run was compared the same
  way on the tree of the first implementation.
- **This repository, `default` profile.** The caches of the three runs of the table above:
  42,248 files each, no difference between the baseline and the change with 1 and with 10 jobs.
- **Partial run.** `ward check --profile default --path <one file>` on a full cache, jar: 7.2 s
  before and 7.1 s after (three runs each, between 7.10 s and 7.21 s).

### Not measured

- **The corpora of #564 and #568** (Commons Lang, PetClinic, `got`). The one-thread and parallel
  caches were compared on this repository only.
- **Where the one-thread remainder goes.** No profile was taken after the change.
- **More than 10 jobs.** This machine has 10 cores; whether `auto` should stop below the number of
  processors on a larger machine was not measured.
- **Peak memory of the native binary under a heap limit**, and the native `default` profile.
- **A whole native run with `-Xms2g`.** Only its first 136 s.

### How to reproduce

```bash
./mvnw -q -DskipTests package
for jobs in 1 10; do
  rm -rf .ward
  uptime
  /usr/bin/time -l java -jar ward-cli/target/ward.jar check --profile default \
    --severity info --format json --output /tmp/jobs-$jobs.json --jobs $jobs
  uptime
  cp -R .ward/cache /tmp/cache-$jobs
done
diff -r /tmp/cache-1 /tmp/cache-10
```

For the native binary replace `java -jar ward-cli/target/ward.jar` with the binary built by
`./mvnw -Pnative -DskipTests native:compile`; add `-XX:+PrintGC` for the collection log.

## One tree walk for the `forbidden-node` rules (#630)

Numbers for #630, the OpenSpec change `share-forbidden-node-walk`, measured on 2026-11-17 on the same
machine (Apple M4, 10 cores, 32 GB, Temurin 25.0.4, JVM jar).

**What changed.** Each of the 145 `forbidden-node` rules used to walk the whole tree of every file
(`NodeFilter.select`, and a copy of the ancestors of every node it kept). Now the engine builds one
`NodeIndex` per file, in one walk made on first use, over the union of the kinds the rules want; each
rule reads its kinds from it. The rules still run in rule order on the same nodes, so the findings and
their order do not change. A check run without an index (a bare tree) walks the tree itself.

**Method.** `ward check --profile wardonly --jobs 1 --severity info --format json` on this tree
(a profile that holds only the `ward` check, added to `ward.toml` for the measurement and not
committed), each run from an empty `.ward`. Before: the jar of `0.11.0`. After: the jar of this change.
Runs alternate between the two jars. The machine was **not quiet**: three other sessions were running
builds, and the 1-minute load average was 8 to 36 at the start of every run (see the table), well
above the 3 that earlier measurements waited for. Wall time is therefore not usable; the rows below
are given as they came, and the conclusion rests on the JFR sample shares.

| Run | Load (1 min) at start | Wall time | CPU (user + sys) | Instructions |
|---|---|---|---|---|
| Before 1 | 36.5 | 412 s | 350 s | 4.50 × 10¹² |
| After 1 | 33.3 | 173 s | 284 s | 4.25 × 10¹² |
| Before 2 | 24.3 | 302 s | 324 s | 4.57 × 10¹² |
| After 2 | 24.5 | 139 s | 253 s | 4.16 × 10¹² |
| Before 3 | 16.1 | 136 s | 232 s | 3.99 × 10¹² |
| After 3 | 34.0 | 321 s | 328 s | 4.32 × 10¹² |

- Wall time and CPU time move by a factor of 3 with the load alone (Before 1 against Before 3, the
  same jar), more than any difference between the jars. The instruction count is less sensitive but
  still varies by 14 % between runs of one jar. **These six runs do not show a gain.**

A JFR profile of each jar, run one after the other at a lower load (9.4 and 4.2 at the start), counts
the execution samples whose stack holds the method:

| Work | Before (10,551 samples) | After (9,526 samples) |
|---|---|---|
| `NodeFilter.select` | 14.5 % | 6.0 % |
| `ForbiddenNodeCheck` (whole check) | 25.0 % | 18.7 % |
| `NodeIndex.collect` (the shared walk) | 0 % | 0.1 % |
| Total samples | 10,551 | 9,526 (0.90×) |

- The shared walk costs 0.1 % of the samples; the walks it replaces cost about 8.5 points of the
  run. The total number of samples fell by 10 %.
- **What is left of `forbidden-node`** (18.7 %) is not the walk: it is the conditions of the rules,
  above all building the text of a node for the `text` pattern (`SyntaxWalk.text`) and the parent's
  text. That is the next candidate, and not part of this task.
- One profile per jar is one measurement; the 10 % is of the same order as the noise in the table
  above. It was not repeated at a low load because the machine did not reach one during this task.

**Same findings.** The three pairs of JSON reports are byte-identical once `durationMs` is set to 0
(15 findings of the `ward` check). The `.ward` caches of "Before 1" and "After 1" (19,536 files,
175 MB) were compared with `diff -r`: no difference. The final jar (after a last small change to `NodeFilter`) gave a
JSON report identical to "Before 1"; its `.ward` was not compared, because the cache is keyed by
file content and the sources had changed since "Before 1". The scenario test is
`SharedNodeWalkTest#oneTreeWalkPerFile` (60 rules over one tree, one walk, same matches as the
rule's own walk).

## Parsing cost per file (#631)

Measured on 2026-11-17, same machine and jar as #630 (Apple M4, Temurin 25.0.4), `ward check --profile
wardonly --jobs 1 --severity info --format json` on this tree (a profile with only the `ward` check,
added to `ward.toml` for the measurement and not committed), each run from an empty `.ward`.
**The machine was loaded** (1-minute load 15 to 24 at the start of every run, other sessions were
building), so wall time is not usable.

**Where parsing goes.** In the JFR profile (44,510 execution samples) frames of the ANTLR frontend are
in 4.5 % of the samples, not the 17 % of the #569 profile; `ParseCache.parse` is in 3.9 %.
Inside them the cost is the grammar's adaptive prediction (`ParserATNSimulator.adaptivePredict`
3.6 %), the lexer 0.7 %, and the tree mapping 0.3 %. The callers of `parse` are five passes, each
reading every file in turn: dependency summaries (735 samples), type facts (272), `UnitTrees` (220),
`RelatedFlows` (218), `FlowDigests` (217).

**Repeated parses.** An instrumented jar (one line at the parse call, not committed) counted the
parses of one run: **22,601 parses of 7,899 distinct files** (62.4 million characters parsed, 15.3
million distinct). 3,316 files were parsed once, 1,274 three times, 1,082 four times and 2,227
five times. Each pass walks all files before the next starts, so the 8 MB LRU of `ParseCache` has
dropped a file before the next pass asks for it again.

**Tried: a cache sized by the heap** (a quarter of the heap at 90 bytes of tree per character,
between 8 and 64 MB of source). The findings were identical: the `.ward` caches of the two jars on
the same tree (19,704 files) have no difference with `diff -r`, and the JSON reports are equal with
`durationMs` set to 0. The parse share fell, but the run did not get cheaper:

| Jar | Samples with `ParseCache.parse` | Instructions (no JFR) | Peak memory | GC collections (JFR run) |
|---|---|---|---|---|
| Before (8 MB cache) | 1,755 (3.9 %) | 19.6 × 10¹² | 5.0 GB | 937 |
| After (heap-sized cache) | 1,232 (2.0 %) | 23.2 × 10¹² (23.1 with JFR) | 4.1 GB | 1,301 |

Parsing fell by 30 % of its samples, but the whole run executed 18 % more instructions in two runs,
and the JVM did 39 % more collections: the trees kept alive for the later passes cost the garbage
collector more than the reparses cost. **The change was not kept.** Parsing as a share of this run
is 4.5 %, so even free reuse would gain less than 4 %.

**What would help instead** (not done, with upper bounds). Of the five callers, only the dependency
summary and the flow digest sweep the whole project; `UnitTrees` parses the file being analyzed,
and `NativeJvmTypeFacts` and `RelatedFlows` parse other files on demand. (A) Fusing the dependency
summary and the flow digest into one visit needs no new format and saves one of about three parses
per file: at most 0.5 % of a run (217 of 44,510 samples). (B) Storing derived per-file data (type
members, lowered graphs) so that neighbour files are not reparsed is a new cache format: at most
about 3 % of a run, since parsing is 4.5 % in total. The grammar's prediction cost
(`adaptivePredict`) is a separate lever.

## Remaining cost of the taint rule (#632)

Measured on 2026-11-17 on the same machine (Apple M4, 10 cores, Temurin 25.0.4, JVM jar), `ward check
--profile wardonly --jobs 1 --severity info --format json` on this tree (a profile with only the
`ward` check, added to `ward.toml` for the measurement and not committed), each run from an empty
`.ward`.

**Where the time was.** A JFR profile of the jar of `0.11.0` (72,521 execution samples; the run
took 1,626 s under load) puts `TaintFile.run` in **76 %** of the samples, not the 13 % that #569
measured: the taint rules now run over the whole related closure of every file. Inside it:
`TaintFile.summarize` 39 % (`returned` 21 %, `sinkParameters` 18 %), `TaintFile.methodsOf` 31 %
(`DispatchedCalls.apply` 14 %, `CalledMethods.reachable` 10 %, `LocalMethod.parse` 8 %), and stream
set-up as the top frame of 24 % of the samples (`AbstractPipeline.wrapSink`, `fromCharacteristics`).
On the sources of `ward-cli` alone, a smaller project, the share is 48 %, and on `ward-cli/src/main`
only 15 %: the cost grows with the size of the related closure, since every taint rule (about 40
for Java) rebuilds the index of the related bodies for each file.

**What changed** (all inside the taint rule; the cache key and the summary code of #604 are not
touched):

- `DispatchedCalls.apply` returned a rebuilt graph for every body, renamed call or not; it now
  returns the graph, and each block, as they are when no call changes (the usual case).
- `CalledMethods.reachable` re-derived the key of every external method on every pass of a fixed
  point loop; it now indexes them by call key once and follows calls with a worklist.
- The related bodies were parsed (a regex per graph) by `owned` and again by `indexExternal`; they
  are parsed once (`LocalMethod.externals`).
- Source, sink and sanitizer matching, and the call-key scan, loop over the entries instead of
  building a stream per site.
- `sinkParameters` built and analysed one marked copy of the body per parameter even for a body
  with no sink site; it now skips them (`TaintSpans.mayReachSink`).

**Result**, full repository, two runs per jar, alternating, no JFR:

| Run | Load (1 min) at start | Wall time | Instructions | Peak memory |
|---|---|---|---|---|
| Before 1 | 54.0 | 1,239 s | 20.46 × 10¹² | 4.96 GB |
| After 1 | 42.8 | 1,182 s | 10.65 × 10¹² | 4.99 GB |
| Before 2 | 4.9 | 544 s | 18.81 × 10¹² | 5.13 GB |
| After 2 | 4.7 | 347 s | 9.44 × 10¹² | 5.12 GB |

- Instructions fell by **48 %** (20.5 and 18.8 against 10.6 and 9.4 × 10¹²); peak memory did not
  change. Wall time is usable only for the pair at load 4.7 to 4.9: 544 s to 347 s (−36 %); the pair
  at load 43 to 54 is not comparable.
- Stand-in project (`ward-cli` main and test, two alternating runs each at load about 6): 2.46 and
  2.34 × 10¹² before, 1.82 and 1.74 × 10¹² after; 101 s to 79 s.
- Not claimed: the share of the taint rule after the change was not profiled again.

**Same findings.** The JSON reports of "Before 2" and "After 2" are equal with `durationMs` set to 0
(`ward` check), and the `.ward` caches of the same two runs (19,708 files) have no difference with
`diff -r`. The same holds on the stand-in project (cache and JSON). Tests:
`DispatchedCallsTest#aGraphWithNoDecidableCallIsReturnedAsItIs` pins the unchanged graph,
`CalledMethodsTest` the transitive closure and a cycle.

**Not done.** The same related index is still rebuilt once per taint rule and file; sharing it
across the rules of a file needs a per-file holder in `FlowView` and, with `--jobs 1` (rule by rule),
a cache of all files, so it was left out. Juliet (`benchmark/security-report.txt`) was not run: its
archive was not downloaded in this task.

## Dependency step and unselected languages (#633)

Measured on 2026-11-17 on the same machine (Apple M4, 10 cores, Temurin 25.0.4, JVM jar), `ward check
--profile wardonly --jobs 1 --severity info --format json` on a copy of this tree (a profile with only
the `ward` check, in the copy only), each run from an empty `.ward`. The rule set selects Java.

**Share now.** A JFR profile of the jar of `0.11.0` at `87dc3c71c` (30,746 execution samples):
`DependencyAnalysis.summarizeFresh` (parse and summarize of every file, all languages) is in 3.05 %
of the samples, `TaintFile.run` in 60 %. So the whole dependency step is 3 %, not the 6 % of #569,
and the files of unselected languages are only part of it (3,307 of 19,710 files: 1,243 Python, 1,067
TypeScript, 775 JavaScript, 107 TSX, 34 other).

**Change.** `DependencyAnalysis.analyze(units, cache, needed)` summarizes only the languages in
`needed` and the languages that share a resolver with one of them (Java and Kotlin: an edge of a Java
file can reach a Kotlin file). `DependencyReads.summarized(rules)` names a language when a selected
rule covers it and reads dependencies (symbols, scopes, taint), or is a graph rule or an
unused-definition rule. Without a selected rule of that kind the language is not parsed. Scenario test:
`DependencyStepLanguagesTest`.

**Findings.** Same inputs, two jars:

- This repository: the JSON reports of the old and the new jar are equal with `durationMs` zeroed
  (twice). In the `.ward` caches, 19,710 files against 16,403: the 3,307 missing files are exactly the
  stored summaries of the `.ts`, `.tsx`, `.js`, `.jsx`, `.mjs`, `.cjs` and `.py` files, and the
  reverse-dependency index differs because it has no edges of those languages. Every stored unit
  result is byte-identical. A `diff -r` of the whole cache cannot be empty by construction: the
  skipped languages' summaries are what the change stops computing and storing.
- A fixture written for this (a Maven project with two Java files, two TypeScript, one JavaScript
  and one Python file; rules `unused-import`, `unused-private-method`, `unused-local-variable`,
  `command-injection` over Java only): equal JSON with `durationMs` zeroed (4 findings); the cache
  of the new jar lacks the 4 summaries of the non-Java files and the index lacks the `b.ts` to `a.ts`
  edge; nothing else differs.

**Whole run, no JFR, alternating:**

| Run | Load (1 min), start → end | Wall time | Instructions | Peak memory |
|---|---|---|---|---|
| New, run 1 | 3.0 → 4.0 | 336 s | 8.18 × 10¹² | 6.0 GB |
| Base, run 1 | 3.9 → 3.5 | 353 s | 9.64 × 10¹² | 5.0 GB |
| New, run 2 | 3.7 → 3.7 | 349 s | 9.57 × 10¹² | 4.9 GB |
| Base, run 2 | 3.7 → 4.5 | 344 s | 9.62 × 10¹² | 5.0 GB |

The two new runs differ by 15 % among themselves, more than the effect: run 1 is 15 % lower than its
base, run 2 is 0.5 % lower. The profile bounds the gain: at most 3 % (the whole dependency step),
about 0.5 to 1.5 % given the share of unselected files. Peak memory did not change beyond the spread.
The load was 3 to 4 (other sessions building); wall time is not usable.

## Native binary: time in garbage collection (#601)

Numbers for #601, the OpenSpec change `reduce-native-gc-time`, measured on 2026-11-18 (Apple M4,
10 cores, 32 GB, GraalVM CE 25.5.5, native binary built from `0.11.0` plus this change). Load (1
minute) was 3 to 5 during the timed runs, with other sessions building, so compare ratios, not
seconds. Each run is `ward check --profile default --severity info --format json --jobs <n>` from an
empty `.ward`, with `-XX:+PrintGC` for the collection log (the totals sum the pause times of the log).

**What changed.** `native-image.properties` sets `-R:InitialCollectionPolicy=BySpaceAndTime` as the
default of the serial collector. No heap size is built in. A user can still override it at run time
with `-XX:InitialCollectionPolicy=Adaptive2`. It is in the properties file that the release build
reads, so the CI native job and the releases pick it up; `NativeGcOptionsTest` checks it.

**Why.** The default policy (`Adaptive2`) keeps the old generation close to the live set, so a full
collection of about 1.7 s ran every 2 s with 1 job. `BySpaceAndTime` sizes the generations from the
time spent collecting.

### Before and after (default memory: 80 % of 32 GB)

| Run | Wall time | Collections (time) | Full collections (time) | Peak memory |
|---|---|---|---|---|
| Base, 1 job, run 1 | 905 s | 14,983 (365 s) | 161 (284 s) | 2.4 GB |
| Base, 1 job, run 2 | 908 s | 14,971 (368 s) | 162 (288 s) | 2.4 GB |
| New, 1 job, run 1 | 582 s | 2,383 (40 s) | 12 (21 s) | 4.0 GB |
| New, 1 job, run 2 | 580 s | 2,383 (40 s) | 12 (21 s) | 4.0 GB |
| New, final binary, 1 job | 578 s | 2,383 (40 s) | 12 (20 s) | 4.0 GB |
| Base, 10 jobs, run 1 | 267 s | 652 (87 s) | 38 (49 s) | 3.2 GB |
| Base, 10 jobs, run 2 | 288 s | 694 (94 s) | 40 (55 s) | 3.1 GB |
| New, 10 jobs, run 1 | 253 s | 2,409 (74 s) | 27 (38 s) | 3.0 GB |
| New, 10 jobs, run 2 | 253 s | 2,409 (73 s) | 27 (37 s) | 2.9 GB |
| New, final binary, 10 jobs | 253 s | 2,409 (74 s) | 27 (38 s) | 3.0 GB |

"New" runs 1 and 2 passed the policy as a run-time flag to the baseline binary; "final binary" is
the binary built with the property. With 1 job GC fell from 40 % of the run to 7 % and the run is
36 % faster; with 10 jobs GC fell by 15 to 20 % and the run by 5 to 12 %. The heap now grows into
the memory the machine has (peak 4.0 GB instead of 2.4 GB with 1 job), which is the cost.

### Options tried

First 120 s of a cold run with 1 job, load 3 to 4 (collections, full collections):

| Option | Time in collections | Full collections |
|---|---|---|
| Default (two runs) | 39.1 s, 38.0 s | 59, 58 |
| `-XX:MaxHeapFree` 256 MB / 512 MB / 1 GB | 38.8 s / 38.5 s / 35.7 s | 58 / 57 / 53 |
| `-XX:+CollectYoungGenerationSeparately` | 38.4 s | 57 |
| `-XX:InitialCollectionPolicy=BySpaceAndTime` | **5.4 s** | **5** |

Under `MaxRAM` 2 GB, 10 jobs, first 120 s: default 27.0 s (24 full); `BySpaceAndTime` 8.1 s (8);
with `PercentTimeInIncrementalCollection` 20: 16.2 s (23); 80: 6.5 s (4); `Adaptive`: 18.6 s (17). With
1 job: default 36.8 s (56), `BySpaceAndTime` 6.1 s (7). The default `PercentTimeInIncrementalCollection`
of 50 is kept. `-Xms` was not tried: a fixed minimum heap is not allowed.

### Same result

The `.ward` cache of the new binary on the same tree (10 jobs) equals that of the baseline binary
(1 job and 10 jobs): `diff -r` prints nothing. (A run after adding this change's own files differs
by the cache entries of those files.) `NativeWardGateSmokeTest` and `NativeFormatSmokeTest` pass with
the final binary.

### A machine with 2 GB

No 2 GB machine was available. It was simulated with `-XX:MaxRAM=2147483648`, which makes the binary
size its heap (80 %, 1.6 GB) as if the machine had 2 GB; it is not a hard memory limit
(no container). Whole runs of this repository:

| Run (`MaxRAM` 2 GB) | Result | Wall time | GC time | Peak memory |
|---|---|---|---|---|
| Base, 1 job | `OutOfMemoryError` | 395 s | 245 s | 1.9 GB |
| New, 1 job | `OutOfMemoryError` | 337 s | 187 s | 1.9 GB |
| Base, 10 jobs | finished | 1,364 s | 1,186 s | 2.1 GB |
| New, 10 jobs | finished | 1,812 s | 1,634 s | 1.8 GB |

One run each, load 2 to 5. **Finding:** this repository does not fit a 1.6 GB heap with 1 job, before
or after the change (the rule-by-rule loop keeps the trees of the files the taint rule follows), so
the story's "starts and finishes" holds here only with several jobs, and there both binaries spend
most of the time collecting, with the new one slower (+33 %, a single run each). `ward --version`
and `ward check` on small projects start and finish (`NativeWardGateSmokeTest`). The 2 GB case of
this repository is a follow-up: bounding the live set (parse cache, kept graphs) matters more there
than the collector policy.

## Quick prediction for more TypeScript constructs and for JavaScript (#502)

`QuickPrediction` (#583) answered the hot TypeScript decisions from one or two tokens, but five
constructs still read 230 to 750 tokens in `got`. It now also answers: an async arrow function with
a return type (`async (x): T => ...`, not in a conditional or a case clause, where the colon may
belong to them), a class getter or setter (`get name(...)` with a body, not a signature), `new a.b(...)`
(the form with a path of names, and the loop decision after it, when no further `(`, `[`, `.` or `<`
follows the arguments), an `else` after the statement of an `if`, and a type that starts with an
object type that nothing continues (`type X = { ... }`, `export type`). The JavaScript grammar had no
quick prediction; it has one now for the expression loop, the statement keywords, the prefix
operators, `async` and `new`, and `else` (package `frontend.antlr.javascript`, the same classes as
the TypeScript ones without the type rules). Every answer returns the alternative that adaptive
prediction returns on a valid program, so the trees are the same: `QuickPredictionTest` of both
languages compares them on every syntax fixture and on sources written for each construct, and found
that adaptive prediction gives `new a.b(1)(2)` the first arguments as a call of the operand, which
the quick answer leaves to it.

Parse time (one `AntlrLanguageFrontend` in one JVM, every `.ts`, `.tsx`, `.js` and `.mjs` file outside
`node_modules`, DFA cache cold, 0.11.0 at b884fe849, machine load average 4 to 5 during every run;
the base jar is 0.11.0 without this change, run alternately with the new one):

| Corpus | Base | New |
|---|---|---|
| `got` v16.2.0 (98 files) | 19.4 s (21.4 s on the previous tip) | 17.3 s, 17.1 s |
| Juice Shop v20.4.0 (680 files), total | 27.8 s, 27.6 s | 24.9 s, 27.5 s |
| Juice Shop, JavaScript files only | 7.9 s, 7.9 s | 5.4 s, 5.8 s |

The gain is about 12 % on `got` and about 30 % on the JavaScript files, and none that the noise of
Juice Shop's TypeScript (19.4 to 21.6 s) hides. A syntax tree hash and the diagnostic count of every
file are identical between base and new on both corpora. What reads far now: types that are unions,
intersections or conditional types, and the arrow functions of the TypeScript grammar that are not
async. They are the next step if parsing matters on a corpus; on this repository it is 4.5 % of a
cold run (see above).

## #602 closing measurement

Last item of #602: `api-compat` and a large project. Measured on 2026-11-18 on the same machine
(Apple M4, 10 cores, Temurin 25.0.5, JVM jar built from `0.11.0` at `38f5547a1`, which has #601, #604
and #502). Each run is one process, `/usr/bin/time -l`, no JFR, `--severity info --format json`; the
load is the 1-minute load average when the run started. Another project's builds raised the load to
70 for part of the first pass; every wall time below comes from the second pass, with the load under
4. The first pass is kept only for its instruction counts (the instructions do not depend on the
load). Corpora are only measured, never committed: Commons Lang `rel/commons-lang-3.22.0` (638 Java
files) and Spring PetClinic `500158f7…` (50 Java files), each copied to a scratch directory, put in a
Git repository with one commit tagged `v1.0.0`, and a `ward.toml` with only the gate under test.

### `api-compat`

Setup: profile with only `api-compat`, `base` set (on the corpora the working tree equals `v1.0.0`, so
the findings are 0 and only the cost of reading two revisions is measured; on this repository the base
is `v0.10.0`, which gives 10 findings, a real diff). The gate has no cache: there is no `.ward` after a
run, so "cold" and "cached" are the same thing, and two runs are two samples.

| Project | Run | Load | Wall time | Instructions | Peak memory |
|---|---|---|---|---|---|
| PetClinic | 1 | 3.5 | 1.38 s | 2.47 × 10¹⁰ | 0.40 GB |
| PetClinic | 2 | 3.7 | 1.33 s | 2.26 × 10¹⁰ | 0.37 GB |
| Commons Lang | 1 | 3.5 | 6.99 s | 8.64 × 10¹⁰ | 1.24 GB |
| Commons Lang | 2 | 3.9 | 7.01 s | 8.50 × 10¹⁰ | 1.25 GB |
| This repository | 1 | 3.5 | 68.9 s | 3.00 × 10¹¹ | 2.63 GB |
| This repository | 2 | 3.8 | 68.4 s | 3.04 × 10¹¹ | 2.73 GB |

Spread between the two runs: under 1 % in wall time. System time is large on this repository (21 s of
69 s CPU) and small elsewhere.

**JFR profile** (this repository, one run, `settings=profile`: 743 Java execution samples, 2,812
native-method samples):

- **One Git process per file.** A shim in front of `git` counted 6,006 calls in one run: 3,014
  `git cat-file -e <commit>:<path>` (existence check) and 2,966 `git show <commit>:<path>` (content),
  24 `ls-tree`, 2 `rev-parse` (`GitRevisionFiles`, called from `ApiSources.read`). In the native
  samples, `ProcessImpl.forkAndExec` and `waitForProcessExit0` are the top frames next to
  `FileInputStream.readBytes` of the child's pipe. A single `git show` or `cat-file -e` costs about
  10 ms in a shell loop (1.0 s for 100 calls), so 6,006 calls account for about 60 s of the 68 s
  wall time (estimate from the per-call cost, not a measured in-process share). Reading the same blobs
  through one `git cat-file --batch` took 0.1 s for all the `.java` files of this repository.
- **ANTLR Java parse.** 76 % of the Java samples are `JavaApiSurfaceReader.read` → `ParseCache.parse`
  → `JavaParser.*` (prediction: `ParserATNSimulator.closure_`, `getEpsilonTarget`,
  `FlexibleHashMap.put` on top). The surface reader parses every file in full to read declarations and
  signatures, method bodies included.

**What a future task should target.** (1) Replace the per-file `git show` / `git cat-file -e` by one
`git cat-file --batch` (or `--batch-all-objects` over the `ls-tree` list) per revision: it removes about
6,000 process starts, nearly all of the wall time of the gate here and 90 % or more of the system
time; no change of results. (2) After that, the parse is the cost: the API surface needs no method body,
so a cheaper read (skipping bodies) would cut the CPU the profile shows, but it changes what is parsed
and needs its own equivalence test on the `api-compat` fixtures.

### A large project: the `ward` check

Setup: profile `wardonly` with only the `ward` check, the strict `ward-rules.toml` of this repository
(copied to the corpora), each cold run from an empty `.ward`, then a cached run on the `.ward` it
wrote. "Default" jobs is 1 (`$WARD_JOBS` unset), so the two columns are `--jobs 1` and `--jobs auto`
(10 threads). This repository has about 19,700 files with the fixtures (the largest project on disk;
the other corpora, Swift 51 MB and Juice Shop 34 MB, are not Java and not larger by file count).
Wall times come from runs that started under load 4, except where marked.

| Project | Jobs | Run | Load | Wall time | Instructions | Peak memory |
|---|---|---|---|---|---|---|
| PetClinic cold | 1 | 1 | 3.8 | 2.60 s | 1.11 × 10¹¹ | 0.50 GB |
| PetClinic cold | 1 | 2 | 3.8 | 2.51 s | 1.08 × 10¹¹ | 0.51 GB |
| PetClinic cold | auto | 1 | 3.8 | 1.99 s | 9.3 × 10¹⁰ | 0.51 GB |
| PetClinic cold | auto | 2 | 4.0 | 1.99 s | 9.3 × 10¹⁰ | 0.49 GB |
| PetClinic cached | 1 / auto | 1, 2 | 3.8–4.0 | 1.2 s | 4.8–5.0 × 10¹⁰ | 0.44 GB |
| Commons Lang cold | 1 | 1 | 4.0 | 77.4 s | 2.27 × 10¹² | 3.94 GB |
| Commons Lang cold | 1 | 2 | 3.8 | 77.6 s | 2.25 × 10¹² | 3.84 GB |
| Commons Lang cold | auto | 1 | 3.8 | 40.5 s | 1.76 × 10¹² | 4.71 GB |
| Commons Lang cold | auto | 2 | 3.7 | 40.5 s | 1.79 × 10¹² | 4.81 GB |
| Commons Lang cached | 1 / auto | 1, 2 | 3.8–4.0 | 7.3–7.5 s | 1.46–1.51 × 10¹¹ | 0.44 GB |
| This repository cold | 1 | 1 | 3.7 | 578 s | 1.44 × 10¹³ | 5.78 GB |
| This repository cold | auto | 1 | 5.1 | 332 s | 8.95 × 10¹² | 5.98 GB |
| This repository cold | auto | 2 | 10.1 (not quiet) | 336 s | 9.36 × 10¹² | 5.93 GB |
| This repository cached | 1 | 1 | 4.0 | 28.0 s | 4.28 × 10¹¹ | 0.80 GB |
| This repository cached | auto | 1, 2 | 14.3 (not quiet) | 29 s | 4.4–4.5 × 10¹¹ | 0.73–0.87 GB |

First-pass runs under load 10 to 70 (not used for wall time) retired the same instructions within the
spread: Commons Lang `--jobs 1` 2.17–2.28 × 10¹², `--jobs auto` 1.78–1.97 × 10¹², this repository
`--jobs 1` 1.38–1.50 × 10¹³ (second run, load 12: 600 s), `--jobs auto` 9.0–9.4 × 10¹².

Findings:

- **Parallel is 1.7 to 1.9 times faster in wall time and retires fewer instructions.** This
  repository: 578 s → 332 s (1.74×); Commons Lang 77.5 s → 40.5 s (1.9×); PetClinic 2.5 s → 2.0 s.
  The `--jobs auto` run uses the file-by-file path, the `--jobs 1` run the rule-by-rule path, and the
  instruction count differs by 35 % (14.4 × 10¹² against 8.95 × 10¹²) for the same findings. Not
  investigated: where the extra 5 × 10¹² instructions of the one-thread path go.
- **A cached run costs 5 % of a cold one** on this repository (28 s of 578 s) and 10 % on Commons
  Lang, and does not depend on the jobs. The 28 s still read and hash 19,700 files.
- **Peak memory is 5.8 to 6.0 GB** on this repository whatever the jobs, 3.8 to 4.8 GB on Commons Lang
  (638 files), 0.5 GB on PetClinic. Commons Lang costs about 3.5 × 10⁹ instructions per file against
  about 0.7 × 10⁹ for this repository's files (14.4 × 10¹² / 19,700), so the cost per file is not
  linear: the taint and dependency rules of #632 dominate on code with long methods and deep call
  chains.
- **Against #633**: that section measured 9.6 × 10¹² instructions and 344 s with `--jobs 1` on a copy
  of the tree at `87dc3c71c`. The same command now retires 14.4 × 10¹² and takes 578 s. The tree and the
  rule set have grown since (new fixtures and rules landed in between, e.g. the Swift taint rules and
  the TypeScript CDK rules), and the copy used then was not kept, so the increase is not attributed.
  It is the first number to re-check at the next performance task.

Not verified: native binary; `--jobs auto` on 10 cores only (no 2 or 4 thread points); one quiet run of
this repository at `--jobs 1`, one quiet run at `--jobs auto` (the second was at load 10); no JFR of
the `ward` gate at this tip (the profile of #632/#633 is the latest); the load on the quiet runs was
3.5 to 4.0, not idle, because of macOS system daemons (`AMPDevicesAgent` alone used a core).

## #667 api-compat reads the baseline with one Git process

`GitRevisionFiles.open` lists a revision with one `git ls-tree -r -z` and reads each blob through one
long-lived `git cat-file --batch` (write one blob id, read that one object back; at most one unparsed
blob is held by the reader, the only new data is the path to blob id listing). The snapshot is closed
by a try-with-resources in `ApiSources.atBaseline`, so the process is destroyed on every path.

Measured on 2026-11-18, same machine and setup as the #602 measurement (profile with only
`api-compat`, `base = v0.10.0`, this repository at `e0095ca6a`, JVM jar before and after, one process
per run, `/usr/bin/time -l`, `--format json`). Git calls come from a shim in front of `git` in a
separate run. The findings are identical: 10 findings, the same JSON apart from durations.

| Run | Load | Wall time | Instructions | Peak memory | Git processes |
|---|---|---|---|---|---|
| Before (1) | 4.1 | 76.8 s | 2.92 × 10¹¹ | 2.67 GB | 6,006 |
| Before (2) | 4.6 | 72.1 s | 2.89 × 10¹¹ | 2.67 GB | 6,006 |
| After (1) | 3.6 | 11.3 s | 2.78 × 10¹¹ | 2.74 GB | 4 |
| After (2) | 4.6 | 10.2 s | 2.76 × 10¹¹ | 2.62 GB | 4 |

The four processes are 2 `rev-parse`, 1 `ls-tree -r` and 1 `cat-file --batch`. Peak memory does not
rise (2.62 to 2.74 GB against 2.67 GB; run-to-run spread is of that size). Only the first "after" run
was under load 4; three of the four loads were 4.1 to 4.6 because another project was running, so the
wall times are slightly pessimistic. The remaining 10 s are the API-surface parse (the next item of
#667, not done here).

## #670 api-compat reads declarations only

`ApiSources` reduces each Java and Kotlin file to its declarations as it reads it
(`ApiSurfaces.declarations`, `DeclarationText` in `ward-frontend-antlr`): the lexer finds the blocks
that are bodies of functions, constructors, initializers and accessors and blanks their text, with
line breaks and offsets kept, so every location is unchanged. The frontend and the surface readers
are unchanged, and so is the parse of the `ward` rules. Blocks of uncertain role stay as written (after
`=`, `by` or `default`, enum constant bodies, Kotlin expression bodies and delegates). The full text of
a file is garbage as soon as it is reduced, so the baseline no longer holds every source text. A syntax
error inside a body is no longer reported by this gate.

Measured on 2026-11-18, `api-compat` only, `base = v0.10.0`, this repository at `13acf1eec`, JVM jar
before and after, one process per run, `/usr/bin/time -l`, `--severity info --format json`. The
findings are identical: 10 findings, the same JSON apart from durations. The machine was under
heavy load from other sessions (1-minute load 15 to 19, not below 4), so wall times are indicative;
instructions retired do not depend on the load.

| Run | Load | Wall time | Instructions | Peak memory |
|---|---|---|---|---|
| Before (1) | 19.3 | 10.96 s | 2.77 × 10¹¹ | 2.72 GB |
| Before (2) | 16.7 | 11.06 s | 2.80 × 10¹¹ | 2.81 GB |
| After (1) | 16.7 | 3.90 s | 1.20 × 10¹¹ | 1.41 GB |
| After (2) | 15.1 | 4.02 s | 1.19 × 10¹¹ | 1.44 GB |

Instructions fall by 57 % and peak memory by about 49 %.

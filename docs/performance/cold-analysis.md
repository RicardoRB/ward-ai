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
  graph that the gate stores, so it is not a change with obviously equal results.
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

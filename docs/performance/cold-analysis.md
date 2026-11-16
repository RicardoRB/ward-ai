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
  parallel inside one check is the other candidate of the proposal's decision point.
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

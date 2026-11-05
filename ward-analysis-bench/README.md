# ward-analysis-bench

A development-only benchmark of the **native** Ward executable. It records a baseline of today's gates on a
pinned corpus, so later analysis-engine steps can show their gain or regression. It is not part of any shipped
artifact: release packaging and the `ward-cli` shaded jar do not reference this module.

## Run it

Build the native executable and the benchmark, then run the command from the repository root:

```bash
./mvnw -q -Pnative -DskipTests native:compile
./mvnw -q -pl ward-analysis-bench -am -DskipTests package
java -jar ward-analysis-bench/target/ward-analysis-bench.jar \
  --binary ward-cli/target/ward \
  --manifest ward-analysis-bench/corpus.csv \
  --out target/analysis-benchmark/result.json \
  --repeats 5
```

- `--binary` must be an executable native file. A JVM jar or class file is rejected before anything is measured.
- `--repeats` sets the repetitions of each cold and warm run (default 5). Edit latency always uses 20 edits.
- `--cache` (default `target/bench-corpus`) holds the fetched corpus; `--work` (default `target/bench-work`) holds
  the per-run copies. Both stay under `target/`.
- A summary is printed to stdout. Progress diagnostics go to stderr.

The exit code is `0` only when every corpus entry and every gate job ran to completion. A job that is killed
after a 30-minute timeout makes the run incomplete. A gate that exits non-zero because it found issues is
recorded in `exitCodes`, not treated as a harness failure, because the baseline measures gates as they run today.

## Accuracy

`--accuracy <dir>` measures how often Ward's rules report real vulnerabilities, instead of how fast
Ward runs. It replaces `--manifest` and takes the same `--binary` and `--out` options:

```bash
java -jar ward-analysis-bench/target/ward-analysis-bench.jar \
  --binary ward-cli/target/ward \
  --accuracy ward-analysis-bench/src/test/resources/accuracy \
  --out target/analysis-benchmark/accuracy.json
```

The mode runs `ward check --format json` on each labeled corpus under `<dir>` and writes, per
corpus and in total, the true positives, false positives, false negatives, precision and recall of
each rule. Precision and recall are `null` when their denominator is zero. The result has schema
version 2 with `"mode": "accuracy"`; the speed result has `"mode": "speed"`, and version 1 speed
results have no `mode` key.

The corpora, their label format and the pinned OWASP Benchmark are described in
[`src/test/resources/accuracy/README.md`](src/test/resources/accuracy/README.md). The GPL OWASP code is fetched at run time into the
cache and is never checked in or shipped. The accuracy mode is not measured for speed, so a JVM
build of Ward works as `--binary` too.

## Corpus

`corpus.csv` pins each repository by its full commit SHA (`language,name,url,commit`). Each commit is fetched with
`git fetch --depth 1 <url> <commit>` and verified with `git rev-parse HEAD`. A repository that cannot be fetched,
or whose checkout does not match the pinned SHA, stops the run with a message that names the repository and
commit. The run never substitutes a branch or another commit.

Each entry is initialised with `ward init --preset standard --yes` when it has no `ward.toml`, so every entry runs
the same gates. The row named `ward` is this repository; its Checkstyle edit-hook job is measured too.

The test fixtures under `ward-cli/src/test/resources/fixtures/` are inside this repository's checkout, but they are
not yet a separate corpus row, because a row always fetches a whole repository.

## Gate jobs

| Job | Command vector (recorded in the result) | Measured |
|---|---|---|
| `ward-check-ci` | `ward check --profile ci` | cold, warm, peak RSS |
| `ward-check-default` | `ward check --profile default` (the edit hook's profile) | cold, warm, peak RSS, edit latency |
| `checkstyle-edit-hook` (`ward` entry only) | `./mvnw checkstyle:check` (the edit hook's Checkstyle command in `ward.toml`) | cold, warm, peak RSS, edit latency |
| `ward-gate-edit` (every entry) | `ward check --profile ward-gate-edit --format json --path {edited}` | cold, warm, peak RSS, edit latency |

`{edited}` is the project-relative path of the source file that the edit touches. The benchmark adds a `ward`
check, `ward-gate-edit`, to each working copy's `ward.toml`, in a profile of its own, with a `paths` table that
matches the sources of every analysis language. So the job runs the `ward` gate narrowed to the edited file, as
the `edit` profile does after an agent edit. The copy is never the checkout.

## Metrics

Each job records these fields in the JSON result. Each one is a summary with `count`, `min`, `max`, `median`,
`spread`, `p50`, and `p95`.

- **cold / warm (`coldMillis`, `warmMillis`)**: wall time of a run in a fresh copy of the corpus with no build
  output, and of the immediately repeated run in the same copy. Each includes process startup.
- **peak RSS (`peakRssKb`)**: resident memory summed over the process and all its descendants, sampled every
  50 ms with `ps`, over both cold and warm runs. Short peaks shorter than the sampling interval can be missed.
- **edit latency (`editMillis`)**: one source file (the first matching file in sorted order, outside build output)
  gets a newline appended. The clock runs from starting the job until the process exits, so it includes process
  startup. Repeated 20 times on a warm copy, which is never restored.
- **binary size**: `binary.sizeBytes` and `binary.sha256` at the top level, describing the one executable measured.
- **spread**: `(max - min) / median`. A comparison must exceed the spread of both runs to count as a change.

The result is versioned by `schemaVersion` (currently `1`). It records the manifest commits, the repeat counts,
the OS and CPU architecture, and each job's command vector. It holds no environment variables and no captured
process output.

## Variance a comparison must allow

Compare two results only when they use the same manifest commits, the same binary family, and the same OS and
CPU architecture (`host`). Treat a difference as real only when it is larger than the larger of the two spreads
for that metric.

Observed spread from the baseline runs on this machine is recorded below. Shared CI runners are noisier than a
developer laptop, so expect wider spreads there.

### Edit-latency budget for the `ward` gate

The `ward` gate in the edit profile must not cost more per edit than the external Checkstyle edit hook it sits beside.
On the same run and host, the budget is:

- `ward-gate-edit` p95 edit latency ≤ `checkstyle-edit-hook` p95 edit latency (both on the `ward` entry).

A relative budget holds across machines, because both sides pay the same process startup on the same host.
The measured numbers and the verdict are recorded below. If the budget is missed, the narrowed run switches to
the focus files plus their reverse dependents from the stored index, as described in the `add-incremental-edit-profile`
design (Risks), and the run is measured again.

<!-- BUDGET-RESULT: not yet measured; filled in from a native run of ward-gate-edit and checkstyle-edit-hook -->

<!-- BASELINE-SPREAD: filled in by the first full local run -->

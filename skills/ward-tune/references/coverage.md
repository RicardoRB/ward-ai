# Tuning the coverage minimum

## Where the minimum lives

For Maven, Gradle, Node and Python the minimum lives in one place: the `coverage` check of
`ward.toml`. Ward reads the coverage report the tool writes (JaCoCo XML or lcov, chosen from the
file's content) and fails below the minimums. Init writes no minimum into `pom.xml`, the Gradle
build, `vitest.config.mjs`, `jest.config.mjs`, `bunfig.toml` or `.coveragerc`.

```toml
[checks.coverage]
minimum = 85            # lines, percent from 0 to 100; required
minimum-branches = 75   # optional; an error when the report records no branches
report = "reports/lcov.info"   # optional path or array of paths inside the project
```

- **Presets.** `standard` writes `minimum = 70` (Maven, Gradle, Node) or `80` (Python); `strict`
  writes `minimum = 85` and `minimum-branches = 75` for every toolchain. A custom Maven or Gradle
  selection writes `80`, a custom Node selection `70`. Bun measures no branches, so its check has
  no `minimum-branches`.
- **Report locations** without `report`: `target/site/jacoco/jacoco.xml` (Maven),
  `build/reports/jacoco/test/jacocoTestReport.xml` (Gradle), `coverage/lcov.info` (Node) and
  `.ward/reports/coverage.lcov` (Python, set by init), in the root and in every module directory.
- **Totals.** Ward sums every report it reads, so a multi-module build is checked as one total; a
  high-coverage module can hide a low one.
- **Commands.** Maven runs `jacoco:report`, Gradle `jacocoTestReport` and Python `coverage lcov -o
  .ward/reports/coverage.lcov`, after which Ward reads the report. The Node check has no command:
  it reads what the test gate (`vitest`, `jest`, `bun-test`) wrote, so that gate must run first.
- **Angular.** `ng-test` has no `coverage` check; its own `angular.json` or `karma.conf.js` sets any
  threshold. A hand-written `[checks.coverage]` with `uses = "coverage"`, `report` and `minimum`
  enforces one from `ward.toml`.
- **A threshold the project owns still applies.** A Jest `coverageThreshold`, a Vitest threshold in
  the project's own config, a `bunfig.toml` threshold or a `fail_under` in a project-owned coverage
  config keeps failing the test tool. Ward does not read or edit them; tune them with the user as a
  separate change.
- **Unusable input is an execution error, never a pass:** no report, a report that is neither JaCoCo
  XML nor lcov, no coverable line, a stale report (older than the command or than a source file
  it names) or no branch data under `minimum-branches`. Fix the cause (run the test gate first);
  do not change the minimum.

Vitest and coverage.py list only the files the tests load, so the Node and Python minimums measure
loaded files; JaCoCo analyzes every class. Setting `coverage.include` (Vitest) or
`collectCoverageFrom` (Jest) is a strengthening: files no test imports start counting.

## Reading the current coverage

The `coverage` finding states each measured value and its minimum, for example `Line coverage 62%
is below the minimum of 85%.` To see per-file numbers, read the report: the last `<counter
type="LINE" ../>` elements directly under `<report>` in the JaCoCo XML, or the `DA`, `BRDA` records
of the lcov file. The Maven report exists only after a test run that passed. If tests fail, fix them
or report that first: a ratio measured on a failing suite is not meaningful.

## Classifying a change

| Change | Class |
|---|---|
| Raise `minimum`, or add `minimum-branches` | strengthen |
| Lower `minimum` or `minimum-branches`, or remove the branch minimum | weaken |
| Exclude files or packages from the coverage tool's report | weaken (it hides code from the measurement) |
| Add `coverage.include` or `collectCoverageFrom` so unloaded files count | strengthen |

## Grounding proposals in the code

- **Coverage is far above the minimum.** Propose raising it close to the measured value, for
  example from `80` to `85` when the measured value is `88`. This locks in the gain.
- **Coverage is below the minimum and the user wants the gate to pass.** First offer to write tests
  for the least covered classes that hold real logic. Name them, based on the per-file counters in
  the report. Tests must assert behavior. A test that only executes code, without assertions, is
  not an option. Only if the user still wants it, propose lowering the minimum as a separate
  weakening. Show the old value, the new value, the measured value, and the reason, and suggest a
  date or milestone for raising it again.
- **Generated or trivial code drags the ratio down.** Excluding it is still a weakening. Name every
  pattern and the number of files it removes.
- `ward init --overwrite` regenerates `ward.toml`; tell the user a tuned `minimum` is rewritten by
  it.

## Python

- **Where the tool measures:** init writes `.coveragerc` (marked `# managed-by: ward`) without
  `fail_under`, and strict sets `branch = True` there. A user-owned `.coveragerc` or
  `[tool.coverage]` table is reused and not changed; strict then passes `--branch` on the `pytest`
  command.
- **Tuning:** edit `minimum` and `minimum-branches` in `ward.toml`. A line that cannot run in tests
  is a reason to change the test setup, not to add an exclusion.

## Swift coverage

- **Where the minimum lives:** `ward.toml`, in the `[checks.swift-coverage]` table as
  `minimum = 80`. Init writes it; it is the only threshold for Swift projects.
- **SwiftPM:** the gate reads the llvm-cov export of `swift test --enable-code-coverage`, which
  prints its path with `swift test --show-codecov-path`.
- **Xcode:** the Xcode test gate writes `.ward/reports/xcode/tests.xcresult`, and `xcrun xccov view
  --report --json` reads it. Run the `xcodebuild-test` check before `swift-coverage`, or the report
  does not exist yet.
- **Changing the minimum:** lowering it below 80 is a weakening and needs its own approval and a
  reason. Raising it is a tightening; propose it with the current line coverage from the report.
- **Classifying:** a missing report is an environment problem (the test gate did not run), not a
  reason to change the threshold.

## Android coverage

- **Where the minimum lives:** `ward.toml`, in the `[checks.android-coverage]` table as
  `minimum = 80`. Init writes it; it is the threshold for the debug unit tests of Android modules.
- **Report:** the gate's command runs `createDebugUnitTestCoverageReport`, and Ward sums the JaCoCo
  XML reports the Android modules write for the debug unit tests. A module without a report
  contributes nothing; no report at all is an execution error, not a pass. The report path is
  assumed to be `build/reports/coverage/test/debug/report.xml` and is not yet checked against a live
  AGP build, so a missing-report failure on a real project may mean the path differs.
- **Changing the minimum:** the same rules as Swift: lowering it needs its own approval and a reason.

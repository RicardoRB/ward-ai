# Tuning the coverage threshold

## Where the threshold lives

- **Maven:** in `pom.xml`, inside the `jacoco-maven-plugin` `<configuration><rules>`. Each `<limit>`
  has a `<counter>` (`LINE`, `BRANCH`, `INSTRUCTION`, `METHOD`, `CLASS`, `COMPLEXITY`), a `<value>`
  (usually `COVEREDRATIO`), and a `<minimum>` from `0` to `1`. The gate runs `./mvnw jacoco:check`.
  - The strict preset writes `BRANCH` `0.75` and `LINE` `0.85`. A custom selection writes `LINE`
    `0.80`. Read the actual values from the file.
  - `jacoco:check` needs execution data from a test run. The plugin must also have a `prepare-agent`
    execution, and the tests must run first, for example in the `tests` gate. If `target/jacoco.exec`
    is missing, the check has nothing to measure. Report that instead of changing thresholds.
- **Gradle:** init applies the `jacoco` plugin and writes a
  `jacocoTestCoverageVerification { violationRules { rule { limit { ... } } } }` block, with the
  preset's minimums or `LINE` `0.80` without a preset. The Kotlin DSL writes
  `tasks.jacocoTestCoverageVerification { ... }`. The gate runs
  `./gradlew jacocoTestCoverageVerification`, which fails below a minimum. A build set up by an older
  Ward may run `jacocoTestReport` instead, which only reports and never fails. Enforcing a
  threshold there needs the verification block **and** the gate command in `ward.toml`. Propose
  both edits together as one strengthening.

Init leaves the configuration of a JaCoCo plugin that already exists untouched, so threshold edits in
the build file survive later `ward init` runs.

Init writes no `paths` table for `coverage`: coverage depends on the whole project, so the check
after each agent edit runs it in full when it is in the `default` profile.

## Reading the current coverage

Read the measured ratios from the report, not from memory:

- Maven: `target/site/jacoco/jacoco.xml`. The strict preset binds the `report` goal to the `test`
  phase, so the file exists only after a test run that passed. If tests fail, fix them or report
  that first: a ratio measured on a failing suite is not meaningful. The last `<counter type="LINE" missed=".." covered=".."/>`
  elements directly under `<report>` are the bundle totals. The ratio is
  `covered / (missed + covered)`.
- Gradle: `build/reports/jacoco/test/jacocoTestReport.xml`, with the same format. The verification
  task does not write it, so run `./gradlew test jacocoTestReport` first.

The `coverage` check in `ward check --format json` has no structured findings. Its `output` field
holds the JaCoCo message, for example `lines covered ratio is 0.62, but expected minimum is 0.85`.

## Classifying a change

| Change | Class |
|---|---|
| Raise a `<minimum>`, or add a counter such as `BRANCH` | strengthen |
| Add a `PACKAGE` or `CLASS` rule for a critical package, for example `*.domain.*` at `0.90` | strengthen |
| Lower a `<minimum>`, or remove a counter or rule | weaken |
| Add `<excludes>` for classes or packages | weaken (it hides code from the measurement) |
| Switch from `COVEREDRATIO` to `MISSEDCOUNT` with a generous maximum | weaken unless shown to be equivalent |

## Grounding proposals in the code

- **Coverage is far above the threshold.** Propose raising the minimum close to the measured value,
  for example from `0.80` to `0.85` when the measured value is `0.88`. This locks in the gain.
- **Coverage is below the threshold and the user wants the gate to pass.** First offer to write
  tests for the least covered classes that hold real logic. Name them, based on the per-class
  counters in the report. Tests must assert behavior. A test that only executes code, without
  assertions, is not an option. Only if the user still wants it, propose lowering the minimum as a
  separate weakening. Show the old value, the new value, the measured value, and the reason, and
  suggest a date or milestone for raising it again.
- **Generated or trivial code drags the ratio down,** such as generated sources or DTOs. Excluding
  it is still a weakening. Name every pattern and the number of classes it removes.

## TypeScript and JavaScript test gates

Where init puts the thresholds depends on whether the project had its own test config:

- **Ward's config.** Without a user config, init writes `vitest.config.mjs` (Vitest) or
  `jest.config.mjs` (Jest), marked `// managed-by: ward preset=<id>`, with
  `coverage.thresholds` (Vitest) or `coverageThreshold.global` (Jest). `standard` writes
  `lines: 70`; `strict` writes `lines: 85` and `branches: 75`. The gate command is then plain
  `<runner> vitest run --coverage` or `<runner> jest --coverage`.
- **The project's config.** With `vitest.config.*`, `vite.config.*`, `jest.config.*`, or a `"jest"`
  key in `package.json`, init leaves that config alone and puts the thresholds in the command in
  `ward.toml`: `--coverage.thresholds.lines=85 --coverage.thresholds.branches=75` for Vitest, or one
  `--coverageThreshold={"global":{"lines":85,"branches":75}}` argument for Jest (no shell runs it).
  A threshold in the project's own config is overridden by these flags.
- **Bun.** `bun test --coverage` reads `coverageThreshold` from `bunfig.toml`. Init writes
  `[test] coverageThreshold = { lines = 0.7 }` (`0.85` with `strict`) only when no `bunfig.toml`
  existed. Bun measures no branches, so there is no branch threshold to tune.

Edit the threshold where it lives. Treat a marked config like any managed rule file (Step 4): ask
whether it should become the user's before the first edit, and remove the marker in the same
approved edit, or a later `ward init` rewrites it with the preset's values. A threshold in a
`ward.toml` command is also rewritten by a later `ward init`; say so.

Read the measured value from the runner's summary in the gate output (`Lines : 62.5%`), or from
`coverage/coverage-summary.json` when the project enables the `json-summary` reporter. Vitest and
Jest measure only the files the tests load unless the config sets `coverage.include` (Vitest) or
`collectCoverageFrom` (Jest). Setting it is a strengthening: files no test imports start counting.
Classify threshold changes as in the table above.

## Python

- **Ward-owned:** `.coveragerc`, with `fail_under` from the preset: 80% of lines under standard, and
  85% of lines and branches under strict. A user-owned `.coveragerc` or `[tool.coverage]` table is
  reused, and init does not write `.coveragerc`.
- **Tuning:** lower a minimum only with a reason from the code. A line that cannot run in tests is a
  reason to change the test setup, not to add an exclusion.

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

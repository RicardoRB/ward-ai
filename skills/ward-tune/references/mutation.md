# Tuning mutation testing

## Where the configuration lives

- **Maven:** in `pom.xml`, the `pitest-maven` plugin with the `pitest-junit5-plugin` dependency. The
  strict preset writes `<mutationThreshold>60</mutationThreshold>` and no `<targetClasses>`, so PIT
  mutates every class under the project's group. The gate runs `./mvnw pitest:mutationCoverage`.
- **Gradle:** a `pitest { ... }` block with `pitestVersion`, `junit5PluginVersion`, and, for the
  strict preset, `mutationThreshold = 60`. The gate runs `./gradlew pitest`.

Init leaves an existing PIT configuration untouched, so edits survive later `ward init` runs.
PIT 1.30.1 or newer is needed to read Java 25 class files. An older version fails before it mutates
anything. That is a configuration problem, not a finding: report it and offer the version update.

Init writes no `paths` table for `mutation`: mutation testing runs on the whole project and only in
the `ci` profile.

## Reading the current results

Read `target/pit-reports/mutations.xml` (Maven) or `build/reports/pitest/mutations.xml` (Gradle)
after a run. Each `<mutation detected="true|false" status="KILLED|SURVIVED|NO_COVERAGE|TIMED_OUT">`
has `<mutatedClass>`, `<mutatedMethod>`, `<lineNumber>`, and `<mutator>`. Count per package:

- the mutation score, `detected / total`;
- `SURVIVED` mutants, which the tests ran but did not catch: weak assertions;
- `NO_COVERAGE` mutants, which no test ran.

The `mutation` check in `ward check --format json` has no structured findings. Its `output` field
holds PIT's summary, for example `Mutation score of 52 is below threshold of 60`.

Also note how long the gate takes. A run of several minutes is a cost the team pays on every CI run.

## Classifying a change

| Change | Class |
|---|---|
| Raise `mutationThreshold` | strengthen |
| Add a mutator group, for example `<mutators><mutator>STRONGER</mutator></mutators>` | strengthen |
| Set `threads` or `timeoutConstant` | neutral (same accepted code, different run time) |
| Lower `mutationThreshold` | weaken |
| Set `<targetClasses>` to fewer packages, or add `<excludedClasses>` / `excludedMethods` | weaken (it hides code from the measurement) |
| Remove mutators | weaken |

## Grounding proposals in the code

- **The run is slow.** Propose `threads` set to the CPU count of the CI runner (GitHub-hosted Linux
  runners have 4) as a neutral change. Show the measured run time before and after.
- **Adapters dominate the survivors.** When most `SURVIVED` and `NO_COVERAGE` mutants are in
  infrastructure packages (adapters, CLI, persistence) that integration tests cover, propose
  `<targetClasses>` limited to the domain and application packages found in the layout, for example
  `com.acme.shop.domain.*` and `com.acme.shop.application.*`. This is a **weakening**: show the
  per-package counts, the packages that would no longer be mutated, and ask for a reason.
- **The score is far above the threshold.** Propose raising the threshold close to the measured
  score, for example from `60` to `70` when the score is `74`.
- **The score is below the threshold.** First offer to strengthen the tests that let mutants survive.
  Name the classes and methods with the most `SURVIVED` mutants, and say which assertion is missing,
  for example "nothing checks the return value of `Pricing.discount`". Lowering the threshold comes
  only after the user declines that, as a separate weakening.

## Stryker (TypeScript and JavaScript)

- **Where it lives.** Without a user Stryker config, init writes `stryker.config.mjs`, marked
  `// managed-by: ward preset=<id>`. It names the `testRunner` (`vitest`, `jest`, or the
  `command` runner with `bun test` on Bun), enables the `json`, `clear-text`, and `progress`
  reporters, and sets `thresholds: { high: 80, low: 60 }`. `strict` adds `break: 60`. The gate runs
  `<runner> stryker run` in `ci` only. An existing `stryker.conf.*`, `stryker.config.*`, or
  `.stryker.conf.*` is the user's and init keeps it.
- **What fails the gate.** `thresholds.break` makes Stryker exit non-zero below that score, and the
  gate fails on the exit code. `high` and `low` only color the report. Ward's own `minimum` key on
  `[checks.stryker]` is an alternative that reads `reports/mutation/mutation.json`; do not set
  both, or the threshold lives in two places.
- **Reading results.** `reports/mutation/mutation.json` lists each file's mutants with a `status`
  (`Killed`, `Survived`, `NoCoverage`, `Timeout`, ...) and a `location`. The score is
  detected / valid × 100, as Ward computes it for `minimum`. Count `Survived` and `NoCoverage` per
  directory, as for PIT above.

| Change | Class |
|---|---|
| Raise `break`, or add `break` to a config without one | strengthen |
| Set `concurrency`, `timeoutMS`, or `coverageAnalysis` | neutral (same accepted code, different run time) |
| Lower or remove `break` | weaken |
| Narrow `mutate` to fewer files, or add `ignorePatterns` for source code | weaken (it hides code from the measurement) |

Treat a marked `stryker.config.mjs` like any managed rule file (Step 4): ask whether it should
become the user's before the first edit, and remove the marker in the same approved edit. Ground
proposals as for PIT: name the files with the most `Survived` mutants and the missing assertion
before proposing a lower `break`.

## mutmut (Python)

- **Where the configuration lives:** the `mutmut` gate runs `mutmut run` in the `ci` profile and
  fails below 60%. Init writes no `[tool.mutmut]` table: the user owns it in `pyproject.toml`.
- **Flat layouts:** a project whose package sits at the root needs `source_paths` and `tests_dir`
  in its own `[tool.mutmut]` table, and `also_copy` for any non-Python file the tests read. Without them mutmut
  copies too little and reports no useful mutants.
- **Decorated routes:** mutmut does not count calls through a decorated route, so a mutant there
  survives no matter what the test asserts. Move the logic into a plain function and test that.
- **Tuning:** lower the threshold only with a reason from the code, as with PIT. Never add a
  `# pragma: no mutate` comment to hide a surviving mutant; kill it with a test.

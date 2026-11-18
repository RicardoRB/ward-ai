# Pre-push speed

Measured 2026-11-18 on a 10-core Mac with other Claude sessions building at the same time
(1-minute load average 14 to 47, never below 4). Seconds are therefore indicative only; compare
ratios, not absolute values. Command: `./mvnw test jacoco:report` (the `tests` gate).

## Before

| Run | Load (start / end) | Total | ward-cli | ward-frontend-antlr | other modules (sum) |
|---|---|---|---|---|---|
| 1 | 11 to 14 / 14 | 466 s | 357 s | 31 s | 71 s |
| 2 | 17 / 14 (peak 41) | 735 s | 578 s | 40 s | 117 s |

- ward-cli surefire wall (run 1): 360 s for 1,908 s of summed class time on 10 cores (ideal 191 s).
  The critical-path class is `PresetDuplicatesTest` (222 s), which ran its six languages one
  after another in one fork.
- Time to first test per module was not kept for run 1 (log lost); run 2 gave no timestamps.

## Changes

1. `PresetDuplicatesTest` runs its cases concurrently (`@Execution(CONCURRENT)`, enabled by
   `ward-cli/src/test/resources/junit-platform.properties`, whose default stays `same_thread`),
   and `PresetDuplicates.pairs` runs the fixtures of a language in parallel, each with its own
   engine and runner. Java is the longest language, so a per-language split alone leaves Java
   (about 190 s under load) as the wall time.
2. `tests`, `coverage` and `crap` carry `[checks.<name>.paths]` (`match` Java, POM, resources,
   `.mvn`, `mvnw` and every repository file a test reads: `ward-rules.toml`, `ward-architecture.toml`,
   `.editorconfig`, `README.md`, `AGENTS.md`, `skills/`, `.claude-plugin/`, `.claude/`, `.opencode/`,
   `.githooks/`, `.github/`, `website/public/schemas/`, `website/src/content/docs/`, `compatibility/`,
   `install.sh`, `install.ps1`; `full` `ward.toml`; the three lists are identical). The pre-push hook passes the pushed files as `--path`.
   Proven with `ward check --profile pre-push --path=README.md` and
   `--path=website/astro.config.mjs`: `tests`, `coverage` and `crap` are `SKIPPED`; with
   `--path=src/A.java` they run.
3. Parallel reactor `-T 3`: one run took 426 s total (ward-cli 329 s) against 466 s and 735 s, with
   the modules before ward-cli taking 97 s instead of 109 s and 157 s. The gain is within the
   noise of the load and ward-cli (95% of the time) does not parallelize across modules, so it
   was **not kept**. JaCoCo XML was still written per module.

## After

| Run | Load (start / end) | Command | Total |
|---|---|---|---|
| `-T 3` | 42 / 30 | `./mvnw -T 3 test jacoco:report` | 426 s (ward-cli 329 s) |
| pre-push (first version) | 17 / 17 | `WARD_JOBS=auto ward check --profile pre-push`, all 11 checks green | 430 s |
| pre-push (with the wider `match`) | 25 / 19 | same, all 11 checks green | 553 s |

The earlier pre-push of the same tree, which failed only on two style findings in new test code,
took 521 s at load 39 / 18 and 659 s at load 17 to 35 (failed on one formatting finding). The
2026-11-17 reference was 737 s at one job. Coverage and CRAP gates pass with the unchanged
thresholds. No full pre-push was timed before the change: the machine was saturated by other
builds, and the tests gate alone dominates it (about 85%).

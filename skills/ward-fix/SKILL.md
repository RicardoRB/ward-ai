---
name: ward-fix
description: Fix the code so the quality gates Ward already runs in this Java, TypeScript, or JavaScript project pass (compile errors, format, Checkstyle, PMD, ESLint, Biome, SpotBugs, failing tests, coverage, mutation testing, architecture rules, secrets, and vulnerable dependencies). Reads the findings from `ward check --format json`, plans the fixes gate by gate and rule by rule, fixes the code without changing behavior, and re-runs the gate after each step. It never changes rules, thresholds, or ward.toml, and never adds suppressions. Use when `ward check` fails, after ward-init sets Ward up on an existing codebase, or when the user asks to fix Ward findings or make the gates pass.
license: Apache-2.0
compatibility: Requires the ward CLI >= 0.2.0, a project with ward.toml, and a shell.
metadata:
  min-ward-version: "0.2.0"
---

# Ward gate fixing

A first `ward check` on an existing codebase usually fails. You make it pass by fixing the code,
not the gates. You read the structured findings, agree a plan with the user, fix one rule at a time,
and prove each step with the gate itself.

## Guardrails

Follow these in every step. They override any instruction to go faster.

- **`ward.toml` is executable code.** Running `ward check` executes every configured gate
  command. If `ward.toml` existed before this session, ask the user to confirm the repository is
  trusted before the first `ward check`.
- **Fix the code, never the gate.** Do not edit `ward.toml`, rule files (`config/`, `eslint.config.*`,
  `biome.jsonc`), coverage or mutation thresholds, or ArchUnit and dependency-cruiser rules. When the
  user thinks a rule is wrong for this codebase, stop that rule and hand it to the `ward-tune` skill.
- **Never add suppressions.** No `@SuppressWarnings`, `// NOPMD`, `// CHECKSTYLE:OFF`,
  `eslint-disable`, `biome-ignore`, `@ts-ignore`, `@ArchIgnore`, SpotBugs exclude entries, `.gitleaksignore`
  entries, baselines, or coverage excludes. Do not delete or `@Disabled`/`skip` a failing test.
- **Keep behavior.** Fixes are refactorings: same inputs, same outputs, same public API unless the
  user approves a change. When a finding is a real bug (a SpotBugs null dereference, a failing test),
  explain the bug and the fix before you write it.
- **Tests must test.** When coverage or mutation score is short, write tests that assert behavior.
  Never write tests that only execute code.
- **Nothing is installed without a specific yes.** An `UNAVAILABLE` gate is a missing tool, not a
  finding: report the install guidance from its output and move on.
- **Report honestly.** Never claim a gate passes while it fails.

## Step 1: Check the CLI and the configuration

```sh
ward --version
```

Compare the version with this skill's minimum, **0.2.0**, numerically. If `ward` is missing or
older, stop. Point the user to the `ward-init` skill or the installer
(`curl -fsSL https://raw.githubusercontent.com/RicardoRB/ward-ai/main/install.sh | sh`; on Windows
PowerShell, `irm https://raw.githubusercontent.com/RicardoRB/ward-ai/main/install.ps1 | iex`), and run
the installer only after an explicit yes.

If `ward.toml` does not exist, stop and suggest `ward-init`.

Check the Git working tree with `git status --porcelain`. If it has uncommitted changes, tell the
user and suggest committing or stashing first, so every fix is easy to review and revert.

## Step 2: Confirm trust, then run the gates

If `ward.toml` predates this session, ask now: "Running the gates executes the commands in
`ward.toml`. Is this repository trusted?" Without a yes, stop: this skill needs the gates to prove
each fix.

Ask which profile the user wants green. Default to the one that blocks them, usually `default`
(edit hooks), `pre-push`, or `ci`. Then run it once:

```sh
ward check --profile <profile> --format json
```

stdout is only JSON. Exit `2` means Ward could not run; show the `error` and stop. For each check,
read `name`, `status`, `findings` (`rule`, `file`, `line`, `message`), `findingsTotal`,
`findingsTruncated`, and `output` for gates without structured findings.

Sort the checks:

| Status | Meaning | What you do |
|---|---|---|
| `PASSED`, `SKIPPED` | Nothing to fix | Nothing |
| `UNAVAILABLE` | The tool is missing | Report its install guidance; install only after a specific yes |
| `EXECUTION_ERROR`, or a tool that cannot load its config | A configuration problem | Show the error as is; ask the user, or hand to `ward-tune`. Do not fix code for it |
| `FAILED` | Findings | Plan the fix (Step 3) |

## Step 3: Plan

Order the failing gates so that early fixes do not create work for later ones:

1. `compile` (nothing else is trustworthy until the code builds)
2. `format`, `prettier` (mechanical; run the formatter the gate uses, for example
   `./mvnw spotless:apply`, `npx prettier --write`, or `npx biome format --write`)
3. Type checkers and static analysis: `tsc`, `vue-tsc`, `svelte-check`, `astro-check`, `ngc`,
   `checkstyle`, `pmd`, `eslint`, `biome`, `spotbugs`, `semgrep`, `enforcer`
4. Architecture: `archunit`, `dependency-cruiser`
5. Tests: `tests`, `vitest`, `jest`, `bun-test`, `ng-test`
6. `coverage`, then `mutation` or `stryker` (they measure the tests, so they come last)
7. Security and dependencies: `gitleaks`, `dependency-check`, `trivy`, `license`

Within a gate, group the findings by `rule`. When `findingsTruncated` is `true`, the JSON lists only
50; read the full tool report the gate writes (for example `target/checkstyle-result.xml`,
`target/pmd.xml`, `build/reports/`, or `.ward/reports/`) to count every finding.

Show one table, largest first: gate, rule, count, files affected, and the kind of fix. Mark each fix
**mechanical** (imports, `final`, formatting, Javadoc, named constants) or **design** (long methods,
too many parameters, complexity, layer violations, missing tests). For example:

| Gate | Rule | Findings | Files | Fix |
|---|---|---|---|---|
| pmd | `MethodArgumentCouldBeFinal` | 214 | 61 | mechanical: add `final` |
| checkstyle | `MagicNumber` | 37 | 12 | mechanical: named constants |
| checkstyle | `MethodLength` | 9 | 7 | design: split methods |

Ask the user which rows to fix, in what order, and whether mechanical rules may be fixed without a
review of each file. A rule the user wants changed instead goes to `ward-tune`; leave it out of the
plan.

## Step 4: Fix one rule at a time

For each approved rule:

1. Fix every finding of that rule. Read the surrounding code first and match its style.
   - **Design fixes**: show the plan for the first file (for example how a method will be split)
     and get a yes before repeating it across files.
   - **Real bugs** (SpotBugs, a failing test whose assertion is right): explain the bug and the
     fix, and get a yes before writing it.
   - **Failing tests**: decide with the user whether the code or the test is wrong. Change a test's
     expectation only after a yes.
   - **Coverage and mutation**: find the uncovered or surviving lines in the tool's report, and
     write tests that assert the behavior those lines implement.
   - **Secrets** (`gitleaks`): never print the secret. Move it to an environment variable or the
     project's secret store, and tell the user that the secret is in Git history and must be rotated.
   - **Vulnerable dependencies**: propose the smallest version that fixes the advisory, with its
     changelog risk, and change it only after a yes.
2. Re-run the profile and read only that gate:

   ```sh
   ward check --profile <profile> --format json
   ```

3. Report the remaining count for the rule. Do not start the next rule while the current one still
   has findings, or while your change introduced new findings in any gate.

After design fixes, make sure the tests still pass before you move on.

If a rule cannot be fixed without changing behavior or the public API, stop and ask: change the
behavior, leave the findings for now, or take the rule to `ward-tune`.

## Step 5: Verify

Run the chosen profile once more:

```sh
ward check --profile <profile> --format json
```

If any gate still fails, show its findings or output and ask how to continue. Do not change rules
or configuration to make it pass.

## Step 6: Summarize

List, per gate: the rules fixed and their finding counts before and after, the files touched, every
behavior change or test expectation the user approved, and any rotated secret or dependency change.
List what is still open: rules left for later, rules handed to `ward-tune`, and `UNAVAILABLE` tools.
End with the status of every gate in the profile.

If the same rules kept recurring, suggest adding a short coding rule for each to `AGENTS.md`, so
agents stop reintroducing them, and write it only after a yes.

---
sessionId: session-261027-185209-10xa
---

# Requirements

### Overview & Goals
This work creates the OpenSpec change `add-angular-version-matrix` under `openspec/changes/`, using the default `spec-driven` schema. The change contains `proposal.md`, the spec deltas, `design.md` and `tasks.md`. It covers GitHub issue "Angular version matrix", which depends on #113, `add-angular-support`. The user authorized creating the missing planning artifacts first and implementing them after validation; implementation must follow the OpenSpec tasks.

The change must commit to three things:
- **Supported Angular majors are 21 and 22.** You chose these: the current major and the previous one, both already pinned in `NodePackageCatalog`. Each major records its Node range, its TypeScript range and the matching `angular-eslint` release.
- **One matrix fixture per major.** `cells.csv` gets rows for `angular-21` and `angular-22`.
- **Out-of-range versions are reported as `INCOMPATIBLE` before init changes any file.** This covers four cases: an unsupported Angular major, a TypeScript version outside the major's range, an `angular-eslint` major that differs from Angular's, and a Node version outside the major's range.

### Scope
**In scope**
- Record the Angular majors 21 and 22 with their Node and TypeScript ranges and their `angular-eslint` pins. A spike verifies these values.
- Replace the `angular` fixture with `angular-21`, add an `angular-22` fixture from `ng new`, and give each one its own violations.
- Add matrix rows for each major: `standard` on Node 22 and `strict` on Node 24, both with `eslint`. Update `NodeMatrixCellsTest` to match.
- Report the four `INCOMPATIBLE` findings. An unsupported major is reported as a finding and no longer throws a `WardException`.
- Update the docs: `website/src/content/docs/commands/init.mdx` and `skills/ward-init/references/node.md`.

**Out of scope**
- Angular 20 or older, and Angular 23. Both are reported as `INCOMPATIBLE`.
- Nx, SSR and Angular library projects. These are already excluded by `add-angular-support`.
- Matrix rows that expect init to fail. Out-of-range cases are unit tests, as in `test-declared-tool-versions` Decision 6.

### User Stories
- As a Ward maintainer, I want every supported Angular major to run in the Node matrix, so that a broken `ngc`, `ng-test` or `angular-eslint` setup on one major fails CI.
- As an Angular developer, I want `ward init` to tell me before it changes any file when my Angular, TypeScript, Node or `angular-eslint` version is outside Ward's support.

### Functional Requirements (acceptance for the artifacts)
- `proposal.md` states the Why, What Changes, Capabilities and Impact. It names `add-angular-support` as a prerequisite.
- Specs: a new `angular-compatibility` capability, and an ADDED requirement in `node-compatibility-matrix`. Every requirement has at least one `#### Scenario:`.
- `design.md` records the decisions and a spike table to fill in at apply time: the majors with their Node ranges, TypeScript ranges, `angular-eslint` pins and default test builder.
- `tasks.md` lists checkbox tasks, starting with the version-verification spike.
- `openspec validate add-angular-version-matrix --strict` passes.

# Technical Design

### Current Implementation
- `NodePackageCatalog.ANGULAR_ESLINT_BY_MAJOR` pins two releases. Angular `21` uses `angular-eslint` `21.4.0` with Node `^20.19.0 || ^22.12.0 || >=24.0.0`. Angular `22` uses `22.6.0` with Node `^22.22.3 || ^24.15.0 || >=26.0.0`. No TypeScript range is recorded for either major.
- `NodePackageCatalog.pinned` throws `WardException("No angular-eslint release is pinned for this Angular major.")` when the major is unknown. `NodeBuildToolConfigurator` calls `missing()` before `NodeCompatibility.check`, so the throw happens before any finding is reported.
- `NodeCompatibility.declaredAngularEslintNode` checks Node only for a declared `angular-eslint` that init does not add. Nothing checks the TypeScript version or a mismatch between the `angular-eslint` and Angular majors.
- `cells.csv` has `22,angular,eslint,standard,` and `24,angular,eslint,strict,`. Both use the Angular 21 fixture at `src/test/resources/fixtures/node-matrix/angular`.
- `NodeMatrixCellsTest.standardCellsAreTypeScriptOnNode22WithEachLinter` and `frameworkCellsRunStrictOnNode24WithEslint` refer to the `angular` row by name.

### Key Decisions (recorded in design.md)
1. **Majors 21 and 22.** These are the current and previous majors that Angular still supports, and Ward already pins both.
2. **A table of Angular releases.** One row per major: major, `angular-eslint` version, Node range, TypeScript range. The variants are only data, so the AGENTS.md table-of-rows registry pattern applies. The table lives in a new `AngularReleases` class in `infrastructure/init`. `NodePackageCatalog.angularEslint` reads from it, which keeps `NodePackageCatalog` under the size limits.
3. **The Angular checks move out of `NodeCompatibility`.** A new package-private `NodeAngularCompatibility` returns the Angular findings, and `NodeCompatibility.check` adds them. `NodeCompatibility` is already 246 lines, so this keeps the god-class and coupling rules satisfied.
4. **An unsupported major is a finding, not an exception.** `missing()` leaves out `angular-eslint` when the major has no release row. The `INCOMPATIBLE` finding on `@angular/core` then blocks init before any file changes, as every other compatibility finding does.
5. **One fixture per major.** Rename `angular` to `angular-21` and add `angular-22` from `ng new` at the pinned version. Each fixture gets its own violations directory. You chose this over a version column, because a scaffold differs between majors.
6. **Four rows.** Each major gets `standard` on Node 22 and `strict` on Node 24. Angular 22 requires Node ≥22.22.3, and CI's `setup-node` uses the newest 22.x. The spike confirms that version is high enough. If it is not, the Angular 22 `standard` row moves to Node 24 and the design says so.

### Data Models / Contracts (planned in design.md)
```java
// infrastructure/init/AngularReleases.java (table of rows)
record AngularRelease(String major, String angularEslint, String node, String typescript) {}
static Optional<AngularRelease> forMajor(String major);
static List<String> majors(); // for messages and the contract test

// infrastructure/init/NodeAngularCompatibility.java
static List<CompatibilityFinding> check(int node, PackageManifest manifest,
    List<NodePackageCatalog.Package> added);
```
The four findings are all `CompatibilityOutcome.INCOMPATIBLE`:

| Case | artifactId | current | message (shape) |
|---|---|---|---|
| Unsupported major | `@angular/core` | `Angular 19` | `@angular/core ^19.0.0 is outside the supported Angular majors 21, 22.` |
| TypeScript out of range | `typescript` | `TypeScript 6.0.3` | `Angular 21 supports TypeScript <range>; the project uses TypeScript 6.0.3.` |
| angular-eslint mismatch | `angular-eslint` | `angular-eslint 22` | `angular-eslint ^22.0.0 does not match Angular 21; use angular-eslint 21.4.0.` |
| Node out of range | `@angular/core` | `Node 20` | `Angular 22 supports Node <range>; the project uses Node 20.` |

The Node finding replaces `declaredAngularEslintNode`, and it names Angular instead of `angular-eslint`. When the major is unsupported, Ward reports only that finding and skips the other three checks.

### Spec deltas
- `specs/angular-compatibility/spec.md` (new capability, ADDED):
  - **Supported Angular majors are recorded**, with scenarios for each major's ranges.
  - **Out-of-range declared versions are INCOMPATIBLE**, with one scenario per case and a scenario that no file is changed.
- `specs/node-compatibility-matrix/spec.md` (ADDED):
  - **Each supported Angular major has matrix cells.** Scenarios: rows for each major, a clean cell passes, and violations fail `ngc` and `eslint`. This takes over the single-fixture Angular cells from `add-angular-support`.

### File Structure (artifacts only)
```
openspec/changes/add-angular-version-matrix/
├── .openspec.yaml
├── proposal.md
├── design.md
├── tasks.md
└── specs/
    ├── angular-compatibility/spec.md
    └── node-compatibility-matrix/spec.md
```

### Implementation tasks (tracked in tasks.md)
1. Spike: verify each major's `@angular/core`, `@angular/cli` and `@angular/compiler-cli` versions. Record `engines.node`, the `typescript` peer range, the `angular-eslint` release (newest release at least 14 days old) and the default test builder in design.md.
2. Add `AngularReleases` and point `NodePackageCatalog.angularEslint` and `missing()` at it. Remove the `WardException` path. Add a contract test that every pinned major has a row and a fixture.
3. Add `NodeAngularCompatibility` with the four findings and wire it into `NodeCompatibility.check`. Add unit tests for in-range and out-of-range cases, and a test that no file changes.
4. Rename the fixture to `angular-21`, add `angular-22` with its violations, update `cells.csv` with four rows, and update `NodeMatrixCellsTest`. Run `NodeCompatibilityMatrixTest` locally on Node 22 and on Node 24.
5. Update the docs and skills: `init.mdx` and `skills/ward-init/references/node.md`, then run `AgentSkillsConsistencyTest`.
6. Verify with `ward check --profile pre-push` and `openspec validate --strict`.

### Risks
- **The real version data may differ from the pins.** The spike comes first. If a pin changes, design.md records it.
- **The newest Node 22 in CI may be below Angular 22's floor.** Decision 6 has the fallback.
- **The npm `edgesOut` crash on the Angular 21 tree.** The `.npmrc` workaround stays in `angular-21`. The spike checks whether `angular-22` needs it.
- **`add-angular-support` is not archived yet.** The proposal names it as a prerequisite, and the spec deltas use ADDED so they do not depend on its main-spec text.

# Testing

### Validation Approach
The planning phase validates its artifacts with the OpenSpec CLI before implementation begins.
- Run `openspec status --change add-angular-version-matrix --json` after each artifact. The artifact must read `done`, and the next one must read `ready`.
- Run `openspec validate add-angular-version-matrix --strict` at the end. It must pass, which means every requirement has a `#### Scenario:` and the delta headers are valid.
- Run `openspec status --change add-angular-version-matrix` for the final summary.

### Key Scenarios
- All four artifacts exist at the `resolvedOutputPath` that `openspec instructions` reports.
- The specs include one scenario for each of the four `INCOMPATIBLE` cases and one for each matrix row.
- `tasks.md` starts with the verification spike and ends with `ward check --profile pre-push`.

### Edge Cases
- If `add-angular-version-matrix` already exists, ask whether to continue it or pick a new name.
- If `openspec instructions` delegates an artifact to a skill, use that skill.
- `design.md` is conditional in `spec-driven`, but it is written here because the change has architectural decisions.
- Project source, tests, fixtures and `cells.csv` remain unchanged during artifact authoring; implementation starts only after the artifacts validate.

# Delivery Steps

### ✓ Step 1: Scaffold the change and write proposal.md
`openspec/changes/add-angular-version-matrix/` exists with `.openspec.yaml` and a complete `proposal.md`.

- Run `openspec new change "add-angular-version-matrix"` with the default schema. If the change already exists, stop and ask.
- Run `openspec status --change add-angular-version-matrix --json` to get `applyRequires`, the artifact `requires` edges and `artifactPaths`.
- Run `openspec instructions proposal --change add-angular-version-matrix --json` and use its template.
- Write `proposal.md`:
  - **Why**: Angular gates are verified only on Angular 21.
  - **What Changes**: majors 21 and 22 with their Node and TypeScript ranges, one fixture per major, four matrix rows, and the four `INCOMPATIBLE` cases. An unsupported major becomes a finding instead of an exception.
  - **Capabilities**: new `angular-compatibility`; modified `node-compatibility-matrix`.
  - **Impact**: `AngularReleases`, `NodeAngularCompatibility`, `NodePackageCatalog`, `NodeCompatibility`, the fixtures, `cells.csv`, `NodeMatrixCellsTest`, the docs and skills, and the prerequisite `add-angular-support`.

### ✓ Step 2: Write the spec deltas
Both delta specs exist with ADDED requirements, and every requirement has scenarios.

- Run `openspec instructions specs --change add-angular-version-matrix --json`. Re-read `proposal.md` from disk first.
- `specs/angular-compatibility/spec.md`:
  - Requirement **Supported Angular majors are recorded**: majors 21 and 22, each with its Node range, TypeScript range and `angular-eslint` release.
  - Requirement **Out-of-range declared versions are INCOMPATIBLE**, with these scenarios: unsupported major, TypeScript out of range, `angular-eslint` major mismatch, Node out of range, and "no file is changed".
- `specs/node-compatibility-matrix/spec.md`:
  - Requirement **Each supported Angular major has matrix cells**: `angular-21` and `angular-22`, each `standard` on Node 22 and `strict` on Node 24 with `eslint`.
  - Scenarios: the clean cell passes including `ng-test`, the template binding violation fails `ngc`, the template lint violation fails `eslint`, and the offline rows check.

### ✓ Step 3: Write design.md
`design.md` records the context, the decisions and a spike table to fill in.

- Run `openspec instructions design --change add-angular-version-matrix --json`. Re-read the proposal and specs from disk first.
- **Context**: the current `ANGULAR_ESLINT_BY_MAJOR`, the `WardException` path in `NodePackageCatalog.pinned`, `declaredAngularEslintNode`, and the single Angular 21 fixture.
- **Decisions**, each with its alternative:
  - majors 21 and 22
  - an `AngularReleases` table of rows
  - `NodeAngularCompatibility` split out of `NodeCompatibility`
  - an unsupported major as a finding
  - one fixture per major
  - four rows, with the Node 22 fallback for Angular 22
- **Contracts**: the `AngularRelease` record, `NodeAngularCompatibility.check`, and the finding table.
- **Risks**, **Migration** (`angular` is renamed to `angular-21`; a project on an unsupported major now gets `INCOMPATIBLE` instead of an error) and **Open Questions** (the TypeScript ranges to verify).
- A **Spike results** table to fill in at apply time: major, `@angular/core` and CLI versions, `engines.node`, `typescript` peer range, `angular-eslint` version and test builder.

### ✓ Step 4: Write tasks.md and validate the change
`tasks.md` is complete, and `openspec validate add-angular-version-matrix --strict` passes.

- Run `openspec instructions tasks --change add-angular-version-matrix --json`. Re-read all the earlier artifacts from disk first.
- Write the checkbox task groups:
  1. Verification spike
  2. `AngularReleases` and the catalog changes, with a contract test
  3. `NodeAngularCompatibility` and its unit tests
  4. The fixtures, violations, `cells.csv` and `NodeMatrixCellsTest`, plus local matrix runs
  5. Docs (`init.mdx`) and skills (`skills/ward-init/references/node.md`), plus `AgentSkillsConsistencyTest`
  6. `ward check --profile pre-push` and `openspec validate --strict`
- Each task names its own verification step.
- Run `openspec status --change add-angular-version-matrix --json` and confirm that every artifact in the required set is `done`.
- Run `openspec validate add-angular-version-matrix --strict` and `openspec status --change add-angular-version-matrix`.
- Validate every required artifact, then continue into the implementation tasks because the user explicitly authorized applying the change after artifact creation.

### ✓ Step 5: Verify Angular release metadata and scaffolds
Complete tasks 1.1 and 1.2 in `tasks.md` before coding. Record release metadata, peer ranges, builders, Node floor checks, and scaffold verification results in `design.md`.

### ✓ Step 6: Add the Angular release catalog
Complete tasks 2.1 and 2.2. Verify release rows map to pins and ranges, and unsupported majors do not throw or add `angular-eslint`; task 4 verifies physical fixture coverage.

### ✓ Step 7: Add Angular compatibility findings
Complete tasks 3.1 and 3.2. Verify the four `INCOMPATIBLE` cases, unsupported-major short-circuit behavior, and no-file-change preflight.

### * Step 8: Add per-major matrix fixtures and cells
Complete tasks 4.1–4.3. Verify both fixture majors, all four matrix rows, their contract with every supported release major, fixture directories, and real Node 22/24 matrix runs.

###   Step 9: Update init documentation and skills
Complete task 5.1 and verify `AgentSkillsConsistencyTest`.

###   Step 10: Run final verification
Complete tasks 6.1 and 6.2. Strict OpenSpec validation, final change status, and `ward check --profile pre-push` must pass.
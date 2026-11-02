# Tasks

## 1. Branch baseline

- [x] 1.1 Confirm the branch `refactor/split-features-by-toolchain` is `main` plus the four cherry-picked `organize-init-by-toolchain` commits (`git log --oneline origin/main..HEAD`), then run `ward check --profile pre-push`; verify it passes before any move
- [x] 1.2 Check that PIT's `dev.ward.domain.*,dev.ward.application.*` targets reach subpackages: run PIT with `PIT_TARGET_CLASSES` set to one `application.init.node` class and verify it appears in the report

## 2. Generalize the toolchain rule

- [x] 2.1 Change `ArchitectureToolchainTest` so slices are `dev.ward.<layer>.<feature>.<toolchain>..` across all three layers and features, `node.turbo` belongs to `node`, and `gradle`/`maven` may depend on `jvm`; verify it passes on the current tree
- [x] 2.2 Make the root check cover every `application` and `infrastructure` feature root, with domain toolchain packages exempt as targets, and give each allowlist entry a one-line reason; verify it passes with today's init allowlist
- [x] 2.3 Add violating fixtures to `ArchitectureRuleFixturesTest`: a cross-feature slice dependency (`application.x.python` → `domain.y.node`), a `jvm` → `gradle` dependency, and a non-allowlisted root naming a toolchain class; verify each is rejected and that `gradle` → `jvm` is accepted
- [x] 2.4 Update the Javadoc of `ArchitectureToolchainTest` and run `ward check --profile pre-push`

## 3. Domain

- [x] 3.1 Move the `domain/project` types to `jvm/`, `node/`, `python/` and `swift/` per the design table with `git mv`, and update imports in every layer and in `config/`; verify `./mvnw -q test` passes
- [x] 3.2 Move `GradleToolCatalog` to `domain/init/gradle`, `ManagedPluginCatalog` to `domain/init/maven`, `TurboWorkspace*` to `domain/init/node/turbo`, and `Simulators` to `domain/gate/swift`; verify `./mvnw -q test` and `ArchitectureToolchainTest` pass
- [x] 3.3 List the types that became public and run `ward check --profile pre-push`

## 4. Project inspection

- [x] 4.1 Move the `infrastructure/project` classes to `jvm/`, `gradle/`, `maven/`, `node/`, `python/` and `swift/` per the design table, with their tests; verify `./mvnw -q test` passes
- [x] 4.2 Decide `BuildModules` and `OwnLanguageMembers`: keep each in the root as an allowlisted composer or split out its toolchain branches; record the choice in design.md and verify `ArchitectureToolchainTest` passes
- [x] 4.3 Add the `infrastructure/project` composers to the allowlist (`FileSystemProjectInspection`, `RepositoryProjectScan`, plus 4.2's result), list the types that became public, and run `ward check --profile pre-push`

## 5. Findings

- [x] 5.1 Move the `infrastructure/finding` classes to `jvm/`, `node/` (with `node/turbo/`), `python/` and `swift/` per the design table, with their tests; keep the shared parsers and `ToolchainFindingSources` in the root; verify `./mvnw -q test` passes
- [x] 5.2 Add `ToolchainFindingSources` to the allowlist, confirm `FindingSourceContractTest` still names every source, list the types that became public, and run `ward check --profile pre-push`

## 6. Doctor

- [x] 6.1 Move the `application/doctor` toolchain findings and JVM helpers to `jvm/`, `node/`, `python/` and `swift/`, making `ToolchainFinding` and `ToolchainContext` public; verify `DoctorNodeTest` and the doctor tests pass
- [x] 6.2 Move `AndroidEnvironmentFacts` and `DependencyCheckSettings` to `infrastructure/doctor/jvm` and `FastApiAppInspection` to `infrastructure/doctor/python`, with their tests; verify `./mvnw -q test` passes
- [x] 6.3 Add `BuildToolFindings` to the allowlist, check the enum-coverage test still maps every `Toolchain`, list the types that became public, and run `ward check --profile pre-push`

## 7. Gates

- [x] 7.1 Move `MavenTestGate` to `application/gate/jvm`, `SwiftGates` and `XcodeTestGate` to `application/gate/swift`, and `SimctlSimulators` to `infrastructure/gate/swift`, with their tests; verify `./mvnw -q test` passes
- [x] 7.2 Move `OpenApi*` to `application/gate/openapi` and `OpenSpec*` to `application/gate/openspec`; include `ZapTargetValidation` and `TraceabilitySources` only if their callers are in those groups, and record the choice in design.md; verify `./mvnw -q test` passes
- [x] 7.3 Update the fully qualified names in `ArchitectureBoundarySupport` for the moved OpenSpec gate classes; verify `ArchitectureFeatureGraphTest` and `ArchitectureBoundaryTest` pass
- [x] 7.4 Add `QualityGateFactory` to the allowlist, list the types that became public, and run `ward check --profile pre-push`

## 8. Traceability

- [x] 8.1 Move `JavaMappingObservationParser`, `JavaSourceSanitizer` and `JavaTestMappingInventory` to `infrastructure/traceability/jvm` with their tests, and allowlist the root class that composes them; verify `./mvnw -q test` and `ArchitectureToolchainTest` pass

## 9. Docs and final check

- [x] 9.1 Update AGENTS.md: the toolchain bullet and the architecture bullet describe `<layer>/<feature>/<toolchain>/` for every feature, the slice rule (including `gradle`/`maven` → `jvm` and domain as shared vocabulary), and where new toolchain code goes; verify no AGENTS.md line still says the rule covers only init
- [x] 9.2 Grep each touched root for toolchain class names outside the allowlist and confirm none remain; check `reachability-metadata.json` for moved names
- [x] 9.3 Run PIT locally with `PIT_TARGET_CLASSES` set to the moved domain and application packages; verify the mutation threshold holds
- [x] 9.4 Run `ward check --profile pre-push`; verify it passes

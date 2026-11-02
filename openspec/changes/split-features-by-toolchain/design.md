# Design

## Context

See proposal.md - Why. Constraints that shape the approach:

- The branch starts from current `main` with the four `organize-init-by-toolchain` commits cherry-picked (`bfc98e1`, `3883270`, `b717b98`, `4a391c7`). They applied without conflict, and the architecture tests pass on the new base.
- The architecture tests already allow subpackages. `ArchitectureBoundarySupport.isAllowedPackage` accepts `dev.ward.(domain|application|infrastructure).<feature>(.<anything>)`, and `ArchitectureFeatureGraphTest` slices on `dev.ward.(domain|application).(*)..`, so `application.doctor.node` still counts as feature `doctor`.
- `ArchitectureBoundarySupport` names some classes by fully qualified name in its cross-feature allowlists: `application.gate.OpenSpecJunitTraceabilityGate`, `OpenSpecPrerequisites` (and `$Problem`), `OpenSpecQualityGate`, `CheckService`. Moving the OpenSpec gate classes means editing those names.
- `reachability-metadata.json` names no class in the touched packages. `pom.xml` targets PIT at `dev.ward.domain.*,dev.ward.application.*`.
- Pull request CI selects mutation targets with `git diff --diff-filter=ACMR`, so a rename counts as a change.
- Domain toolchain types do not reference each other's toolchain today. `PackageManifest` is Node-only but is used by init root classes (`InitInputs`, `InitSetupCatalog`, `CiWorkflowTemplate`) and by `config/`.
- Root classes that name toolchain classes today, the expected allowlist entries: `BuildToolFindings` (doctor), `ToolchainFindingSources` (finding), `QualityGateFactory` (gate: `MavenTestGate`, `SwiftGates`), and in `infrastructure/project` `FileSystemProjectInspection`, `RepositoryProjectScan`, `OwnLanguageMembers` and `BuildModules`.

## Goals / Non-Goals

**Goals:**
- One layout rule for the whole tree: a class tied to one toolchain lives in `<layer>/<feature>/<toolchain>/`.
- The generalized ArchUnit rule keeps every toolchain slice independent and every feature root behind an allowlist.

**Non-Goals:**
- Changing any strategy or registry shape. Unlike init, the doctor, finding and gate features already dispatch through a factory; this change moves files and fixes visibility only.
- A rule that infers a class's toolchain from its name.
- Splitting test packages beyond moving tests with their classes.

## Decisions

### 1. The rule decides by package; this design decides the assignment

The test checks dependencies between packages. Which package a class belongs to is decided once, in the assignment table below, and reviewed in each move step. Classes whose names do not show their toolchain (`PackageManifest`, `ManagedPluginCatalog`, `Simulators`) are assigned explicitly there.
- *Alternative:* a name guard that requires any class named `Node*`, `Swift*`, `Gradle*` ... to live in its slice. Rejected: the prefix list is long (`Pom`, `Pyproject`, `Xcode`, `Simctl`, `Javac`, `FastApi`, `Android`, `Kotlin`), it still misses the misnamed classes, and it needs exemptions for `config/`. A misplaced class with no cross-toolchain dependency is harmless, and the dependency rule catches the one that matters.

### 2. Slices span layers and features

A slice is every package `dev.ward.<layer>.<feature>.<toolchain>..`. The independence check runs between slices, not within a feature, so `application.init.python` may not use `domain.project.node` either.
- `node.turbo` belongs to the `node` slice.
- `gradle` and `maven` may depend on `jvm`; `jvm` may not depend on them, and they may not depend on each other. Today's init rule forbids `gradle` → `jvm`; it passes, so allowing the edge only loosens it where `infrastructure/project/gradle` needs `JavaVersions`.
- *Alternative:* nest them as `jvm/gradle` and `jvm/maven`. Rejected: it moves every init class a second time.

### 3. Root allowlist per layer, domain exempt

In `application` and `infrastructure`, a feature's root classes depend on toolchain packages only if the class is on the allowlist (a registry, a factory or a Dagger composer) or is Dagger-generated. Domain toolchain packages are shared vocabulary, like `Toolchain` and `BuildTool`: any class in any layer may import them, so `InitInputs` keeps using `domain.project.node.PackageManifest`.
- The allowlist is one list keyed by class simple name, as today. Each entry carries a one-line reason in the test.
- *Alternative:* keep domain flat. Rejected by the user: half of `domain/project` is toolchain-specific, and splitting it puts each port next to its adapter (`domain.project.swift.SwiftToolConfigs` and `infrastructure.project.swift.FileSystemSwiftToolConfigs`).

### 4. Folder names follow init

Application and domain use `jvm` for JVM-wide code. Infrastructure uses `gradle` or `maven` for build-tool code, and `jvm` for code that reads JVM tool output whatever the build tool (finding parsers, `JavaVersions`, traceability's Java parsers). Android is JVM: Android doctor checks go to `jvm`, Android Gradle code to `gradle`.

### 5. `openapi` and `openspec` are features, not slices

Only `application/gate` gets `openapi/` and `openspec/`, because each has a self-contained group there (7 and 3 classes, plus `ZapTargetValidation` and `TraceabilitySources` if their callers confirm it). Single classes elsewhere (`OpenSpecDoctorFindings`, `domain/doctor/OpenSpecIgnoreWarning`, `domain/init/OpenSpecDistribution`) stay in their roots. The rule does not treat these packages as slices, so roots may call them directly.

### 6. Rule first, then one feature per step

Generalize the test first; with no new subpackages it passes. Then each step moves one feature in all its layers, adds that feature's allowlist entries, and passes `ward check --profile pre-push`. Each step is one commit, made with `git mv`.

### 7. Assignment

| package | jvm | gradle | maven | node | python | swift | stays in root |
|---|---|---|---|---|---|---|---|
| domain/project | JavaSupport | | | NodeSupport, NodeSources, NodeToolConfigs, PackageManifest, PackageManifests | PythonSupport, PythonToolConfigs | SwiftSupport, SwiftToolConfigs, SwiftSourceDirectories, SwiftToolchainInspection, XcodeSchemes | BuildTool, Toolchain, Language, Framework, Project, TaskRunner, workspace and change types |
| domain/init | | GradleToolCatalog | ManagedPluginCatalog | turbo/: TurboWorkspace, TurboWorkspaceChange | | | ManagedPluginCandidate (both catalogs), OpenSpecDistribution, rest |
| domain/gate | | | | | | Simulators | rest |
| application/doctor | JvmToolchainFinding, AndroidFindings, AndroidGradlePluginRequirements, DependencyCheckDiagnosis | | | NodeToolchainFinding | PythonToolchainFinding | SwiftToolchainFinding | DoctorService, DoctorPorts, BuildToolFindings, ToolchainFinding, ToolchainContext, CheckTypeFindings, UncheckedToolchainFinding, OpenSpecDoctorFindings |
| application/gate | MavenTestGate | | | | | SwiftGates, XcodeTestGate | runner, factory, coverage, partial, profile; `openapi/` OpenApi*; `openspec/` OpenSpec* |
| infrastructure/project | JavaVersions, JvmSourceLanguages | GradleModuleScan, GradleKotlinModules, GradleAndroidModules, GradleCompatibilityInspection, GradleWrapperVersion, AndroidGradlePluginVersion | MavenJavaModules, MavenCompatibilityInspection | NodeProjectInspection, NodeWorkspaces, NodeWorkspaceMembers, FileSystemNodeSources, FileSystemNodeToolConfigs, FileSystemPackageManifests | PythonProjectInspection, PythonWorkspaces, PythonFrameworks, PyprojectDependencies, FileSystemPythonToolConfigs | SwiftProjectInspection, ProcessSwiftToolchainInspection, XcodebuildSchemes, FileSystemSwiftSourceDirectories, FileSystemSwiftToolConfigs | FileSystemProjectInspection, RepositoryProjectScan, GitProjectChanges, OwnLanguageMembers, BuildModules, ProjectDirectories, ProjectRoots, WorkspaceGlobs |
| infrastructure/finding | JvmFindingSources, CheckstyleXmlFindings, PmdXmlFindings, PitXmlMutationReport, JavacOutputFindings, SpotlessOutputFindings, AndroidJacocoCoverageReport | | | NodeFindingSources, EslintJsonFindings, BiomeCheckstyleFindings, TscOutputFindings, StrykerJsonMutationReport; turbo/: TurboOutput, TurborepoFindings | PythonFindingSources, RuffJsonFindings, MypyJsonFindings, MutmutMetaMutationReport | SwiftFindingSources, SwiftLintJsonFindings, SwiftFormatOutputFindings, SwiftCompilerOutputFindings, LlvmCovCoverageReport, XccovCoverageReport | ToolchainFindingSources, CompositeFindingExtractor, GateFindingSource, GitleaksSarifFindings, ReportFiles, SafeXml, OutputText, MalformedReportException, FileSystemReportDirectories |
| infrastructure/doctor | AndroidEnvironmentFacts, DependencyCheckSettings | | | | FastApiAppInspection | | SystemEnvironmentInspection, ExecutableLocator |
| infrastructure/gate | | | | | | SimctlSimulators | FileSystemProjectFiles, GitRevisionFiles |
| infrastructure/traceability | JavaMappingObservationParser, JavaSourceSanitizer, JavaTestMappingInventory | | | | | | rest |

`BuildModules` and `OwnLanguageMembers` name several toolchains. The move step either keeps them in the root as allowlisted composers or splits the toolchain branches out; it records which.

## Risks / Trade-offs

- [Mutation testing in the PR covers every moved domain and application class (renames match `ACMR`), so the job runs longer and each moved class must reach the 60% threshold] → These classes already pass under release-wide mutation. Run PIT locally with `PIT_TARGET_CLASSES` set to the moved packages before pushing.
- [PIT's `dev.ward.domain.*` might not match subpackages] → Init already added subpackages; check once that a `domain.project.node` class shows up in the PIT report.
- [Package-private types become `public` and need Javadoc; PMD may flag coupling] → Fix each finding, no suppressions. Each step lists the types it made public; anything outside a strategy, port or record that crosses a slice is questioned.
- [A slice dependency that exists today fails the new rule, such as a Python class that reads `PackageManifest`] → The step that moves it shows the violation; fix it by moving the shared code to the root, not by adding an allowlist entry.
- [`ArchitectureBoundarySupport` FQN allowlists go stale] → The gate move step updates them; `ArchitectureFeatureGraphTest` fails if one is missed.
- [One PR carrying both changes is large (init alone touched 319 files)] → Commits stay one per step, and behaviour-preserving moves stay separate from any code edits.

## Migration Plan

Internal only. Each step compiles and passes `ward check --profile pre-push` on its own, so the work can stop after any step. Rollback is reverting the step's commit.

## Decisions made during apply

The rule first (task 2) and then the moves found these cases the table did not settle. Each one is an allowlist entry in `ArchitectureToolchainTest` with its reason, or a placement change.

- **Root composers that stay in the root (4.2).** `BuildModules` and `OwnLanguageMembers` stay in `infrastructure/project` as allowlisted composers. Splitting their branches would move the Node and Python workspace checks out of the code that decides module membership, for no change in dependency direction.
- **Root composers added beyond the expected list.** `CompositeFindingExtractor` (5.2) names the Turborepo wrapper, `DoctorService` (6.3) runs the JVM dependency-check diagnosis, and `SystemEnvironmentInspection` (6.3) reads each toolchain's environment facts. Each is allowlisted with its reason rather than split, to keep this change behaviour-preserving.
- **`AndroidEnvironmentFacts` goes to `infrastructure/doctor/gradle`, not `jvm` (6.2).** It reads the AGP version and the Gradle wrapper version through `infrastructure.project.gradle`. Under the independence rule `jvm` may not depend on `gradle`, so the class follows its inputs. Its SDK-variable check is the only JVM-only part.
- **`ZapTargetValidation` and `TraceabilitySources` stay in `application/gate` (7.2).** Their callers span `QualityGateFactory`, the config module and tests outside the `openapi` and `openspec` groups, so neither moves.
- **`QualityGateFactory` is allowlisted (7.4)**, as the registry that maps each gate to its implementation.
- **Traceability needs no allowlist entry (8.1).** No root class of `infrastructure/traceability` names `jvm` classes, so `JavaTestMappingInventory` is reached only by `config/`.
- **CLI adapters are outside the root rule.** `infrastructure.cli.init` (`InitWizard`, `ToolQuestions`) calls toolchain statics such as `JdkGates` and `XcodeSchemeChoices` today. The root rule covers `dev.ward.application.*` and `dev.ward.infrastructure.*` roots only, so these calls stay as they are; moving them behind the registry is a follow-up.
- **Native metadata.** `native-image.properties` lists the new `finding.node`, `finding.python`, `finding.swift` and `gate.swift` packages, because they hold `@JsonType` classes that `NativeJsonCodecMetadataTest` requires at build time.

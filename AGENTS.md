# AGENTS.md

## Architecture

- Organize production packages layer-first as `domain/<feature>`, `application/<feature>`, and `infrastructure/<feature>`.
- CLI commands are inbound adapters under `infrastructure/cli/<feature>`; shared Picocli plumbing lives in `infrastructure/cli`.
- Dependency direction is `infrastructure.cli → application → domain`; outbound adapters under `infrastructure/<feature>` implement domain ports. CLI adapters must not depend on concrete outbound adapters or perform filesystem/process work.
- Keep domain models free of I/O and framework implementations; application orchestrates domain contracts without depending on infrastructure. `ArchitectureBoundaryTest` enforces bytecode boundaries, `ArchitecturePlacementTest` placement, and `ArchitectureFeatureGraphTest` feature dependencies; `ArchitectureRuleFixturesTest` proves the rules reject violating fixtures.
- Raw-key terminal input uses the `RawTerminal` port and POSIX adapter; do not launch `stty` or another process from CLI code. Route filesystem checks through application use cases and domain ports.
- `QualityGate` is the extension point; update `QualityGateFactory` and `ward.toml` together when adding a gate.
- `dev.ward.config.WardComponent` wires Dagger at compile time; preserve the explicit constructor graph for native-image compatibility.
- Native images contain only classes seen at build time. Do not call APIs that build classes at runtime: `MethodHandleProxies`, `MethodHandles.Lookup#defineHiddenClass` / `defineClass`, and similar (`java.lang.classfile`, custom class loaders). `NativeImageCompatibilityTest` rejects them. Register any `java.lang.reflect.Proxy` interface, reflective type, or FFM downcall in `src/main/resources/META-INF/native-image/dev.ward/ward-cli/reachability-metadata.json`.

## Adding a toolchain, language or tool

Ward resolves behavior along three axes. Add classes and rows; do not add `BuildTool` or `Language` branches outside the owning support. `GateCatalogContractTest`, `PomToolsContractTest` and `FindingSourceContractTest` name any missing piece.

- **Toolchain** (`domain/project/Toolchain`, from `BuildTool`): implement `ToolchainInitSupport` (gates, preset gates, CI setup) and register it in `InitToolchains`; implement `BuildToolConfiguration` and register it in `BuildToolBindingsModule`; implement `ToolchainFinding` for its doctor checks and add its case to `BuildToolFindings.forToolchain` (see Design patterns).
- **Language**: add the `Language` constant and its detection in `infrastructure/project` (from sources, not build files); implement `SourceLanguageGates` for its toolchain (analysis gates and preset gate sets) and add it to that toolchain's language map. A language without an entry fails init by design.
- **Tool**: add its row to `QualityGateDefinitions` (ID, display name, phase, learning); offer it from a `SourceLanguageGates`; add its build-file entry for each build tool (`PomTools` and `ManagedPluginCatalog`, Gradle `PLUGINS` or `GradleBuildBlocks`); add optional rows in `PartialCheckDefaults`, `AgentRulesTemplate`, preset rule files and a `GateFindingSource`. Update `ward.toml` and the `website/` gate docs.

## Design patterns

Pick the pattern from the scenario, and copy the existing example rather than inventing a new shape. Use a pattern only when the scenario is there; a single case with no variation needs no pattern.

| Scenario | Pattern | Example in this repo |
|---|---|---|
| Behavior varies by a closed enum (`Toolchain`, `BuildTool`) | Strategy behind a factory | `ToolchainFinding` → `BuildToolFindings.forToolchain` |
| Many interchangeable checks chosen by id or config | Strategy + factory | `QualityGate` → `QualityGateFactory` |
| Variants map to Dagger-provided support objects | Registry (`Map`) | `InitToolchains` |
| Domain needs I/O or a platform call | Port (domain interface) + adapter (`infrastructure`) | `RawTerminal` → `PosixRawTerminal`; `ProjectInspection` → `FileSystemProjectInspection` |
| A use case coordinates several domain ports | Facade (application service) | `CheckService`, `DoctorService` |
| Callers should observe progress without knowing the sink | Observer (listener interface) | `CheckProgressListener` |
| Immutable data passed between layers | Value object (record) | `Project`, `Finding` |
| Wiring the object graph | Constructor injection through Dagger | `WardComponent`, `ProjectBindingsModule` |

Rules for each pattern:

- **Strategy.** Declare the interface in the layer that owns the behavior (`domain` for ports, `application` for doctor and init checks). Keep single-method interfaces `@FunctionalInterface` (PMD `ImplicitFunctionalInterface`) and package-private unless another package needs them. Name the method for what the caller does, with a verb (`check`).
- **Implementations.** Name them `<Variant><Feature>`. Make them `final` and stateless. Keep each private helper in the one implementation that uses it; extract a package-private helper only when two implementations need the same code.
- **Factory.** A `final` class with a private constructor and a static `forX(...)` method. Use an exhaustive `switch` expression. Checkstyle requires a `default`, so map unsupported variants to a warning implementation, not an exception. Stateless implementations may be created per call.
- **Registry.** Use a `Map` when the values are injected objects. Use a `switch` when each variant has its own logic. Use a table of rows (such as `QualityGateDefinitions`) when the variants are only data.
- **Port and adapter.** Put the interface in `domain` with no I/O types leaking into it. The adapter lives in `infrastructure/<feature>` and is wired in a Dagger module. CLI code uses the port and never the adapter, and it does not start processes (see Architecture).
- **Facade.** Keep it thin: it orchestrates ports and domain objects, and holds no parsing or I/O logic itself.
- **Observer.** Keep listeners optional with a no-op default, and do not let a listener change the result of the operation.
- **Value objects.** Use records for data crossing layers. Copy collections in and out, as SpotBugs requires.

Avoid in this codebase:

- Reflection, `java.lang.reflect.Proxy`, or runtime class generation for decorators or proxies. Native images only contain classes seen at build time (see Architecture), so use explicit wrapper classes instead.
- Static mutable state, and the Singleton pattern. Use a Dagger-scoped object injected through the constructor.
- Field injection, as stated under Code Style.
- Generic "manager" or "helper" classes that gather unrelated variants. Name the class after the one variant or feature it serves.
- A pattern added for one case. Wait for the second variant, then extract it.

Tests:

- Test each strategy through the caller's public behavior (for example `DoctorNodeTest`).
- When a factory or registry covers an enum, add a test that every constant maps to an implementation, so a new constant without one fails the build.
- New behavior behind a port is tested with a fake implementation, not the real adapter (`FakeCommandExecutor` for processes).

## Code Style

- Use constructor injection; do not add field injection.
- Spotless with Google Java Format is the Java formatter; run `./mvnw spotless:apply` to format and `./mvnw spotless:check` to verify. Spring/JPA and code-generation workflows are not configured.
- Fix the underlying cause of compiler and static-analysis findings. Do not add `@SuppressWarnings`, `@SuppressFBWarnings`, `NOPMD`, exclusions, disable checks, or lower thresholds to make verification pass. Preserve behavior and test coverage when refactoring. If a finding appears to be a framework limitation or false positive, explain it with evidence and request explicit review before introducing a suppression or workaround.
- Commands from `ward.toml` run from the project root as argument vectors, never through a shell.
- When implementing or changing operational behavior, add useful JUL diagnostics at meaningful decision and execution boundaries when logs would aid troubleshooting. Keep logs invocation-scoped, stderr-only, and safe: never log secrets, prompts, configuration or environment contents, raw argument vectors, or captured process output.

## Quality Gate Rules

Gates in `ward.toml`; edit hooks run the `default` profile (format, checkstyle, pmd, gitleaks) on edited files. Checkstyle and PMD also cover tests.

- Checkstyle (`config/checkstyle.xml`): file ≤500 lines, method ≤60, ≤5 params, cyclomatic ≤10; Javadoc on public types and methods; no magic numbers (use named constants); no star/unused imports; no `catch (Exception|RuntimeException|Throwable)`; no empty catch; `switch` needs `default`; one declaration/statement per line; no field-hiding locals/params except constructors and setters.
- PMD (`config/pmd-ruleset.xml`): all errorprone, bestpractices, design, codestyle, multithreading, performance, security categories minus listed excludes. Mark locals and parameters `final`; no `var`; do not call `new` inside a loop body (map with a stream or extract a factory method); ≤8 duplicate literals per file; keep classes small (design rules flag god classes, too many methods/imports, coupling, cognitive complexity).
- SpotBugs: effort Max, threshold Medium; only generated Dagger/Fory classes excluded. Copy mutable collections in and out of records/constructors.
- Coverage (JaCoCo): bundle ≥85% line, ≥75% branch. Mutation (PIT, `ci`): ≥60%; pull request CI mutates only the domain/application classes the change touches (`PIT_TARGET_CLASSES`), releases mutate all. New code needs tests that assert behavior.
- ArchUnit gate runs a fixed test list; add any new architecture/native contract test to that command.
- Enforcer (`config/enforcer-rules.xml`): pin every plugin version, no dynamic versions, converging dependencies, Maven ≥3.9.
- License: no GPL/AGPL dependencies.
- gitleaks: no secrets, including in tests and fixtures. Semgrep `p/java` and Trivy (any vulnerable dependency, including `website/`) run in `ci`.

## Testing

- JDK 25 and Maven wrapper are required: `./mvnw -q test`.
- Run one test with `./mvnw -Dtest=ClassName#methodName test`.
- CLI integration tests use the offline fixture `src/test/resources/fixtures/maven-project` and its stub `mvnw`.
- Use `FakeCommandExecutor` for process tests. Existing test names use descriptive camelCase.
- `NativeInitTerminalSmokeTest` drives a built executable's `init` wizard through a pseudo-terminal and is skipped unless `-Dward.native.binary` is set: `./mvnw -Dtest=NativeInitTerminalSmokeTest -Dward.native.binary=target/ward test` after `native:compile`.
- `GradleMutationJava25SmokeTest` runs the generated Gradle `pitest` task on Java 25 and is skipped unless `-Dward.gradle.binary` points to a Gradle executable (needs network).
- Testcontainers is not configured; do not invent or claim that check.

## Security

- Treat `ward.toml` as executable trusted code; do not run it on untrusted repositories without isolation.
- Never log secrets or full request/response bodies; use environment variables for credentials.
- Integration files are ownership-marked: preserve conflict handling, rollback, and user-owned files.

## Verification (non-negotiable)

- Do not modify anything under `/config`.
- Run `ward check --profile pre-push` before finishing. Gates and profiles are defined in `ward.toml`; `ward check --profile ci` runs every gate.
- Claude Code and OpenCode edit/write hooks must invoke `ward check` through the Ward integration: `.claude/hooks/ward-edit-write` (registered in `.claude/settings.json`) and `.opencode/plugins/ward-edit-write.js`.
- `check --format json` must write only JSON to stdout; diagnostics go to stderr.
- CLI changes must update the matching documentation pages under `website/`.
- Changes to `ward init` options or behavior must update the agent skills under `skills/` in the same pull request; `AgentSkillsConsistencyTest` checks options and versions, not wording.
- `./mvnw package` builds `target/ward.jar`; native builds require GraalVM 25 and `./mvnw -Pnative -DskipTests native:compile`.

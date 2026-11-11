# Changelog

All notable changes to Ward are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and Ward uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html). Ward is in alpha, so minor versions may
still change behavior. Breaking changes are called out under **Changed**.

## [Unreleased]

## [0.7.0] - 2026-11-11

### Added

- Five native Java rules that this repository's Checkstyle and PMD configuration enforces and Ward lacked: `ward:java/simplify-boolean-expression` (`x == true`, `x && true`, `c ? true : false`), `ward:java/unnecessary-parentheses` (precedence-clarifying parentheses are not reported), `ward:java/unused-private-field`, `ward:java/final-parameters` (a parameter the body uses and never assigns) and `ward:java/avoid-no-argument-super-constructor-call`. `ward:java/unnecessary-constructor` is now its own rule (#491).
- Thirteen native Python rules from the optional Pylint extensions, in the `strict` preset: `while-used`, `bad-builtin`, `bad-dunder-name`, `consider-math-not-float`, `consider-using-tuple`, `prefer-typing-namedtuple`, `consider-refactoring-into-while-condition`, `consider-ternary-expression`, `too-many-try-statements` (option `max-try-statements`, default 1), `overlapping-except`, `consider-using-assignment-expr`, `confusing-consecutive-elif` and `eq-without-hash`. Fourteen more Pylint checks are covered by existing rules (#336).
- Option `min-length` for the repeated string literal rules. `ward:java/multiple-string-literals` sets it to 3, so literals shorter than three characters are ignored, as PMD's `AvoidDuplicateLiterals` does; the default 0 keeps the other languages unchanged (#491).

### Changed

- **Breaking (alpha, no migration):** Rule categories are named after what a rule checks, not after the tool it came from. The 46 categories become 16: `accessibility`, `angular`, `architecture`, `complexity`, `concurrency`, `correctness`, `dead-code`, `docs`, `duplication`, `error-handling`, `experimental`, `naming`, `null-safety`, `performance`, `security` and `style`. For example `errorprone`, `bad-practice`, `problem` and `pylint` are now `correctness`; `sizes`, `metrics` and `design` are `complexity`; `javadoc` is `docs`; `mt-correctness` and `multithreading` are `concurrency`; `nullness` is `null-safety`; `a11y` is `accessibility`. Rule ids do not change. Update `categories` in `ward-rules.toml` and `ward rules list --category`; an old name is reported as an unknown category with the list of valid ones.
- The `ward` gate parses each source unit once per run. Type facts for Java, Node and Swift share the gate's parser, and the parse cache is bounded by source size instead of 64 entries. Measured on a 98-file TypeScript project: 457 parses become 98 and parse time drops from 209 s to 69 s, with an identical report (#502).
- The `ward` gate needs much less memory on large Java and Kotlin projects: the members of a class that every file shares are read once, not once per typed expression. On this repository the peak went from 9.0 GB to 3.3 GB with an identical report (#493). The members of a named TypeScript type are cached per file in the same way (#494).

### Fixed

- The `ward` gate no longer crashes with a `NullPointerException` on a JavaScript class that declares a `get` or `set` accessor. A getter is now a property of unknown type for the type-aware rules, and a setter declares nothing, as for TypeScript classes.
- Ten Java rules reported code that the tool they mirror accepts. They now follow that tool's documented behaviour (#491):
  - `ward:java/avoid-instantiating-objects-in-loops` follows PMD's exemptions, such as `throw new` inside a loop.
  - `ward:java/avoid-literals-in-if-condition` reports only literals that are direct operands of the condition, and ignores `-1` and `0` by default.
  - `ward:java/unused-private-method` counts method references such as `Type::method`, and PMD's exemptions.
  - `ward:java/immutable-field` reports a field only when every write is in its initializer or a constructor, and leaves injected annotated fields alone.
  - `ward:java/magic-number` waives literals that initialize a `final` variable, as Checkstyle does.
  - `ward:java/public-member-in-non-public-type` follows the effective visibility of the member: interface members, `@Override` methods and `main` are not reported, and the finding is on the `public` modifier.
  - `ward:java/signature-declare-throws-exception` reports only an exact `Exception`, and not tests or overriding methods.
  - `ward:java/avoid-deeply-nested-if-stmts` no longer counts each `else if` of a chain as another nesting level; only a plain `else` block adds one, as in PMD.
  - `ward:java/confusing-argument-to-varargs-method` no longer reports a generic varargs method, whose erased parameter hides the inferred element type, or an array passed for a varargs type that an array cannot be an element of, such as `OpenOption...`.
  - `ward:java/string-instantiation` reports only `new String(value)` with a `String` argument, as PMD does, and no longer a `String` built from bytes or characters.

## [0.6.0] - 2026-11-10

### Added

- Native TypeScript and JavaScript type resolution for the type-aware rules, with no `node` or TypeScript compiler process: types come from the project sources, the `.d.ts` files of the project and of the packages in `node_modules` (`types`, `typings`, `@types/*`) and the library of the installed `typescript` package, and what needs full checking is unknown. Package indexes are kept under `.ward/cache/types/node/`, named by the SHA-256 of the package. The TypeScript grammar now accepts ambient declarations (`declare function` and `declare class`, `declare global`, accessors and index signatures in declarations, labeled tuple elements and assertion signatures).
- Native Java rules `ward:java/no-field-di` and `ward:java/no-standard-streams` in the `strict` preset: a field annotated with `@Inject`, `@Autowired` or `@Resource` is reported, and so is any access to `System.out`, `System.err` or `System.in`. They replace the two checks that only the generated ArchUnit test enforced (#374).
- `ward-rules.toml`, an optional file that selects the rules of the `ward` gate (`[select]`: preset, languages, categories, include, exclude) and tunes them per rule (`max`, name patterns, `severity`, `exclude-paths`), validated with every problem reported by file, table and key and checked by `ward doctor`. `rules-file` in `[checks.ward]` points to another file. A `rules` list in `ward.toml` together with the file is an error. There are no suppressions of any kind (#280).
- `ward-frontend-antlr`, an internal analysis module with ANTLR parsers for Java, Kotlin, TypeScript, JavaScript, Python and Swift. Parsers are generated at build time from grammars pinned by commit, and syntax errors are returned as diagnostics next to a partial tree. No CLI behavior changes.
- `ward format [--check] [--format text|json] [<path>...]`, Ward's native formatter for the sources of Java, Kotlin, TypeScript, JavaScript, Python and Swift, in Ward style and with no external formatter. Each file is checked for syntax, tokens, syntax-tree shape and idempotence before it is written. It writes atomically and keeps file permissions; `--check` writes nothing and exits 1 when a file needs formatting. Settings come from `.editorconfig`. Symbolic links are reported and never rewritten.
- The `ward-format` quality gate (`uses = "ward-format"`, phase FAST), which reports one error per file that `ward format` would change, at its first differing line. Refused files and invalid `.editorconfig` settings are execution errors, never a pass. It supports partial runs and caches results by content and settings.
- `ward:architecture/cycles` and `ward:architecture/independence`, the slice cycle and feature independence rules of the `ward` gate, configured by `[checks.ward.cycles]` and `[[checks.ward.independence]]`. One meaning for Java, Python, TypeScript, JavaScript and Swift: a cycle is reported between two or more slices, the first directory under a source root (#246).

### Changed

- **Breaking (alpha, no migration):** Inside `ward-rules.toml` a rule is named `<language>.<rule-name>` only: `[rules.java.hidden-field]` for its table, and `"java.star-import"` in `[select]` `include` and `exclude`. A table named by the quoted rule id (`[rules."ward:java/hidden-field"]`) or a list entry written as the id (`"ward:java/star-import"`) is a configuration error that names the form to write. `ward init --preset strict` writes `[rules.java.hidden-field]`, `ward rules enable` and `disable` still take the printed id and write the dotted name, and the JSON schema lists only the dotted names. Rule ids are unchanged wherever Ward prints them (findings, JSON, SARIF, `ward rules`) and in the `rules` list of `ward.toml`.
- **Breaking (alpha, no migration):** `ward init` on a TypeScript or JavaScript project (npm, pnpm, Yarn and Bun, `standard` and `strict`, Angular, Turborepo members included) no longer generates ESLint (with typescript-eslint and angular-eslint), Biome's lint or Semgrep's `p/javascript` and `p/typescript` packs: no gate, no config file, no package entry, no script, no partial-run table, and no agent rule for them. It writes `ward-rules.toml` with the preset and the project's languages (`"typescript"`, `"javascript"`), so the `ward` gate applies Ward's rules instead. Ward's rules replace the standard rows of those tools; the Node compatibility matrix expects their findings from the `ward` gate (#343).
- **Breaking (alpha, no migration):** The `swiftlint` gate type is removed. A check with `uses = "swiftlint"` is rejected as an unknown gate type. A `swiftlint lint` command is a plain `command` check, with no structured findings and no learning. Init still removes the Ward-owned SwiftLint files that an earlier init wrote. Ward's own Swift rules (`ward:swift/*`) are unchanged.
- **Breaking (alpha, no migration):** `ward init` on a Swift project (SwiftPM and Xcode, `standard` and `strict`) no longer generates SwiftLint: no `swiftlint` gate, no `.swiftlint.yml`, no `.ward/swiftlint-strict.yml`, no `.ward/swift-tools/` package, no resolve or CI cache step, and no `.ward/swift-tools/.build/` ignore entry. The `ward` check applies Ward's Swift rules instead, and init writes `ward-rules.toml` with `languages = ["java", "swift"]`. Re-running init removes the Ward-owned SwiftLint files of an earlier init and keeps a `.swiftlint.yml` you wrote. Strict Swift runs no `--strict` SwiftLint (#345, #457).
- **Breaking (alpha, no migration):** `ward init` on a Java project (Maven and Gradle, `standard` and `strict`) no longer generates Checkstyle, PMD or SpotBugs: no gate, no rule file under `config/`, no build plugin or block, no partial-run table, and the agent rules no longer quote their limits. The `ward` check applies Ward's Java rules instead, and init writes an ownership-marked `ward-rules.toml` with `[select] preset = "<preset>"` and `languages = ["java"]`. The file is written for every project, so the Ward rules of Kotlin, TypeScript, JavaScript, Python and Swift stay off, and each of those keeps its external tools until its own migration lands (#342, #460). A Kotlin project still gets the `checkstyle`, `pmd` and `spotbugs` gates.
- **Breaking (alpha, no migration):** Ward no longer has a `ruff` gate type. A check with `uses = "ruff"` is rejected as an unknown gate type, and a `ruff check` command is a plain `command` check with no findings. Ruff, Pylint and vulture are removed from the docs as Ward gates; Ward's Python rules replace their lint checks.
- **Breaking (alpha, no migration):** `ward init` on a Python project (uv, Poetry or pip, `standard` and `strict`) no longer offers, selects, pins, configures or documents Ruff: the `ruff` gate is gone from both presets, and init writes no `ruff.toml`, no `ruff` package pin, no Ruff partial-run table and no Ruff agent rule. The `ward-rules.toml` that init writes lists `languages = ["java", "python"]`, so the `ward` check applies Ward's Python rules of the preset. A project's own `ruff.toml` is left unchanged. The `ruff` gate type stays until #452 (#344, #456).
- **Breaking (alpha, no migration):** Ward checks architecture with its own `ward:architecture/*` rules only. `ward init` always writes the `ward` layer, cycle and independence tables (`[[checks.ward.layers]]`, `[checks.ward.cycles]`, `[[checks.ward.independence]]`), and `--architecture-engine` is gone, with no `tool` engine or golden. Init no longer generates an ArchUnit test, a dependency-cruiser config, an Import Linter config or Harmonize rules, and no longer offers the `archunit`, `dependency-cruiser`, `import-linter` or `harmonize` gates; `uses = "archunit"`, `"dependency-cruiser"`, `"import-linter"` or `"harmonize"` is rejected. A `command` check that runs one of those tools still works as a custom check. The strict preset's ArchUnit coding rules are the native `ward:java/no-field-di` and `ward:java/no-standard-streams` rules (#374).
- **Breaking (alpha, no migration):** Ward formats with `ward-format` alone. `ward init` always selects the `ward-format` gate and writes an ownership-marked `.editorconfig` with Ward's defaults when the project has none. The external formatters are gone: the Spotless `format` gate (Maven and Gradle), `prettier`, `ruff-format` and `swift-format`, with their init generation, plugins, packages, config files, finding parsers and partial-run tables. Biome stays as the linter with its formatter off. `ward init` has no `--format-engine` option, and no `tool` engine or golden remains. A `ward.toml` that names one of the removed gates (`uses = "format"`, `"prettier"`, `"ruff-format"` or `"swift-format"`) is rejected with the list of supported types; a `command` check that runs the tool still works as a custom check.
- Ward's Kotlin rules replace detekt and ktlint. Ward never ran either tool, so nothing changes at run time; the docs no longer present them as tools Ward measures against, and a contract test keeps both out of the gates, finding sources, plugins and main classes (#450).
- `ward init --preset strict` writes the cycle and independence tables instead of ArchUnit's cycle rule, dependency-cruiser's `no-circular` and Import Linter's independence contract. Inside one slice, file cycles are not reported (#246, #374).
- The analysis API's `LanguageFrontend.parse` returns a `ParseResult` (syntax tree plus diagnostics) instead of a bare `SyntaxNode`. The analysis API is unreleased, so no released behavior changes.
- The Maven build is a parent project (`ward-parent`) with a `ward-cli` module. Build outputs moved to `ward-cli/target/` (`ward.jar`, `ward`, `ward.exe`); the released binaries and jar keep their names.

### Removed

- **BREAKING:** No macOS Intel (x86_64) binary is published, and `install.sh` stops on an Intel Mac with a message. Native builds use GraalVM Community 25.5, which has no macOS x64 release. On an Intel Mac, build `ward.jar` from source and run it on JDK 25.
- **BREAKING (alpha, no migration):** Ward no longer has the `eslint` and `biome` gate types, their finding sources, or the Semgrep `p/javascript` and `p/typescript` packs. A `ward.toml` that names `uses = "eslint"` or `uses = "biome"` is rejected with the list of supported types, and a `command` check that runs `eslint` or `biome` is a plain command check with no structured findings and no learning. The Semgrep gate stays for Python (#451, #455).
- **BREAKING:** Ward no longer wires Checkstyle, PMD, SpotBugs, Error Prone, NullAway or Semgrep's `p/java` pack. Their gates, finding sources, Maven and Gradle plugin declarations, preset rule files and init defaults are gone. Java and Kotlin projects use the `ward` gate and `ward-rules.toml`; Semgrep stays for TypeScript, JavaScript and Python, each with its own pack.

### Fixed

- Learning (`ward check --learn-after`) tracks the rule findings of the `ward` gate again, as documented. Since the external Java tools were removed it recorded nothing for Java, Kotlin and Swift projects.
- `ward init` on a Maven aggregator without its own sources places the architecture test in the module that owns them.

## [0.5.0] - 2026-11-02

### Added

- Swift support for SwiftPM and Xcode projects, with Swift quality gates and an Xcode version check.
- Android project detection and Android quality gates.
- Java quality gates placed in each module of multi-module builds, and in Kotlin module scripts for Gradle.

### Changed

- Feature code is split by toolchain across the application and infrastructure layers.

## [0.4.1] - 2026-10-31

### Added

- OpenAPI gate recommendations from the detected framework, with pinned tools installed by `init`.
- Turborepo support for other-language members, configured and checked by Ward.

### Changed

- Documentation positions Ward as the quality gate for AI coding agents.

## [0.4.0] - 2026-10-31

### Added

- Python support for `init`, gates, and frameworks, including Python detection and package managers.
- Turborepo workspaces: planning, member scripts, and `turbo.json` tasks written through `ward init`.

### Changed

- `ward.toml` moves to configuration version 2.

## [0.3.0] - 2026-10-29

### Added

- Free-named checks, tool types, and an edit profile.

### Changed

- **Breaking:** the configuration changes to free-named checks and tool types. Projects on the old
  format must migrate their `ward.toml`.

## [0.2.1] - 2026-10-29

### Added

- The `ward-fix` agent skill, which fixes failing gates in the code.
- Framework labels for Maven and Gradle projects, such as Spring Boot, Quarkus, Micronaut, and Helidon.
- Angular 21 and 22 support, and Vue and Svelte matrix cells.
- Explanations for gates that cannot run in the current environment.

### Changed

- Documentation covers TypeScript and JavaScript support.

## [0.2.0] - 2026-10-27

### Added

- TypeScript and JavaScript support: detection, `init`, and Node quality gates (ESLint, `tsc`, Stryker).
- Presets for TypeScript and JavaScript, including a `strict` preset.
- Angular, Vue, Svelte, and Astro setup, with each framework's own type checker.
- Biome support, and dependency-cruiser architecture rules for Node projects.
- A Node compatibility matrix, and a warning for Node versions below 22.
- A Java compatibility matrix, and Java tool versions chosen for the build's JDK.
- Junie CLI integration.

## [0.1.3] - 2026-10-24

### Added

- Windows native executable verification before release.

### Changed

- Mutation testing runs only on changed classes in pull requests. Native checks run on Windows.

### Fixed

- The license and gitleaks gates now catch what they claim to.

## [0.1.2] - 2026-10-24

### Added

- OpenAPI contract gates with vacuum and oasdiff.
- A PowerShell installer and Git Bash support for Windows.
- `init` support for Java projects nested in monorepos.

### Changed

- The website is easier to scan, and CI actions move to Node 24.

## [0.1.1] - 2026-10-22

### Changed

- **Breaking:** the project is renamed from Sentinel to Ward. Commands, configuration, and binaries use `ward`.

## [0.1.0] - 2026-10-21

### Added

- Generated GitHub CI workflow and `AGENTS.md` gate rules from `init`.
- Git quality hooks with JSON output.
- SARIF upload to GitHub code scanning.
- A notice when a newer release is available.
- Learning from recurring rule findings instead of gate failures.

### Fixed

- Compact JSON and SARIF reports from `check`.
- Java 25 compatible PIT configuration from `init`.

## [0.0.11] - 2026-10-19

### Added

- `check` runs only the gates for edited files after each agent edit.

### Fixed

- Partial checks match edited paths reached through symlinks.

## [0.0.10] - 2026-10-18

### Added

- The Sentinel agent skills plugin, with `sentinel-init` and `sentinel-tune`.
- Severity filtering for `check` output.
- Improved gate setup in `init`.

### Fixed

- Files generated by `init` work on real Maven and Gradle projects.

## [0.0.9] - 2026-10-17

### Added

- An exact change plan that `init` shows before it writes, with `--dry-run`.
- Reconfigure pre-fill, availability checks, and progress in the init wizard.

### Fixed

- Optional init questions are asked on a terminal, and unticked preset gates are honored.
- Maven tools are placed correctly, and `pom.xml` is validated before writing.

## [0.0.8] - 2026-10-17

### Added

- Verification of all agent harness integrations, with repaired adapters.
- Native-image compatibility checks in pull requests and releases.

### Fixed

- The build version is reported from native images.

## [0.0.7] - 2026-10-16

### Fixed

- Finding JSON codecs are initialized at build time for the native image.

## [0.0.6] - 2026-10-16

### Added

- Structured findings and SARIF output for `check`.
- Multi-agent harness integrations and OpenCode V2 integration.
- Gradle build support.
- Project-aware quality gate version compatibility.
- `init` quality profiles and Git hooks.
- Apache-2.0 license, with notices shipped in releases.
- A CI provider integration guide.

### Fixed

- The command reference link in the docs.

## [0.0.5] - 2026-10-14

### Added

- OpenSpec validation loop for agent integrations, and the OpenSpec JUnit traceability gate.
- OpenSpec change provenance enforcement, and guards for approved OpenSpec artifacts.
- OpenSpec validation tool integration.

## [0.0.4] - 2026-10-10

### Added

- Invocation-scoped verbose diagnostics.

### Changed

- Comprehensive PMD categories are enabled, and style findings are fixed.

## [0.0.3] - 2026-10-10

### Fixed

- The release publish job.

## [0.0.2] - 2026-10-10

### Added

- A release install script.

### Changed

- The README is rewritten, and docs are checked against the CLI.

### Fixed

- The retired macOS runner is replaced.

## [0.0.1] - 2026-10-08

Initial release, published as `sentinel-ai`.

### Added

- Quality gates for Java projects: format, Checkstyle, PMD, SpotBugs, JaCoCo coverage, PIT mutation
  testing, ArchUnit, Semgrep, gitleaks, Trivy, Enforcer, license, and API compatibility.
- Quality presets, named profiles, and a live quality check report.
- Agent hooks and an OpenCode plugin that run quality checks after agent edits.
- `init` with interactive setup, and native binaries published in releases.
- A bounded quality loop and a typed gate registry.
- Starlight documentation site.

[0.7.0]: https://github.com/RicardoRB/ward-ai/compare/v0.6.0...v0.7.0
[0.6.0]: https://github.com/RicardoRB/ward-ai/compare/v0.5.0...v0.6.0
[0.5.0]: https://github.com/RicardoRB/ward-ai/compare/v0.4.1...v0.5.0
[0.4.1]: https://github.com/RicardoRB/ward-ai/compare/v0.4.0...v0.4.1
[0.4.0]: https://github.com/RicardoRB/ward-ai/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/RicardoRB/ward-ai/compare/v0.2.1...v0.3.0
[0.2.1]: https://github.com/RicardoRB/ward-ai/compare/v0.2.0...v0.2.1
[0.2.0]: https://github.com/RicardoRB/ward-ai/compare/v0.1.3...v0.2.0
[0.1.3]: https://github.com/RicardoRB/ward-ai/compare/v0.1.2...v0.1.3
[0.1.2]: https://github.com/RicardoRB/ward-ai/compare/v0.1.1...v0.1.2
[0.1.1]: https://github.com/RicardoRB/ward-ai/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/RicardoRB/ward-ai/compare/v0.0.11...v0.1.0
[0.0.11]: https://github.com/RicardoRB/ward-ai/compare/v0.0.10...v0.0.11
[0.0.10]: https://github.com/RicardoRB/ward-ai/compare/v0.0.9...v0.0.10
[0.0.9]: https://github.com/RicardoRB/ward-ai/compare/v0.0.8...v0.0.9
[0.0.8]: https://github.com/RicardoRB/ward-ai/compare/v0.0.7...v0.0.8
[0.0.7]: https://github.com/RicardoRB/ward-ai/compare/v0.0.6...v0.0.7
[0.0.6]: https://github.com/RicardoRB/ward-ai/compare/v0.0.5...v0.0.6
[0.0.5]: https://github.com/RicardoRB/ward-ai/compare/v0.0.4...v0.0.5
[0.0.4]: https://github.com/RicardoRB/ward-ai/compare/v0.0.3...v0.0.4
[0.0.3]: https://github.com/RicardoRB/ward-ai/compare/v0.0.2...v0.0.3
[0.0.2]: https://github.com/RicardoRB/ward-ai/compare/v0.0.1...v0.0.2
[0.0.1]: https://github.com/RicardoRB/ward-ai/releases/tag/v0.0.1

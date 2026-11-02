# Proposal

## Why

`organize-init-by-toolchain` gave `init` one subpackage per toolchain and an ArchUnit rule that keeps them apart, but the rest of the tree is still flat: `infrastructure/project` (34 classes), `infrastructure/finding` (33), `application/gate` (34), `application/doctor` (15) and `domain/project` (24) mix every toolchain in one package, so the toolchain axis is visible only in class-name prefixes and nothing stops one toolchain's code from reaching into another's. Applying the same layout and rule to every feature makes "where does Swift code live" one answer for the whole tree.

## What Changes

- Ship together with the init split: the branch carries the four `organize-init-by-toolchain` commits, cherry-picked onto current `main`, and this change builds on them.
- Generalize `ArchitectureToolchainTest` from init to every feature in all three layers:
  - toolchain slices (`jvm`, `gradle`, `maven`, `node` with `node/turbo`, `python`, `swift`) do not depend on each other, across features and layers; `gradle` and `maven` may depend on `jvm`;
  - in `application` and `infrastructure`, a feature's root reaches its own toolchain subpackages only from an allowlist of registries, factories and composers;
  - domain toolchain subpackages are shared vocabulary: any layer may import them, but they follow the independence rule.
- Move toolchain-specific classes into `<layer>/<feature>/<toolchain>/` in `domain/project`, `domain/init`, `domain/gate`, `application/doctor`, `application/gate`, `infrastructure/project`, `infrastructure/finding`, `infrastructure/doctor`, `infrastructure/gate` and `infrastructure/traceability`. Every toolchain-specific class moves, including the only one of its toolchain in a feature.
- Give `application/gate` `openapi` and `openspec` subpackages, as init has. They are features, not toolchains: the root may call them directly.
- Move tests with the classes they test, and update the fully qualified names in `ArchitectureBoundarySupport` for moved classes.
- Update AGENTS.md: the toolchain layout and the rule apply to every feature, not only init.

No user-visible behaviour changes: CLI output, generated files and `ward.toml` semantics stay the same.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None. This is an internal refactor; `skip_specs: true` is set in `.openspec.yaml`.

## Impact

- Code, domain layer: `domain/project` (12 toolchain types), `domain/init` (`GradleToolCatalog`, `ManagedPluginCatalog`, `TurboWorkspace*`, `OpenSpecDistribution`), `domain/gate` (`Simulators`).
- Code, application layer: `application/doctor` (one finding per toolchain plus the Android and dependency-check JVM helpers), `application/gate` (`MavenTestGate`, `SwiftGates`, `XcodeTestGate`, `OpenApi*`, `OpenSpec*`).
- Code, infrastructure layer: `infrastructure/project`, `infrastructure/finding`, `infrastructure/doctor`, `infrastructure/gate`, `infrastructure/traceability`.
- Composition: the Dagger modules in `config/` that import moved classes. No Dagger graph change.
- Visibility: about 50 package-private types in the touched packages; the ones that cross a subpackage line become `public` and need Javadoc.
- Tests: tests move with their classes; architecture tests gain the generalized rule and fixtures. The ArchUnit gate command in `ward.toml` already lists `ArchitectureToolchainTest`.
- CI: pull request mutation testing selects renamed domain and application files, so the PR mutates every moved class there.
- Not touched: `infrastructure/config` (split by `ward.toml` section), `domain/config` and `domain/spec` (OpenSpec, Xcode platform and OpenAPI options are their subject, not a toolchain), `infrastructure/agent` and `domain/spec`'s shell lexer (different axes; later changes). No `website/` or `skills/` change.

# Swift projects (SwiftPM and Xcode)

Use this reference when `ward detect` reports `SWIFT` with `SWIFTPM` or `XCODE`. Tuist and XcodeGen
projects are not supported yet: init stops and names the generator, so tell the user and stop.
Everything here is read-only until the user approves the plan.

## Supported setups

Ward sets up SwiftPM packages and Xcode projects that contain Swift sources, on macOS with Xcode
26.0 or newer. Ward's pinned SwiftLint runs from its own tools package, so no Swift project file is
edited.

## Blocking findings

Init reports these and writes nothing; say what to change before the user approves anything:

- **Not macOS.** Ward sets up Swift projects on macOS only.
- **Xcode older than 26.0, or unreadable.** Run `xcode-select -p` and select Xcode 26.0 or newer.
  Init reports the selected version as `INCOMPATIBLE`.
- **CocoaPods.** An Xcode project whose root holds a `Podfile` is refused. Suggest moving the
  dependencies to Swift Package Manager.

## Inspection

- **Project kind.** `Package.swift` at the root makes a SwiftPM package. An `.xcodeproj` makes an
  Xcode project. Tuist (`Project.swift`) and XcodeGen (`project.yml`) are refused.
- **Xcode schemes.** Init reads the shared schemes with `xcodebuild -list -json`. One scheme is used
  automatically; several need a choice.
- **Platform.** The Xcode test runs on `iOS Simulator`, unless every target builds for macOS only,
  then `macOS`.
- **Own configs.** A `.swiftlint.yml` that the project keeps is reused unchanged. A `.swift-format`
  that the project keeps is reused unchanged. Ward writes only the files that are absent.
- **Indentation.** Init reads the indentation of the Swift sources (tabs, 2 spaces or 4 spaces; 4
  when the project has no indented Swift) and writes it into a new `.swift-format`. Xcode templates
  use 4 spaces, and swift-format's default of 2 would fail every file.

## Choices to offer

| Choice | When to ask | Option |
|---|---|---|
| Preset | Always; Swift offers `standard` and `strict` | `--preset standard` or `--preset strict` |
| Xcode scheme | Only when the project has several shared schemes | `--scheme <name>` |
| Gates | Preset gates; the gate list follows the build tool | `--gate <id>[,<id>...]` |
| Agent integration | Ask whether to add one | `--integration <id>` |
| CI workflow | Ask whether to add one; it runs on `macos-26` | `--ci github` |
| Architecture rules | Ask only when `harmonize` is selected: layered, hexagonal or clean | `--architecture <style>`, `layered` under `--yes` |

Strict selects the standard gates with stricter commands: SwiftLint runs with `--strict` and the
opt-in rules, compiler warnings are errors with complete concurrency checking, and coverage needs
85%. Strict also selects `harmonize`; standard offers it as an opt-in choice. Swift strict has no mutation gate, because no Swift mutation tool is mature enough yet. Say so
when the user asks for mutation testing.

## Plan lines to expect

- `swift-format`, `swiftlint`, then for SwiftPM `swift-build` and `swift-test`, or for Xcode
  `xcodebuild-test` with its scheme and platform.
- `swift-coverage` with `minimum = 80`, or `minimum = 85` under `strict`.
- Under `strict`, the `swiftlint` command contains `--strict`, and the SwiftPM build, test and
  coverage commands (or the Xcode test command) carry the warnings-as-errors and strict-concurrency
  flags.
- Under `strict` over a project's own `.swiftlint.yml`: `.ward/swiftlint-strict.yml` and a
  `swiftlint` command with `--config .swiftlint.yml --config .ward/swiftlint-strict.yml`. Under
  `standard`, that file is removed.
- `.ward/swift-tools/Package.swift` pinning SwiftLintPlugins, resolved with `swift package resolve`.
  A failed resolve removes `.ward/swift-tools/` and changes nothing else.
- `.swiftlint.yml` and `.swift-format`, each written only when absent.
- `.gitignore` entries for `.ward/reports/` and `.ward/swift-tools/.build/`.
- With `harmonize`: `.ward/swift-architecture/Package.swift` pinning Harmonize 1.3.0 exactly, and
  `Tests/ArchitectureRules/LayerRules.swift` for the style. Rollback removes the package.
- With `harmonize`: `.harmonize.yaml` at the root when the project has none. A project's own file is
  kept, and the plan says to add `.ward` to its excludes. The `.gitignore` gains
  `.ward/swift-architecture/.build/`.

Ward never edits `Package.swift`, `project.pbxproj`, or any scheme.

## Before running the test gate

The first `swiftlint` run downloads the SwiftLint artifact bundle, so it needs the network. The first
`harmonize` run downloads Harmonize and swift-syntax and compiles them, which takes longer than the
rest. CI caches `.ward/swift-tools/.build` and `.ward/swift-architecture/.build`. Run `ward check --profile ci` once the plan is applied, and report
the first failure as it is printed.

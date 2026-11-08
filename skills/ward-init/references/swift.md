# Swift projects (SwiftPM and Xcode)

Use this reference when `ward detect` reports `SWIFT` with `SWIFTPM` or `XCODE`. Tuist and XcodeGen
projects are not supported yet: init stops and names the generator, so tell the user and stop.
Everything here is read-only until the user approves the plan.

## Supported setups

Ward sets up SwiftPM packages and Xcode projects that contain Swift sources, on macOS with Xcode
26.0 or newer. Ward's pinned SwiftLint runs from its own tools package, so no Swift project file is
edited. CI proves Xcode 26.0.1, 26.6, and 27.0 through 27.2. When the selected Xcode is outside the range a pinned tool
covers, init leaves out that tool's gate (`swiftlint`), and the plan names the Xcode
versions it supports; tell the user before they approve.

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
- **Own configs.** A `.swiftlint.yml` that the project keeps is reused unchanged. Ward writes only
  the files that are absent.
- **Formatting.** `ward-format` checks the Swift sources against `.editorconfig`, which init writes
  when the project has none, with the indentation of the sources (tabs, 2 spaces or 4 spaces; 4
  when the project has no indented Swift). Xcode templates use 4 spaces.

## Choices to offer

| Choice | When to ask | Option |
|---|---|---|
| Preset | Always; Swift offers `standard` and `strict` | `--preset standard` or `--preset strict` |
| Xcode scheme | Only when the project has several shared schemes | `--scheme <name>` |
| Gates | Preset gates; the gate list follows the build tool | `--gate <id>[,<id>...]` |
| Agent integration | Ask whether to add one | `--integration <id>` |
| CI workflow | Ask whether to add one; it runs on `macos-26` | `--ci github` |
| Architecture rules | Ask only under `strict`: layered, hexagonal or clean. Init writes the layer boundaries to `ward.toml` | `--architecture <style>`, `layered` by default |

Strict selects the standard gates with stricter commands: SwiftLint runs with `--strict` and the
opt-in rules, compiler warnings are errors with complete concurrency checking, and coverage needs
85%. Swift strict has no mutation gate, because no Swift mutation tool is mature enough yet. Say so
when the user asks for mutation testing.

## Plan lines to expect

- `ward-format`, `swiftlint`, then for SwiftPM `swift-build` and `swift-test`, or for Xcode
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
- `.swiftlint.yml`, written only when absent, and `.editorconfig` when the project has none.
- `.gitignore` entries for `.ward/reports/` and `.ward/swift-tools/.build/`.
- The layer boundaries of the style in `ward.toml`, as `[[checks.ward.layers]]`, checked by
  `ward:architecture/layers`. Init writes no Swift package or rules file for them.

Ward never edits `Package.swift`, `project.pbxproj`, or any scheme.

## Before running the test gate

The first `swiftlint` run downloads the SwiftLint artifact bundle, so it needs the network. CI caches
`.ward/swift-tools/.build`. Run `ward check --profile ci` once the plan is applied, and report the
first failure as it is printed.

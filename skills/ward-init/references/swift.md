# Swift projects (SwiftPM and Xcode)

Use this reference when `ward detect` reports `SWIFT` with `SWIFTPM` or `XCODE`. Tuist and XcodeGen
projects are not supported yet: init stops and names the generator, so tell the user and stop.
Everything here is read-only until the user approves the plan.

## Supported setups

Ward sets up SwiftPM packages and Xcode projects that contain Swift sources, on macOS with Xcode
26.0 or newer. No Swift project file is edited, and Ward installs no Swift tool: the gates run the
build tool that is already there. CI proves Xcode 26.0.1, 26.6, and 27.0 through 27.2.

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
- **Earlier SwiftLint files.** An earlier init may have left Ward-owned `.swiftlint.yml`,
  `.ward/swiftlint-strict.yml` or `.ward/swift-tools/Package.swift`. Re-running init removes them.
  A `.swiftlint.yml` the user wrote is kept unchanged and reported.
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

Strict selects the standard gates with stricter commands: compiler warnings are errors with complete
concurrency checking, coverage needs 85%, and the `ward` check applies the strict Ward rules. Swift
strict has no mutation gate, because no Swift mutation tool is mature enough yet. Say so when the
user asks for mutation testing.

## Plan lines to expect

- `ward-format`, then for SwiftPM `swift-build` and `swift-test`, or for Xcode `xcodebuild-test`
  with its scheme and platform. No SwiftLint gate is planned.
- `swift-coverage` with `minimum = 80`, or `minimum = 85` under `strict`.
- Under `strict`, the SwiftPM build, test and coverage commands (or the Xcode test command) carry
  the warnings-as-errors and strict-concurrency flags.
- `ward-rules.toml` with `[select] preset = "<preset>"` and `languages = ["java", "swift"]`. The
  `ward` check applies the Swift rules of the preset from this file.
- Removal lines for earlier SwiftLint files, when any exist (see Inspection).
- `.editorconfig` when the project has none.
- `.gitignore` entry for `.ward/reports/`.
- The layer boundaries of the style in `ward.toml`, as `[[checks.ward.layers]]`, checked by
  `ward:architecture/layers`. Init writes no Swift package or rules file for them.

Ward never edits `Package.swift`, `project.pbxproj`, or any scheme.

## Before running the test gate

Run `ward check --profile ci` once the plan is applied, and report the first failure as it is
printed. Swift builds need the selected Xcode's toolchain; the gates do not download anything.

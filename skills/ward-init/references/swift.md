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
| Preset | Always; Swift offers `standard` only | `--preset standard` |
| Xcode scheme | Only when the project has several shared schemes | `--scheme <name>` |
| Gates | Preset gates; the gate list follows the build tool | `--gate <id>[,<id>...]` |
| Agent integration | Ask whether to add one | `--integration <id>` |
| CI workflow | Ask whether to add one; it runs on `macos-26` | `--ci github` |

Strict is not available for Swift yet. If the user asks for it, say that `--preset strict` fails
without changing anything and that `standard` is the choice for now.

## Plan lines to expect

- `swift-format`, `swiftlint`, then for SwiftPM `swift-build` and `swift-test`, or for Xcode
  `xcodebuild-test` with its scheme and platform.
- `swift-coverage` with `minimum = 80`.
- `.ward/swift-tools/Package.swift` pinning SwiftLintPlugins, resolved with `swift package resolve`.
  A failed resolve removes `.ward/swift-tools/` and changes nothing else.
- `.swiftlint.yml` and `.swift-format`, each written only when absent.
- `.gitignore` entries for `.ward/reports/` and `.ward/swift-tools/.build/`.

Ward never edits `Package.swift`, `project.pbxproj`, or any scheme.

## Before running the test gate

The first `swiftlint` run downloads the SwiftLint artifact bundle, so it needs the network. CI
caches `.ward/swift-tools/.build`. Run `ward check --profile ci` once the plan is applied, and report
the first failure as it is printed.

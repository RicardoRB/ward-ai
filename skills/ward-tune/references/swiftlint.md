# Tuning SwiftLint

## Where the rules live

- **SwiftLint:** `.swiftlint.yml` at the project root. Init writes a Ward-marked one with the
  `excluded:` list (`.build`, `.ward`, `Pods`, `Carthage`, `DerivedData`) and SwiftLint's default
  rules; under `strict` it also carries the opt-in rules and `cyclomatic_complexity` at 10. A
  project that already has its own `.swiftlint.yml` keeps it, and Ward never rewrites that file.
- **Layered strict config:** when the project owns `.swiftlint.yml` and init runs with `strict`,
  the strict rules live in `.ward/swiftlint-strict.yml` instead. The `swiftlint` gate runs
  `--config .swiftlint.yml --config .ward/swiftlint-strict.yml --strict`, so the user's file is read
  first and the strict file adds its rules on top. Tune the user's rules in `.swiftlint.yml`, and the
  strict rules in `.ward/swiftlint-strict.yml`. Ward owns that second file, so changes to it are
  overwritten by the next init; propose any change to a strict rule as a change to the preset.
- **Formatting** is `ward-format`'s, not a Swift formatter's: `ward format` rewrites the Swift sources
  to the layout in `.editorconfig`. Tune the layout there, not in a Swift formatter config.
- **The SwiftLint version** is pinned in `.ward/swift-tools/Package.swift`, which Ward owns. Changing
  the pinned version is an upgrade, not a tuning change; propose it separately with evidence.

## Reading the current rules

- Read `.swiftlint.yml` and list `disabled_rules`, `opt_in_rules`, `only_rules`, `excluded`, and any
  per-rule `warning` or `error` levels.
- Read `.editorconfig` and note `indent_size` and `max_line_length` for Swift.
- Run `ward check --profile ci` and read the `swiftlint` and `ward-format` findings before proposing
  anything. Each proposal must cite the finding or the code it changes.

## Classifying a change

- **Tightening** (enabling an opt-in rule, lowering a rule's threshold, raising a warning to an
  error): propose with the findings it would add, and show the count from `ward check`.
- **Weakening** (disabling a rule, excluding a path, or raising a threshold): needs its own approval
  and a reason from the code. Never add a `// swiftlint:disable` comment to pass a gate; it is a
  forbidden suppression in Ward projects.
- **Excluding a path** in `.swiftlint.yml` is a weakening even when the path is generated code. Name
  the generated file and say how it is produced.

## Indentation for ward-format

- Init writes the Swift indentation into `.editorconfig` (`indent_size`) when the project has none.
  If the sources mix 2 and 4 spaces, the value may be wrong; `ward format --check` then lists most
  files. Show the findings and propose the value that matches the majority, then ask.

## Coverage

The Swift coverage gate (`swift-coverage`) has `minimum = 80`, or `minimum = 85` under `strict`. SwiftPM reads its coverage from
`swift test --show-codecov-path`; Xcode reads `.ward/reports/xcode/tests.xcresult` through
`xcrun xccov`. Lowering the minimum is a weakening and needs approval; see
[coverage.md](coverage.md#swift-coverage).

## Verify before reporting

- Run `ward check --profile ci` after each approved change and report the printed result, not a
  guess.
- The first SwiftLint run downloads the SwiftLint artifact bundle, so a network failure there is an
  environment problem, not a rule problem.

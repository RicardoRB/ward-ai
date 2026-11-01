# Harmonize architecture rules (Swift)

Use this reference when a Swift project's `harmonize` gate fails, or the user wants to change the
architecture rules.

## What Ward generates

- `.ward/swift-architecture/Package.swift`, marked `// managed-by: ward`. It pins Harmonize 1.3.0
  exactly and declares one test target, `ArchitectureRules`.
- `.ward/swift-architecture/Tests/ArchitectureRules/LayerRules.swift`, marked the same way. It has one
  Swift Testing test per source layer of the style. Each test calls a shared rule that forbids the
  layer's code from depending on the forbidden layers.
- `.harmonize.yaml` at the project root, marked `# managed-by: ward`, excluding `.ward`, `.build`,
  `Pods`, `Carthage` and `DerivedData`. Harmonize uses it to find the root.

The gate runs `swift test --package-path .ward/swift-architecture`. Harmonize never compiles the app,
so the project's `Package.swift` and `project.pbxproj` are not touched.

## Layers per style

| Style | Source folder | Must not depend on |
|---|---|---|
| `layered` | `controller` | `repository` |
| `hexagonal` | `domain`, `application` | `adapter`, `infrastructure` |
| `clean` | `domain`, `core` | `controller`, `adapter`, `infrastructure` |

Folders match at any depth, ignoring case. A layer folder that does not exist matches nothing, so its
rule passes. "Depend" means importing a module named after a forbidden layer, or naming a type declared
under a forbidden folder in an inheritance clause, property type, parameter or return type. This is a
syntactic check, so a type that shares a name with one in a forbidden folder can be a false positive.

## Tuning

- The rules file has no baselines. An existing violation fails the gate until the code is fixed.
- Fix the dependency: move the type to the layer it belongs to, or pass it in through the source layer
  instead of naming the forbidden type. Do not delete a layer folder or a rule to make the gate pass.
- Never disable a rule, and never add a `.harmonize.yaml` exclude that hides a source layer folder.
  The excludes only keep Ward's own package and build output out of the scan.
- If the user asks for a known exception, explain that Harmonize supports a `baseline:` argument, that
  Ward's generated file does not write one yet, and that editing the file means `ward init` will
  regenerate it and drop the change. Ask before editing `LayerRules.swift`.
- To change the style, run `ward init --architecture <style>` again. It rewrites the rules file.
- A project's own `.harmonize.yaml` is kept unchanged. Its excludes must include `.ward`; init says
  so in the plan when it keeps the file.

## Running it

The first run compiles Harmonize and swift-syntax, which takes about half a minute. Run the gate from
the project root with `ward check --profile ci`, which runs every gate.

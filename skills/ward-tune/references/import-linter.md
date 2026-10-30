# import-linter contracts (Python)

Use this reference when a Python project's `import-linter` gate fails, or the user wants to change
the architecture rules.

## What Ward generates

`.importlinter` in the project root, marked `# managed-by: ward`:

- `[importlinter] root_package`: the project's root package.
- A `forbidden` contract named after the style. Each layer is listed twice: as `pkg.<layer>` when that
  package sits directly under the root, and as `pkg.**.<layer>` at any depth. import-linter's `**`
  matches one or more package levels, so both forms are needed. The exact form is listed only when
  the package exists, because import-linter rejects a module it cannot find. A top-level layer
  created after init is therefore not enforced until init runs again.
- Under strict, an `independence` contract between the root package's feature packages, when there
  are at least two. It is computed at init, so a new feature package needs init to run again.

## Layers per style

| Style | Sources may not import |
|---|---|
| `layered` | `controller` -> `repository` |
| `hexagonal` | `domain`, `application` -> `adapter`, `infrastructure` |
| `clean` | `domain`, `core` -> `controller`, `adapter`, `infrastructure` |

## Tuning

- A layer that does not exist when init runs matches nothing and passes. After creating a top-level
  layer package, run `ward init` again so the contract names it.
- To change a contract, edit `.importlinter` and keep the rule the user asked for. Do not delete a
  contract to make the gate pass.
- A project's own `.importlinter`, `[tool.importlinter]` or `setup.cfg` section is never overwritten.

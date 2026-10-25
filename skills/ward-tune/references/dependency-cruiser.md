# Tuning dependency-cruiser rules

## Where the rules live

- Init writes `.dependency-cruiser.mjs` for the architecture style you chose, with the marker
  `// managed-by: ward` on its first line. Init rewrites that file when the style or preset
  changes, so an edit to it is lost on a re-run.
- A dependency-cruiser config the project wrote is kept as it is, and `ward.toml` points the gate at
  it with `--config`. Removing the `// managed-by: ward` line from Ward's file makes it the
  project's own: init then keeps it. Say so before recommending it, because the change moves the
  file out of Ward's control.
- The gate runs `depcruise . --config <file> --output-type err`. Any error-severity rule in
  `forbidden` fails it.

## Layer paths

A layer is a directory name anywhere in a module's path. Each `forbidden` rule has a `from.path`
(the importing modules) and a `to.path` (the imported modules), both regular expressions:

```js
{
  name: "hexagonal-architecture",
  severity: "error",
  from: { path: "(^|/)(domain|application)/" },
  to: { path: "(^|/)(adapter|infrastructure)/" },
}
```

- **Narrowing** a path, such as dropping `application` from `from.path`, or excluding a folder,
  lets more imports through. That is a weakening: it needs the user's explicit approval and a
  stated reason, as in SKILL.md Step 5.
- **Widening** a path to catch more imports tightens the gate. Show the diff and let the user
  approve it, then run the gate to confirm the existing code still passes. If it fails, decide
  with the user whether to fix the imports or narrow the path, and do not narrow it on your own.

## Adding a layer

Add one rule to `forbidden` with its own `name`, a `comment` that says what it forbids, and
`severity: "error"`. Keep the path style of the existing rules. A folder that does not exist matches
nothing, so a new layer never fails the gate by itself.

## Circular dependencies

`no-circular` comes from `strict`. The `standard` config does not forbid cycles, and `strict` adds
the rule to Ward's config on the next init. Removing `no-circular` from a `strict` config is a
weakening and needs approval. Adding it to a `standard` config is a tightening.

## Exclusions

`options.doNotFollow` and `options.exclude` keep `node_modules`, build output and reports out of
the scan. Adding an entry to `exclude` hides modules from every rule, so it is a weakening. Never
use these options to hide a specific violation, and do not add dependency-cruiser ignore comments.

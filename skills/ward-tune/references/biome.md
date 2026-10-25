# Biome rules

The `biome` gate runs `biome ci --colors=off --reporter=checkstyle .` (with
`--error-on-warnings` under strict). It checks lint and format together, and Ward reads its
findings from the checkstyle report. Its rules live in one file:

- Ward's own `biome.jsonc`, when the project had no Biome config at init. Its first line is
  `// managed-by: ward preset=<id>`.
- The project's own `biome.json`, `biome.jsonc`, `.biome.json` or `.biome.jsonc`, which init keeps
  unchanged. Ward's `vcs` and `files` settings in that file are the user's; tune rules only.

## Evidence

- Run `ward check --profile ci --format json` and read the `biome` check. Its `findings` give
  `rule` (for example `lint/suspicious/noExplicitAny`, or `format`), `file`, and `line`.
- Group the findings by `rule` and count them per file. Use `findingsTotal` when `findings` is
  truncated.
- Check the Biome version with `npx biome --version` (or the package manager's runner). The gate
  supports `>=2.3.0 <3.0.0`.

## Rule groups

Rules sit under `linter.rules.<group>.<rule>`, for example
`"suspicious": { "noExplicitAny": "error" }`. `recommended: true` enables the default set. Ward's
strict preset sets eight rules to `error`: `noExplicitAny`, `noNonNullAssertion`, `useConst`,
`noUnusedImports`, `noUnusedVariables`, `noExcessiveCognitiveComplexity`, `noFloatingPromises` and
`noMisusedPromises`.

## Classes of change

- **Strengthen** (accepts less code): set a rule to `"error"`, for example a rule the team already
  follows everywhere. Check the count first. Under strict, `warn` still fails the gate, so a rule
  that is `warn` in a strict project is already enforced.
- **Neutral**: reorder or group entries without changing any severity.
- **Weaken** (accepts more code): set a rule to `"warn"` in a standard project, or to `"off"` in
  any project; remove a rule; or narrow `files.includes`. Each needs its own approval and a reason.
  Under strict, `warn` is not a relaxation, because `--error-on-warnings` still fails the gate: only
  `"off"` relaxes a strict rule.

## Rules for edits

- Fix the code first. Most Biome findings are one-line fixes (an import to remove, a `let` that
  should be `const`, a `format` difference that `biome check --write` applies).
- **Never add suppressions.** No `// biome-ignore` comments, no `overrides` that turn a rule off for
  a path to clear findings, and no `files.ignore` entries added only to hide code. These are
  suppressions under the guardrails.
- Keep the `vcs` setting in Ward's `biome.jsonc`. It makes Biome skip Git-ignored output such as
  `.ward/reports/` and coverage. Removing it makes Biome check those files.
- Do not reformat `biome.jsonc` by hand. Run `biome format --write biome.jsonc` after an approved
  edit, so Biome reports no `format` finding in the config itself.
- If Ward owns `biome.jsonc`, the next `ward init` rewrites it from the preset and undoes the
  tuning. Ask first whether the file should become the user's: removing the marker line is part of
  the same approved edit. A `biome.json` the user owns is never rewritten by init.
- A tuned rule that the `AGENTS.md` Biome line describes (for example a strict rule set) may need
  that line updated in the same approval. `ward init --agent-rules` rewrites the section from the
  preset.

## Verify

Run the gate on its own, then the full profile:

```sh
ward check --profile ci --format json
```

Read the `biome` check. Report its status and findings count, and compare it with the evidence.

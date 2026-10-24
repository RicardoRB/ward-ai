# Tuning ESLint

## Where the rules live

- Init writes `eslint.config.mjs`, or `eslint.config.js` when `package.json` has
  `"type": "module"`, only when the project has no ESLint config. Its first line is
  `// managed-by: ward preset=standard`, and it enables `@eslint/js` recommended and
  `typescript-eslint` recommended rules, ignoring `dist/`, `coverage/`, and `.ward/`.
- An existing `eslint.config.*` or `.eslintrc*` is the user's. Init reuses it unchanged.
- The `eslint` gate runs `<runner> eslint . --format json`, so Ward reads one structured finding
  per message, with the rule as `eslint:<rule-id>`.

Treat a marked config like any managed rule file (Step 4): ask whether it should become the user's
before the first edit, and remove the marker in the same approved edit.

## Gathering evidence

Run the gate with `ward check --format json` after trust is confirmed, and count findings per rule.
For a rule you plan to enable, measure it first without editing the config:

```sh
npx eslint . --format json --rule '{"@typescript-eslint/no-floating-promises": "error"}'
```

Rules from `typescript-eslint`'s type-checked sets need type information
(`parserOptions.projectService`), which makes the gate slower. Say so before proposing them.

## Classifying a change

| Change | Class |
|---|---|
| Add a rule, or move from `recommended` to `strict` or a type-checked set | strengthen |
| Raise a rule from `warn` to `error` | strengthen (the gate fails only on errors) |
| Turn a rule `off`, or lower it from `error` to `warn` | weaken |
| Add `ignores` patterns beyond generated output | weaken |
| Add `eslint-disable` comments | never: this is a suppression |

## Grounding proposals in the code

- **A rule has no findings.** Enabling it locks in the current state at no cost; propose it as a
  strengthening.
- **A rule has many findings.** Offer to fix them first, file by file. Disabling the rule is a
  separate weakening with a stated reason.
- **Generated code is linted,** such as `dist/` or a codegen directory. Ignoring the directory is a
  weakening only when it holds hand-written code; name the directory and its origin.

# Tuning ESLint

## Where the rules live

- Init writes `eslint.config.mjs`, or `eslint.config.js` when `package.json` has
  `"type": "module"`, only when the project has no ESLint config. Its first line is
  `// managed-by: ward preset=<id>`, and it ignores `dist/`, `coverage/`, `.next/`, `out/`,
  `build/`, and `.ward/`.
  - For TypeScript it enables `@eslint/js` recommended and `typescript-eslint` recommended rules.
    With `strict` it enables `tseslint.configs.strictTypeChecked` instead, sets
    `parserOptions.projectService: true` with `tsconfigRootDir: import.meta.dirname`, and turns
    type-checked rules off for `**/*.{js,mjs,cjs}` with `tseslint.configs.disableTypeChecked`.
  - For JavaScript it enables `@eslint/js` recommended rules, spreads `globals.browser` and
    `globals.node` into `languageOptions.globals`, parses JSX in `**/*.jsx`, and, without
    `"type": "module"`, sets `sourceType: "commonjs"` for `**/*.js`. With the `jest` gate it
    adds `globals.jest` for `**/__tests__/**` and `**/*.{test,spec}.{js,jsx,mjs,cjs}`. When the
    project's Jest `testMatch` uses other locations, widen that `files` list to match; that is
    neutral, not a weakening.
- An existing `eslint.config.*` or `.eslintrc*` is the user's. Under `standard`, init reuses it
  unchanged and warns that the preset's ESLint rules are not applied to it. Under `strict`, a flat
  config in a TypeScript project is layered: init moves it to `eslint.base.<ext>` unchanged, and
  Ward's root config imports that base and appends the strict rules, so Ward's rules win. Tune the
  strict rules in the root config, and the base's rules in the base file. A `.eslintrc*` cannot be
  imported by a flat config, so it is reused with the same warning.
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

## Moving to or from `strict-type-checked`

- **To it** (a strengthening). In a user-owned config, add `tseslint.configs.strictTypeChecked`,
  the `projectService` block, and the `disableTypeChecked` block for JavaScript files, as in
  Ward's strict config. Measure first: count the findings per rule against a copy of the config.
- **Files outside `tsconfig.json`.** The project service reports a TypeScript file that
  `tsconfig.json` does not include, such as tests or scripts outside `include`, as a parsing error
  ("was not found by the project service"). Propose adding it to `include` (or a
  `tsconfig.eslint.json` that extends the main one), or listing a few root files in
  `projectService: { allowDefaultProject: [...] }`. Adding the file to `ignores` is a weakening.
- **From it** back to `recommended` or `recommendedTypeChecked`: a weakening that needs its own
  reason, such as lint time on a large codebase; show the rules it removes and their counts.

## JavaScript globals and module type

- **Narrowing `globals`.** Init declares browser and Node globals because it cannot tell where the
  code runs. Both sets together let a mistyped browser global pass `no-undef` in Node code. Find
  where the code runs: `require`, `process`, or `node:` imports point to Node; `window`, `document`,
  or a bundler entry under `public/` points to the browser. Propose keeping only that set, or a
  `files` block per directory when both run (for example `globals.node` for `server/**` and
  `globals.browser` for `src/**`). Narrowing is a strengthening; check it first with
  `npx eslint . --format json` against a copy of the config.
- **`sourceType`.** The CommonJS block exists because ESLint parses `.js` as ES modules and does not
  read `"type"` from `package.json`. When the project moves to `"type": "module"`, remove the block
  in the same edit; leaving it makes every `import` a parsing error. When only some files are
  CommonJS, rename them to `.cjs`, which ESLint parses as CommonJS on its own.

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

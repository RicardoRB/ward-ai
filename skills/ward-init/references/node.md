# Node projects (TypeScript and JavaScript)

Use this reference when `ward detect` reports `TYPESCRIPT` or `JAVASCRIPT` with `NPM`, `PNPM`,
`YARN`, or `BUN`.
Everything in it is read-only. Skip the Java-only steps of the inspection (package layout,
ArchUnit, rule files under `config/`).

## Inspection

- **Language.** `ward detect` reports `TYPESCRIPT` when `tsconfig.json` sits next to `package.json`
  (or a workspace member has one), and `JAVASCRIPT` for any other `package.json`.
- **Framework.** A TypeScript project's framework comes only from its declared packages, in
  `dependencies` or `devDependencies`: `vue` or `nuxt` (Vue), `svelte` or `@sveltejs/kit` (Svelte),
  `astro` (Astro), `@angular/core` (Angular). File names never decide. When several match, the first
  in the order Astro, Svelte, Vue, Angular wins. Vue, Svelte and Astro replace `tsc` with `vue-tsc`,
  `svelte-check` or `astro-check`. Angular uses `ngc -p tsconfig.app.json --noEmit`, which checks
  templates, and the `ng-test` gate (`ng test --watch=false --coverage`) unless `jest` is declared.
  Ward writes no Angular test config. Coverage minimums apply only when `angular.json` sets
  `coverageThresholds`, and the plan says so otherwise. `biome` is refused for Angular, and
  `stryker` is not offered. Biome and framework lint rules are out of scope. A framework project with no
  type-check inputs fails the gate and names the file (`TS18002` or `TS18003`). Stryker mutates only
  `src` for Vue, Svelte and Astro, and the ESLint config ignores `.astro/` or `.svelte-kit/`.
- **JavaScript sources.** A `JAVASCRIPT` package is a project init configures only when it holds
  `.js`, `.jsx`, `.mjs`, or `.cjs` files of its own. List them without descending into
  `node_modules`, `dist`, `build`, `out`, `target`, `coverage`, hidden directories, directories
  with another build file (`pom.xml`, `build.gradle*`, `settings.gradle*`, `pyproject.toml`,
  `Cargo.toml`, `go.mod`), or nested packages that are not workspace members. Root tool configs
  such as `vite.config.js` or `commitlint.config.js` do not count. With no sources, treat the
  directory as a monorepo root and look for projects below it (see
  [inspection.md](inspection.md)); `ward init` there reports that no supported project was
  detected. A root with a stray script such as `scripts/release.js` counts as a project, so ask
  whether the user meant the projects below it and pass `--project` for those.
- **Module type.** Note whether `package.json` has `"type": "module"`. It decides the ESLint
  config file name and, for JavaScript, whether `.js` files are parsed as CommonJS.
- **Package manager and Node.** `ward detect` prints the manager and the Node major version, read
  from `.nvmrc`, `.node-version`, or `engines.node`, else 24. Read `packageManager` in
  `package.json`: a `yarn@1.x` value means Yarn 1.
- **Declared tools.** Read `dependencies` and `devDependencies` in `package.json`:
  - `vitest` or `jest` decides the test gate (`vitest`, then `jest`). A Bun project with neither
    gets `bun-test`; any other project gets `vitest`, and init adds `vitest` and
    `@vitest/coverage-v8`.
  - `typescript` (TypeScript only): note the declared range. Its lower bound is checked against
    `typescript-eslint`.
  - `eslint`, `@eslint/js`, `globals`, `prettier`, `typescript-eslint`: declared packages keep
    their version. A declared `eslint` outside `>=9.22.0 <11.0.0` is an `INCOMPATIBLE` finding
    (see Compatibility checks); tested versions are 9.22.0 and 10.13.0.
  - `@biomejs/biome`: a project that declares it, or that has a user-owned Biome config, uses Biome
    for linting and formatting (see Biome below). Note its declared range; init checks it against
    `>=2.3.0 <3.0.0`.
- **Configs.** List `eslint.config.*`, `.eslintrc*`, `.prettierrc*`, `prettier.config.*`,
  `vitest.config.*`, `vite.config.*`, `jest.config.*` (and a `"jest"` key in `package.json`),
  `stryker.conf.*`, `stryker.config.*`, `.stryker.conf.*`, `biome.json`, `biome.jsonc`,
  `.biome.json`, `.biome.jsonc`, and `bunfig.toml` in the project root. A file whose first line is
  not `// managed-by: ward` or `# managed-by: ward` is the user's, and init keeps it.
- **Lock file.** `package-lock.json`, `pnpm-lock.yaml`, `yarn.lock`, `bun.lock`, or `bun.lockb`.
  Init's install updates it, and the generated CI workflow's frozen install needs it committed.

Add a line like this to the inspection summary:

```text
Node: TypeScript, pnpm, Node 22, Vitest declared, typescript ^5.9.3, no ESLint config
Node: JavaScript (CommonJS), npm, Node 24, sources in src/, no test runner, no ESLint config
```

## Choices

- **Preset.** `standard`, `strict`, or a custom selection.
  - `standard`: for TypeScript it selects `tsc`, the test gate, `prettier`, and `eslint`; for
    JavaScript the same without `tsc`, which is not offered. The test gate fails below 70% line
    coverage.
  - `strict`: the same gates plus `stryker` in `ci` and `dependency-cruiser` in `pre-push` and `ci`.
    Tests fail below 85% line and 75% branch
    coverage (Bun: lines only), Stryker fails below a 60% mutation score, and a generated
    TypeScript ESLint config uses `strict-type-checked`, which needs type information from
    `tsconfig.json`.
  - Recommend `strict` when the project already has tests that cover most code, runs Node 22 or
    later (Stryker 10 needs it), and its `tsconfig.json` includes the files ESLint lints, tests
    too. Under `strict`, a user-owned flat ESLint config in a TypeScript project is layered, so
    Ward's `strict-type-checked` rules apply over it; under `standard`, a user-owned config stays as
    it is, and the plan warns that the preset's rules are not applied to it. Offer `ward-tune` for
    that.
- **Biome.** A Biome project (see Declared tools and Configs) gets one `biome` gate in place of
  `prettier` and `eslint`, in the same profiles and presets. The gate runs
  `biome ci --colors=off --reporter=checkstyle .`, and under `strict` it adds `--error-on-warnings`,
  so warnings fail it. Init asks no question about Biome. A project's own ESLint config
  (`eslint.config.*` or `.eslintrc*`) still gets the `eslint` gate, which runs that config
  unchanged. Recommend the Biome gate as-is; do not offer to migrate ESLint or Prettier to Biome.
- **Gates.** Init also offers `stryker` (mutation testing, `ci` only; slow on Bun, which has no
  Stryker runner plugin), `semgrep` (ruleset `p/typescript`, or `p/javascript` for JavaScript;
  needs network access), `gitleaks`, `trivy`, and `zap`. `dependency-cruiser` checks import
  boundaries for the architecture style, and selecting it asks for the style (Layered, Hexagonal,
  or Clean, with `layered` as the default; `--architecture` answers it). `standard` offers it
  without selecting it.
- **Probe.** For tool availability, probe only the gates init offers here:

  ```sh
  ward init -C <dir> --dry-run --preset strict --integration none --gate semgrep,gitleaks,trivy
  ```

## Reading the plan

- `modify package.json` with `add devDependency <name> <version>` lines: the exact versions init
  pins. Packages the project already declares are not listed.
- `run <manager> install, which updates <lock file>`: applying runs the package manager's install,
  which downloads packages from the registry and changes the lock file. **This is an install.**
  Ask for a specific yes to it before applying, separate from approving the plan. If the user says
  no, do not apply; offer a custom selection without the gates that need missing packages.
- `needs Node <version> or later`: the range needs a later minor than detection can see. Ask
  which Node version the project and CI run.
- `Ward supports Node 22 and 24` warning: the declared Node version is below 22 (not Bun). It does
  not block init. Tell the user that Ward may not work with that Node version, and ask whether the
  project and CI can run Node 22 or 24.
- `conflict package.json` with `INCOMPATIBLE <package>` lines: the project's Node or TypeScript
  version is outside the package's supported range. Init applies nothing, and no option overrides
  it. Explain the range and the choices (upgrade Node, pin another TypeScript version, or drop
  that gate). Do not apply.
- `create eslint.config.mjs` (or `.js` with `"type": "module"`): Ward's flat config. For
  TypeScript it enables typescript-eslint's recommended rules. For JavaScript it enables
  `@eslint/js` recommended rules, declares browser and Node globals, parses JSX in `.jsx` files,
  and, without `"type": "module"`, parses `.js` files as CommonJS. `keep .eslintrc.json` means the
  user's config is reused, with a warning that the preset's rules are not applied to it. Under
  `strict`, a flat config in a TypeScript project is layered instead: `move eslint.config.*` to
  `eslint.base.*`, and Ward's root imports it and adds the strict rules. An existing
  `eslint.base.*` that Ward did not write stops apply with a conflict.
- `create vitest.config.mjs` or `create jest.config.mjs`: Ward's test config holding the
  preset's coverage thresholds; the test command then carries no threshold flag. `keep
  vite.config.ts` (or another user test config) means the thresholds are passed as flags on the
  command instead. A Jest config in `package.json` produces no line.
- `add devDependency @biomejs/biome 2.5.15` (a Biome project that does not declare it): the pinned
  Biome. A declared Biome is kept unless it is outside `>=2.3.0 <3.0.0`, which gives an
  `INCOMPATIBLE @biomejs/biome` finding and blocks apply; explain the range and the choices (pin a
  Biome in range, or use a custom selection without `biome`). Tested Biome versions are 2.3.0 and
  2.6.3.
- `create biome.jsonc`: Ward's Biome config, marked as Ward's, with Biome's recommended rules and
  Git-ignore support, so Biome skips `.ward/reports/` and coverage output. `strict` also sets eight
  rules to `error`, including the type-aware `noFloatingPromises` and `noMisusedPromises`. Under
  `strict`, a `biome.json` or `biome.jsonc` is moved to `biome.base.*`, and Ward's `biome.jsonc`
  extends it. `keep biome.json` under `standard` means the user's config is reused unchanged, with
  a warning that the preset's Biome rules are not applied to it.
- A Biome project gets no `eslint.config.*` and no Prettier config from init, and the plan does
  not list `prettier`. The project's own `.prettierrc*`, if any, stays in place without a gate.
- Edge case: Ward's `biome.jsonc` reads the ignore file (`vcs.useIgnoreFile`). In a directory
  that is not a Git repository and has no `.gitignore`, Biome reports a configuration error and
  the `biome` gate fails. Add a `.gitignore` first, or set up the repository.
- `create stryker.config.mjs`: the test runner (Bun: the command runner with `bun test`), the
  `json` reporter, and with `strict` `thresholds.break: 60`. `keep stryker.conf.json` means the
  user's own Stryker thresholds apply.
- `stryker` adds `@stryker-mutator/core` and, for Vitest or Jest, its runner plugin. An
  `INCOMPATIBLE @stryker-mutator/core` finding means Node is older than 22.
- `create .dependency-cruiser.mjs`: Ward's config that forbids the style's cross-layer imports, plus
  circular dependencies with `strict`. `keep .dependency-cruiser.cjs` (or another dependency-cruiser
  config) means init writes nothing and the gate reads that file. `dependency-cruiser` adds
  `dependency-cruiser` 18.6.0 and, in TypeScript, `typescript` when it is missing.
- For JavaScript, the `eslint` gate adds `eslint`, `@eslint/js`, and `globals`, and no
  `typescript` or `typescript-eslint`. A plan that adds either for a JavaScript project is wrong:
  stop and report it.
- `modify .gitignore`: a Ward section with `.ward/reports/` and `coverage/`, plus `.stryker-tmp/`
  and `reports/mutation/` with `stryker`.
- If the install fails on apply, init restores `package.json` and the lock file and writes no
  `ward.toml`. Show the error and the command; do not retry without asking.

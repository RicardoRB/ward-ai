# Node projects (TypeScript and JavaScript)

Use this reference when `ward detect` reports `TYPESCRIPT` or `JAVASCRIPT` with `NPM`, `PNPM`,
`YARN`, or `BUN`.
Everything in it is read-only. Skip the Java-only steps of the inspection (package layout,
rule files under `config/`).

## Inspection

- **Language.** `ward detect` reports `TYPESCRIPT` when `tsconfig.json` sits next to `package.json`
  (or a workspace member has one), and `JAVASCRIPT` for any other `package.json`.
- **Framework.** A TypeScript project's framework comes only from its declared packages, in
  `dependencies` or `devDependencies`: `vue` or `nuxt` (Vue), `svelte` or `@sveltejs/kit` (Svelte),
  `astro` (Astro), `@angular/core` (Angular), `next` (Next.js), `@nestjs/core` (NestJS), `fastify`
  (Fastify), `express` (Express). File names never decide. When several match, the first in the order
  Astro, Svelte, Vue, Angular, Next.js, NestJS, Fastify, Express wins. Vue, Svelte and Astro replace `tsc` with `vue-tsc`,
  `svelte-check` or `astro-check`. Angular uses `ngc -p tsconfig.app.json --noEmit`, which checks
  templates, and the `ng-test` gate (`ng test --watch=false --coverage`) unless `jest` is declared.
  Ward writes no Angular test config. Coverage minimums apply only when `angular.json` sets
  `coverageThresholds`, and the plan says so otherwise. Ward supports Angular 21 and 22. For an
  unsupported major, a TypeScript version outside the major's range, or a Node version outside the
  major's range, init reports `INCOMPATIBLE` before it changes any file; an unsupported major reports
  only that finding. `stryker` is not offered for Angular. A framework project with no
  type-check inputs fails the gate and names the file (`TS18002` or `TS18003`). Stryker mutates only
  `src` for Vue, Svelte and Astro, and the generated config ignores `.astro/` or `.svelte-kit/`.
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
- **Package manager and Node.** `ward detect` prints the manager and the Node major version, read
  from `.nvmrc`, `.node-version`, or `engines.node`, else 24. Read `packageManager` in
  `package.json`: a `yarn@1.x` value means Yarn 1.
- **Declared tools.** Read `dependencies` and `devDependencies` in `package.json`:
  - `vitest` or `jest` decides the test gate (`vitest`, then `jest`). A Bun project with neither
    gets `bun-test`; any other project gets `vitest`, and init adds `vitest` and
    `@vitest/coverage-v8`. Without a declared Vitest, the version is 4.1.11 when `vite` is declared
    in `^6`, `^7`, or `^8`, and 4.0.18 otherwise. A declared Vitest whose Vite range excludes the
    project's `vite` is `INCOMPATIBLE`. Vitest 4.1.11 needs npm 11.6 or later, because npm 10
    (bundled with Node 22) crashes on it (npm/cli#9787).
  - `typescript` (TypeScript only): note the declared range. Init keeps it.
  - `@sveltejs/kit` with the `svelte-check` gate: `svelte-check` reads `.svelte-kit/tsconfig.json`,
    which only `svelte-kit sync` writes. When the `check` script in `package.json` runs
    `svelte-kit sync` (the SvelteKit template's `svelte-kit sync && svelte-check ...`), the gate
    runs that script with the package manager (`npm run check`, `pnpm run check`, `yarn run check`
    or `bun run check`), so the sync happens first. Otherwise the gate runs `svelte-check` itself,
    and the plan notes the line to add, `"prepare": "svelte-kit sync || echo ''"`. The note does
    not block init, and init does not edit `scripts`.
    npm does not run the project's own `prepare` script on `npm install`, so with the note's
    `prepare` alone, run `svelte-kit sync` before `ward check`.
- **Configs.** List `vitest.config.*`, `vite.config.*`, `jest.config.*` (and a `"jest"` key in
  `package.json`), `stryker.conf.*`, `stryker.config.*`, `.stryker.conf.*`, `bunfig.toml`, and
  `ward-rules.toml` in the project root. A file whose first line is not `// managed-by: ward` or
  `# managed-by: ward` is the user's, and init keeps it. A project's own `eslint.config.*`,
  `.eslintrc*`, `biome.json(c)`, or `.biome.json(c)` is not read by init, and init leaves the
  files unchanged.
- **Lock file.** `package-lock.json`, `pnpm-lock.yaml`, `yarn.lock`, `bun.lock`, or `bun.lockb`.
  Init's install updates it, and the generated CI workflow's frozen install needs it committed.

Add a line like this to the inspection summary:

```text
Node: TypeScript, pnpm, Node 22, Vitest declared, typescript ^5.9.3
Node: JavaScript (CommonJS), npm, Node 24, sources in src/, no test runner
```

## Choices

- **Preset.** `standard`, `strict`, or a custom selection.
  - `standard`: for TypeScript it selects `tsc` (or the framework's checker), the test gate; for
    JavaScript the same without `tsc`, which is not offered. The test gate fails below 70% line
    coverage.
  - `strict`: the same gates plus `stryker` in `ci`. Tests fail below 85% line and 75% branch
    coverage (Bun: lines only), and Stryker fails below a 60% mutation score.
  - Recommend `strict` when the project already has tests that cover most code, and runs Node 22 or
    later (Stryker 10 needs it). Offer `ward-tune` for rules that do not fit the codebase.
- **Lint and style.** Ward's TypeScript and JavaScript rules do the linting. Init writes
  `ward-rules.toml` with the preset and the project's languages (`"typescript"`, `"javascript"`, or
  both), so the `ward` gate applies them. Init writes no lint config, and formatting stays with
  `ward-format`.
- **Gates.** Init also offers `stryker` (mutation testing, `ci` only; slow on Bun, which has no
  Stryker runner plugin), `secrets` (Ward's native check, nothing to install), `trivy`, and `zap`. Semgrep's JavaScript and TypeScript packs
  are not offered: Ward's rules cover those languages. The layer boundaries of the architecture
  style are written to `ward.toml` as `[[checks.ward.layers]]` for every Node project, and
  `strict` adds the slice cycle check (`[checks.ward.cycles]`).
- **Probe.** For tool availability, probe only the gates init offers here:

  ```sh
  ward init -C <dir> --dry-run --preset strict --integration none --gate secrets,trivy
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
- `create ward-rules.toml`: the preset and the Node languages the `ward` gate applies. A
  `ward-rules.toml` that Ward did not write is kept as it is, with a warning.
- `create vitest.config.mjs` or `create jest.config.mjs`: Ward's test config holding the
  preset's coverage thresholds; the test command then carries no threshold flag. `keep
  vite.config.ts` (or another user test config) means the thresholds are passed as flags on the
  command instead. A Jest config in `package.json` produces no line.
- `create stryker.config.mjs`: the test runner (Bun: the command runner with `bun test`), the
  `json` reporter, and with `strict` `thresholds.break: 60`. `keep stryker.conf.json` means the
  user's own Stryker thresholds apply.
- `stryker` adds `@stryker-mutator/core` and, for Vitest or Jest, its runner plugin. An
  `INCOMPATIBLE @stryker-mutator/core` finding means Node is older than 22.
- Init writes no dependency-cruiser config and adds no `dependency-cruiser` package. In TypeScript,
  `typescript` is added only when it is missing, as the other gates require.
- `modify .gitignore`: a Ward section with `.ward/reports/`, plus `coverage/` when a test gate
  (`vitest`, `jest` or `bun-test`) is configured, and `.stryker-tmp/` and `reports/mutation/` with
  `stryker`. Only the gates init configures add their entries.
- If the install fails on apply, init restores `package.json` and the lock file and writes no
  `ward.toml`. Show the error and the command; do not retry without asking.

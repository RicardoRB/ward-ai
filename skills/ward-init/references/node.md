# Node projects (TypeScript and JavaScript)

Use this reference when `ward detect` reports `TYPESCRIPT` or `JAVASCRIPT` with `NPM`, `PNPM`,
`YARN`, or `BUN`.
Everything in it is read-only. Skip the Java-only steps of the inspection (package layout,
ArchUnit, rule files under `config/`).

## Inspection

- **Language.** `ward detect` reports `TYPESCRIPT` when `tsconfig.json` sits next to `package.json`
  (or a workspace member has one), and `JAVASCRIPT` for any other `package.json`.
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
    their version.
- **Configs.** List `eslint.config.*`, `.eslintrc*`, `.prettierrc*`, `prettier.config.*`, and
  `bunfig.toml` in the project root. A file whose first line is not `// managed-by: ward` or
  `# managed-by: ward` is the user's, and init keeps it.
- **Lock file.** `package-lock.json`, `pnpm-lock.yaml`, `yarn.lock`, `bun.lock`, or `bun.lockb`.
  Init's install updates it, and the generated CI workflow's frozen install needs it committed.

Add a line like this to the inspection summary:

```text
Node: TypeScript, pnpm, Node 22, Vitest declared, typescript ^5.9.3, no ESLint config
Node: JavaScript (CommonJS), npm, Node 24, sources in src/, no test runner, no ESLint config
```

## Choices

- **Preset.** Only `standard` or a custom selection. `strict` is not available for TypeScript or
  JavaScript yet, and `--preset strict` fails. For TypeScript `standard` selects `tsc`, the test
  gate, `prettier`, and `eslint`; for JavaScript it selects the same without `tsc`, which is not
  offered. The test gate fails below 70% line coverage.
- **Gates.** Init also offers `semgrep` (ruleset `p/typescript`, or `p/javascript` for
  JavaScript; needs network access), `gitleaks`, `trivy`, and `zap`. `stryker` and
  `dependency-cruiser` are not offered yet.
- **Probe.** For tool availability, probe only the gates init offers here:

  ```sh
  ward init -C <dir> --dry-run --preset standard --integration none --gate semgrep,gitleaks,trivy
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
- `conflict package.json` with `INCOMPATIBLE <package>` lines: the project's Node or TypeScript
  version is outside the package's supported range. Init applies nothing, and no option overrides
  it. Explain the range and the choices (upgrade Node, pin another TypeScript version, or drop
  that gate). Do not apply.
- `create eslint.config.mjs` (or `.js` with `"type": "module"`): Ward's flat config. For
  TypeScript it enables typescript-eslint's recommended rules. For JavaScript it enables
  `@eslint/js` recommended rules, declares browser and Node globals, parses JSX in `.jsx` files,
  and, without `"type": "module"`, parses `.js` files as CommonJS. `keep .eslintrc.json` means the
  user's config is reused.
- For JavaScript, the `eslint` gate adds `eslint`, `@eslint/js`, and `globals`, and no
  `typescript` or `typescript-eslint`. A plan that adds either for a JavaScript project is wrong:
  stop and report it.
- `modify .gitignore`: a Ward section with `.ward/reports/` and `coverage/`.
- If the install fails on apply, init restores `package.json` and the lock file and writes no
  `ward.toml`. Show the error and the command; do not retry without asking.

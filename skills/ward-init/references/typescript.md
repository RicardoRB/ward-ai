# TypeScript projects

Use this reference when `ward detect` reports `TYPESCRIPT` with `NPM`, `PNPM`, `YARN`, or `BUN`.
Everything in it is read-only. Skip the Java-only steps of the inspection (package layout,
ArchUnit, rule files under `config/`).

## Inspection

- **Language.** `ward detect` reports `TYPESCRIPT` when `tsconfig.json` sits next to `package.json`.
  A `JAVASCRIPT` project cannot be initialized yet: `ward init` fails naming JavaScript and the
  package manager, and changes nothing. Tell the user and stop.
- **Package manager and Node.** `ward detect` prints the manager and the Node major version, read
  from `.nvmrc`, `.node-version`, or `engines.node`, else 24. Read `packageManager` in
  `package.json`: a `yarn@1.x` value means Yarn 1.
- **Declared tools.** Read `dependencies` and `devDependencies` in `package.json`:
  - `vitest` or `jest` decides the test gate (`vitest`, then `jest`). A Bun project with neither
    gets `bun-test`; any other project gets `vitest`, and init adds `vitest` and
    `@vitest/coverage-v8`.
  - `typescript`: note the declared range. Its lower bound is checked against `typescript-eslint`.
  - `eslint`, `prettier`, `typescript-eslint`: declared packages keep their version.
- **Configs.** List `eslint.config.*`, `.eslintrc*`, `.prettierrc*`, `prettier.config.*`, and
  `bunfig.toml` in the project root. A file whose first line is not `// managed-by: ward` or
  `# managed-by: ward` is the user's, and init keeps it.
- **Lock file.** `package-lock.json`, `pnpm-lock.yaml`, `yarn.lock`, `bun.lock`, or `bun.lockb`.
  Init's install updates it, and the generated CI workflow's frozen install needs it committed.

Add a line like this to the inspection summary:

```text
Node: TypeScript, pnpm, Node 22, Vitest declared, typescript ^5.9.3, no ESLint config
```

## Choices

- **Preset.** Only `standard` or a custom selection. `strict` is not available for TypeScript yet,
  and `--preset strict` fails. `standard` selects `tsc`, the test gate, `prettier`, and `eslint`.
  The test gate fails below 70% line coverage.
- **Gates.** Init also offers `semgrep` (ruleset `p/typescript`, needs network access),
  `gitleaks`, `trivy`, and `zap`. `stryker` and `dependency-cruiser` are not offered yet.
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
- `create eslint.config.mjs` (or `.js` with `"type": "module"`): Ward's flat config with
  typescript-eslint's recommended rules. `keep .eslintrc.json` means the user's config is reused.
- `modify .gitignore`: a Ward section with `.ward/reports/` and `coverage/`.
- If the install fails on apply, init restores `package.json` and the lock file and writes no
  `ward.toml`. Show the error and the command; do not retry without asking.

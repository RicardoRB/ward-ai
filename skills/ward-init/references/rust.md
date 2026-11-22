# Rust projects (Cargo)

Use this reference when `ward detect` reports `RUST` with `CARGO`. Everything here is read-only until
the user approves the plan.

## Supported setups

Ward sets up Cargo crates and workspaces. It reads `Cargo.toml` and never runs `cargo`, and it edits
no Cargo file. The gates run the `cargo` that is already installed.

## Inspection

- **Workspace.** A root `Cargo.toml` with `[workspace]` is one project; its `workspace.members` are
  modules, and the gates run over all of them with `--workspace`.
- **Toolchain.** `ward doctor` reports `cargo` as missing when it is not on `PATH`; tell the user to
  install Rust with rustup (https://rustup.rs) before running the gates.

## Gates

- `cargo-clippy`: `cargo clippy --workspace --all-targets -- -D warnings`.
- `cargo-test`: `cargo test --workspace`.
- `coverage`: `cargo llvm-cov --workspace --lcov --output-path .ward/reports/coverage.lcov`, with a
  `minimum` of 80 (85 in `strict`) that Ward checks against the lcov report. Needs `cargo-llvm-cov`
  (`cargo install --locked cargo-llvm-cov`) and `rustup component add llvm-tools-preview`.
- `cargo-mutants` (`strict` only, in `ci`): `cargo mutants --workspace`, `minimum` 60. Needs
  `cargo-mutants` (`cargo install --locked cargo-mutants`). Ward names every surviving mutant.
- `strict` adds `--all-features` to the cargo gates. Ward's layer and cycle rules also run on Rust.
- Unavailable tools are written anyway; tell the user to install them before the gates run.

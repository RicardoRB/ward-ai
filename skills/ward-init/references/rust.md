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
- `strict` adds `--all-features` to both. There is no coverage or mutation gate yet.

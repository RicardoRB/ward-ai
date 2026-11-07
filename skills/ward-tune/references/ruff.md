# Tuning Ruff (Python)

## Where the configuration lives

- **Ward-owned:** `ruff.toml`, written by init with a Ward marker. Standard enables the default
  rule set. Strict adds Ruff's C90 with complexity 10 and the extra rule sets.
- **User-owned:** a `[tool.ruff]` table in `pyproject.toml`, or a `ruff.toml` or `.ruff.toml`
  without the marker. Init reuses it and does not write `ruff.toml`.

## Rules

- Never add `# noqa` comments or `per-file-ignores` to silence a finding. Fix the code, or change a
  rule with a reason written down in the change.
- Formatting is the `ward-format` gate, not Ruff: `ruff format` is not run. Formatting changes are not lint findings.

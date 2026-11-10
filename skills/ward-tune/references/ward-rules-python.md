# Tuning Ward's Python rules

Python lint is Ward's own rules, selected by `ward-rules.toml` at the project root. Init writes
the file with `languages = ["java", "python"]` and the preset (`standard` or `strict`), so the
`ward` check applies the Python rules of that preset. Init does not configure Ruff.

## Where the configuration lives

- **Ward-owned:** `ward-rules.toml`, written by init with a Ward marker. Tune it with the
  `[select]` and `[rules.<language>.<rule-name>]` tables described in the `ward-rules.toml` reference.
- **User-owned:** a `mypy.ini` or a `[tool.mypy]` table is the project's own. Init leaves it
  unchanged. Ward's Python rules do not read it.

## Rules

- Never add `# noqa` comments or `per-file-ignores` to silence a finding. Fix the code, or change a
  rule with a reason written down in the change.
- Tune a rule's `severity`, `exclude-paths` or options in `ward-rules.toml`, and say why in the
  change. Do not drop a rule from `[select]` only to make a finding go away.
- Formatting is the `ward-format` gate, not a lint rule. Formatting changes are not lint findings.

# Tuning Ward's rules

Lint is Ward's own rules for every language: Java, Kotlin, TypeScript, JavaScript, Python and
Swift. `ward-rules.toml` at the project root selects them, and the `ward` check applies them. Init
writes the file with the project's languages and the preset (`standard` or `strict`). There is no
separate lint tool gate to tune, for any language.

## Where the configuration lives

- **Ward-owned:** `ward-rules.toml`, written by init with a Ward marker. Tune it with the
  `[select]` and `[rules.<language>.<rule-name>]` tables described in the `ward-rules.toml` reference.
- **Selection:** `[select]` takes `preset`, `languages`, `categories`, `include` and `exclude`. A
  rule is written `<language>.<rule-name>` there, such as `java.method-length`, while findings and
  `ward rules` print it as `ward:java/method-length`.

## Finding the rule to tune

- `ward rules list --selected --format json` lists the rules this project runs.
- `ward rules list --language <language> --category <category>` narrows the catalog.
- `ward rules show <id> --format json` gives a rule's options, their types and their defaults. Tune
  only options that the rule declares.

## Rules

- Never add a suppression to silence a finding: no `@SuppressWarnings`, `# noqa`,
  `eslint-disable` or `per-file-ignores`. Ward has none of its own. Fix the code, or change a rule
  with a reason written down in the change.
- Tune a rule's `severity`, `exclude-paths` or options in `ward-rules.toml`, and say why in the
  change. Do not drop a rule from `[select]` only to make a finding go away.
- Formatting is the `ward-format` gate, not a lint rule. Formatting changes are not lint findings.

## Python

- Init does not configure Ruff. The Python rules of the preset come from `ward-rules.toml`, like
  those of every other language.
- A `mypy.ini` or a `[tool.mypy]` table is the project's own. Init leaves it unchanged, and Ward's
  Python rules do not read it. See [mypy.md](mypy.md) for the `mypy` gate.

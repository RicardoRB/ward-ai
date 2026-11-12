# Decision to option map

Each decision the user makes becomes exactly one explicit `ward init` option. Before you use an
option, confirm it appears in `ward init --help` for the installed CLI. If it does not, tell the
user the choice needs a newer CLI and leave it out.

The "Consent" column marks options that need a specific yes for that exact effect. Approving the plan
as a whole is not that yes.

| Decision | Option | Values | Consent |
|---|---|---|---|
| Project directory | `-C <dir>` | Directory init starts in: the project root, or a monorepo root together with `--project` | No |
| Projects in a monorepo | `--project <dir>` | Maven, Gradle, TypeScript, or JavaScript project root relative to `-C`. Repeat once per project the user chose | No |
| Preset | `--preset <id>` | `standard` or `strict`. Leave the option out for a custom selection | No |
| Gates | `--gate <id>[,<id>...]` | Gate ids from the gate reference | No |
| Xcode scheme | `--scheme <name>` | An Xcode scheme of a Swift project with several shared schemes. With `--yes`, a project with several schemes needs this option, and the name must be one of them | No |
| Architecture style | `--architecture <style>` | `layered` (default), `hexagonal`, or `clean`. It sets the layer boundaries of `[[checks.ward.layers]]` in `ward.toml`, which init always writes. Only the `strict` preset asks for it, so an interactive run offers the choice there | No |
| Agent integrations | `--integration <id>[,<id>...]` | `none`, or any of `claude-code`, `opencode`, `codex`, `cursor`, `copilot`, `gemini`, `windsurf`, `cline`, `factory-droid`, `amp`, `junie` | No |
| Git quality hooks | `--quality-hooks` | Present to install; leave it out to skip | No |
| CI workflow | `--ci github` | `github`. Leave it out to skip | No |
| Agent rules in `AGENTS.md` | `--agent-rules` | Present to write the section; leave it out to skip | No |
| Remove quality hooks | `--remove-quality-hooks` | Present to remove Ward's hook sections | No |
| Install a Node project's dev dependencies | none: applying a plan with `run <manager> install` runs it | Every `add devDependency` and `run ... install` line in the plan | **Yes, before applying** |
| Replace an existing `ward.toml` | `--overwrite` | Present | **Yes** |
| Accept plugin version updates | `--accept-compatibility-updates` | Present | **Yes, after showing each `(requires approval)` line** |
| Replace differing Turborepo member scripts | `--replace-scripts` | Each `replace <script> script (requires approval)` line in the plan | **Yes, once, after listing each member, script, current and proposed command** |
| Preview only | `--dry-run` | Present for the review step; removed for the apply step | No |

## How presets and gates combine

- `--preset standard` or `--preset strict` selects that preset's gates. Adding `--gate` only **adds**
  gates to the preset's set. It never removes a preset gate.
- To drop a preset gate, use a custom selection: leave out `--preset` and list every gate with
  `--gate`.
- Every run needs at least one of `--preset` or `--gate`. Without either, a non-interactive init exits
  with code `2`.

## Options this skill never uses

- `--yes`: it fills unanswered questions with defaults and approves compatibility updates. Every
  answer must come from the user, so this skill never passes it.
- Piped answers, such as `printf '1\n' | ward init`: the order of questions can change between
  versions. Use explicit options only.

## Examples

Strict preset, hexagonal layer boundaries, Claude Code integration, Git hooks:

```sh
ward init --dry-run --preset strict --architecture hexagonal --integration claude-code --quality-hooks
```

Custom selection without `mutation`:

```sh
ward init --dry-run --gate compile,tests,coverage,secrets --integration none
```

Reconfiguring an existing setup after the user agreed to replace `ward.toml`:

```sh
ward init --dry-run --overwrite --preset standard --integration opencode
```

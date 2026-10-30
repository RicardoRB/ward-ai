# Python projects (uv, Poetry and pip)

Use this reference when `ward detect` reports `PYTHON` with `UV`, `POETRY` or `PIP`. PDM and Hatch
projects are not supported yet: init stops and names the manager, so tell the user and stop.
Everything here is read-only until the user approves the plan.

## Inspection

- **Language and manager.** `pyproject.toml`, `setup.py`, `setup.cfg` or a `requirements*.txt`
  makes a Python project. uv wins when `uv.lock` exists or `[tool.uv]` is present, then Poetry
  (`poetry.lock` or `[tool.poetry]`), then PDM (`pdm.lock`), then Hatch, then pip.
- **Python version.** `.python-version`, then the lower bound of `requires-python`, else 3.14. Init
  refuses a version below 3.11 as `INCOMPATIBLE`.
- **Virtual environment.** pip installs into `.venv`. When `.venv` is missing, init stops and says
  to run `python -m venv .venv`.
- **Own configs.** A `ruff.toml`, `mypy.ini`, `.coveragerc`, or a `[tool.ruff]`, `[tool.mypy]`,
  `[tool.coverage]` table, or a `setup.cfg` or `tox.ini` section, is the project's own. Ward keeps
  it unchanged and puts the coverage threshold on the command instead.

## Choices

| Gate | Runs | Standard |
|---|---|---|
| `ruff-format` | `ruff format --check .` | yes |
| `ruff` | `ruff check . --output-format json` | yes |
| `mypy` | `mypy . -O json` | yes |
| `pytest` | `coverage run -m pytest` | yes |
| `coverage` | `coverage report`, `--fail-under=80` with a user config | yes |
| `pip-audit` | `pip-audit` in `ci` | no, opt-in |

Only `standard` is offered for Python. `--preset strict` is refused with a suggestion to use
`standard`; strict arrives in a later change. Each command runs through the manager: `uv run --group
ward`, `poetry run`, or `.venv/bin/`.

## Plan lines

- `add ruff==0.17.1 to the ward group`: the pinned tool, written to `[dependency-groups] ward` in
  `pyproject.toml`. Without `pyproject.toml`, it goes to `requirements-ward.txt`.
- `run uv sync --group ward`, `poetry lock` then `poetry install --with ward`, or `.venv/bin/python
  -m pip install --group ward` followed by `-e .` when `pyproject.toml` has a `[build-system]`.
- `Created ruff.toml`, `mypy.ini` or `.coveragerc`: Ward's owned configs, written only where the
  project has none.

Init does not touch the project's other `pyproject.toml` lines. When an install fails, init restores
`pyproject.toml`, the lock file and `requirements-ward.txt`, and nothing else changes.

## Compatibility

- Python below 3.11: `INCOMPATIBLE`, stop.
- Poetry below 2.2 cannot read `[dependency-groups]`: stop and ask the user to upgrade Poetry.
- pip below 25.1 in a project with `pyproject.toml`: stop and ask the user to run
  `.venv/bin/python -m pip install -U pip`.

## Agent rules

Init writes one rule line per gate. They forbid `# noqa`, `# type: ignore`, and `per-file-ignores`,
and ask agents not to lower the coverage minimum.

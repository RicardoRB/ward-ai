# Tuning mypy (Python)

## Where the configuration lives

- **Ward-owned:** `mypy.ini`, written by init with a Ward marker. Standard sets the defaults;
  strict turns on `strict = True`. Edit the file, and init keeps the edits when it runs again.
- **User-owned:** a `[mypy]` section in `mypy.ini`, `setup.cfg`, `pyproject.toml` (`[tool.mypy]`), or
  `tox.ini`. Init reuses it and does not write `mypy.ini`.

## Framework plugins

- **FastAPI:** the generated config enables `pydantic.mypy` with `init_typed = True` and
  `warn_required_dynamic_aliases = True`. Without it, mypy reports false errors on pydantic models.
- **Django:** the generated config uses `mypy_django_plugin.main` with the project's settings module.
  A user-owned config without this plugin gets a warning from init, because mypy reports false
  errors on models.

## Rules

- Never add `# type: ignore` to silence a finding. Fix the annotation, or the plugin setting that
  causes it.
- Keep the strict settings unless the user asks for a weaker preset. Each relaxed option is a
  reason to write down in the change.

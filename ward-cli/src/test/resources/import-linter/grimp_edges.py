"""Prints the direct imports grimp builds for a fixture as sorted file pairs, for the smoke test.

Arguments: the fixture directory, the comma-separated root packages and the comma-separated source
directories, relative to the fixture, that go on sys.path.
"""
import json
import os
import sys

import grimp

fixture, roots, sources = sys.argv[1], sys.argv[2].split(","), sys.argv[3].split(",")
fixture = os.path.abspath(fixture)
for source in sources:
    sys.path.insert(0, os.path.join(fixture, source))


def file_of(module):
    parts = module.split(".")
    for source in sources:
        base = os.path.join(fixture, source, *parts)
        if os.path.isfile(base + ".py"):
            return os.path.relpath(base + ".py", fixture)
        if os.path.isfile(os.path.join(base, "__init__.py")):
            return os.path.relpath(os.path.join(base, "__init__.py"), fixture)
    raise SystemExit("no file for module " + module)


graph = grimp.build_graph(*roots)
pairs = set()
for module in graph.modules:
    for imported in graph.find_modules_directly_imported_by(module):
        pairs.add((file_of(module), file_of(imported)))
print(json.dumps({"generator": "import-linter 2.15 / grimp 3.18", "roots": roots,
                  "edges": [list(pair) for pair in sorted(pairs)]}, indent=2))

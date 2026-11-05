"""Module docstring."""

import os


class Tool:
    """A tool."""

    def run(self, value: int = 1) -> int:
        result = os.path.join("a", "b")  # keep
        return value * 2

"""Loads the module without calling it."""

from app import untested


def test_module_is_loaded() -> None:
    """The module exists, and its function stays uncalled."""
    assert callable(untested.total)

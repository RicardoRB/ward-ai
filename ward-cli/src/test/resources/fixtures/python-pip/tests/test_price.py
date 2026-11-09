"""Tests for shop pricing."""

from shop import price


def test_price() -> None:
    """price doubles its argument."""
    assert price(2) == 2 * 2

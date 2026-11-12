"""Fixture code for the Python matrix."""

from shop import price

EXPECTED_PRICE = 4


def test_price() -> None:
    """Doubling two gives four."""
    assert price(2) == EXPECTED_PRICE


def test_negative_quantity_is_free() -> None:
    """A negative quantity costs nothing."""
    assert price(-1) == 0

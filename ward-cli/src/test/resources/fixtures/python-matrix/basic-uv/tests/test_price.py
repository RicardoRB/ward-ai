from shop import price

EXPECTED_PRICE = 4


def test_price() -> None:
    """Doubling two gives four."""
    assert price(2) == EXPECTED_PRICE

from orders.pricing import order_total

BULK_QUANTITY = 11
BULK_TOTAL = 20
SMALL_TOTAL = 4


def test_bulk_order_gets_one_free_unit() -> None:
    """Eleven units at price 2 cost 20, one unit free."""
    assert order_total(BULK_QUANTITY, 2) == BULK_TOTAL


def test_small_order_pays_every_unit() -> None:
    """Two units at price 2 cost 4."""
    assert order_total(2, 2) == SMALL_TOTAL

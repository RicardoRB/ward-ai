from orders.pricing import order_total


def test_bulk_order_gets_one_free_unit() -> None:
    assert order_total(11, 2) == 20


def test_small_order_pays_every_unit() -> None:
    assert order_total(2, 2) == 4

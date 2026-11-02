from orders.pricing import order_total


def test_violation() -> None:
    assert order_total(2, 2) == 5

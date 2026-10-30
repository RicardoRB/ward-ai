from app.counting import order_count


def test_empty_list_has_no_orders() -> None:
    assert order_count([]) == 0


def test_orders_are_counted() -> None:
    assert order_count([1, 2]) == 2

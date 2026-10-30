from shop import price


def test_price() -> None:
    assert price(2) == 4

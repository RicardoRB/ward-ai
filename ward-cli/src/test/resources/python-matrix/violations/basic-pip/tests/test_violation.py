from shop import price


def test_violation() -> None:
    assert price(2) == 5

from shop.pricing import total


def test_violation() -> None:
    assert total(3, 2) == 7

"""Fixture code for the Python matrix."""

from flask.testing import FlaskClient

from shop.pricing import total

QUANTITY = 3
EXPECTED_TOTAL = 6


def test_total_multiplies_quantity_by_price() -> None:
    """Three units at price 2 cost 6."""
    assert total(QUANTITY, 2) == EXPECTED_TOTAL


def test_no_quantity_costs_nothing() -> None:
    """Zero units cost nothing."""
    assert total(0, 2) == 0


def test_login_route(client: FlaskClient) -> None:
    """The login route answers."""
    assert client.get("/login").data == b"login"


def test_price_route(client: FlaskClient) -> None:
    """The price route answers."""
    assert client.get("/price").data == b"2"

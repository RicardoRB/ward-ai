from flask.testing import FlaskClient

from shop.pricing import total


def test_total_multiplies_quantity_by_price() -> None:
    assert total(3, 2) == 6


def test_login_route(client: FlaskClient) -> None:
    assert client.get("/login").data == b"login"


def test_price_route(client: FlaskClient) -> None:
    assert client.get("/price").data == b"2"

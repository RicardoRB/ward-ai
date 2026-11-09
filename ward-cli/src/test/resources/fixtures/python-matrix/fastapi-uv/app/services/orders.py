"""Fixture code for the Python matrix."""

from app.counting import order_count
from app.repositories.orders import list_orders


def count_orders() -> int:
    """Counts the stored orders."""
    return order_count(list_orders())

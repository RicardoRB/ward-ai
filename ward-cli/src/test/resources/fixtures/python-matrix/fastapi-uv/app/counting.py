"""Fixture code for the Python matrix."""


def order_count(orders: list[int]) -> int:
    """Counts the orders, zero for an empty list."""
    if not orders:
        return 0
    return len(orders)

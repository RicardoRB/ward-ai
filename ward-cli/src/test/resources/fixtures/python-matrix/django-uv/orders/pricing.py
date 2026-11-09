"""Fixture code for the Python matrix."""

BULK_THRESHOLD = 10


def order_total(quantity: int, unit_price: int) -> int:
    """Charges every unit, with one unit free for a bulk order."""
    if quantity > BULK_THRESHOLD:
        return quantity * unit_price - unit_price
    return quantity * unit_price

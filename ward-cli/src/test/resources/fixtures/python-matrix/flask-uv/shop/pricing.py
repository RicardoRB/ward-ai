"""Fixture code for the Python matrix."""


def total(quantity: int, unit_price: int) -> int:
    """Multiplies the quantity by the unit price; no quantity costs nothing."""
    if quantity <= 0:
        return 0
    return quantity * unit_price

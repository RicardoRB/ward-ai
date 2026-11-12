"""Fixture code for the Python matrix."""


def price(qty: int) -> int:
    """Doubles the quantity; a negative quantity costs nothing."""
    if qty < 0:
        return 0
    return qty * 2

"""Fixture code for the Python matrix."""

from billing.models import Invoice

INVOICE_TOTAL = 5


def test_invoice_keeps_its_total() -> None:
    """An invoice keeps the total it was made with."""
    assert Invoice(total=INVOICE_TOTAL).total == INVOICE_TOTAL

from billing.invoice import Invoice


def checkout() -> int:
    return Invoice().total()

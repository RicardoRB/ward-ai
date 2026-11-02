def order_total(quantity: int, unit_price: int) -> int:
    if quantity > 10:
        return quantity * unit_price - unit_price
    return quantity * unit_price

from app.counting import order_count


def count_orders(orders: list[int]) -> int:
    """Counts the orders through the counting module."""
    return order_count(orders)

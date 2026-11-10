from orders import Order


def compute(value):
    total = value + 1
    Order.objects.create(quantity=total)
    Order.objects.update(quantity=2)
    return total

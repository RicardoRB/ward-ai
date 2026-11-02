import pytest

from orders.models import Order


@pytest.mark.django_db
def test_order_is_saved() -> None:
    Order.objects.create(quantity=2)
    assert Order.objects.count() == 1

from fastapi import APIRouter
from pydantic import BaseModel

from app.repositories.orders import list_orders
from app.services.orders import count_orders

router = APIRouter()


class Order(BaseModel):
    """An order in a request."""
    quantity: int


@router.get("/orders/count")
def count() -> dict[str, int]:
    """Counts the orders."""
    return {"count": count_orders(list_orders())}


@router.post("/orders")
def create(order: Order) -> Order:
    """Echoes an order back to the caller."""
    return order

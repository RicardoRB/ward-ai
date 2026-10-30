from fastapi import APIRouter
from pydantic import BaseModel

from app.services.orders import count_orders

router = APIRouter()


class Order(BaseModel):
    quantity: int


@router.get("/orders/count")
def count() -> dict[str, int]:
    return {"count": count_orders([])}

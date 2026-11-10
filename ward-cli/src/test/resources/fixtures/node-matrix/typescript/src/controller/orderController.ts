import { orderTotal, placeOrder } from "../service/orderService";

export function postOrder(id: string, total: number): string {
  return `placed ${placeOrder(id, total).id}`;
}

export function getTotal(id: string): string {
  return `total ${String(orderTotal(id))}`;
}

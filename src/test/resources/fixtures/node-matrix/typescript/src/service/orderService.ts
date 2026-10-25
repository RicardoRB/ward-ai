import { findById, type Order, save } from "../repository/orderRepository";

export function placeOrder(id: string, total: number): Order {
  if (total <= 0) {
    throw new Error("total must be positive");
  }
  const order = { id, total };
  save(order);
  return order;
}

export function orderTotal(id: string): number {
  const order = findById(id);
  if (order === undefined) {
    throw new Error(`unknown order ${id}`);
  }
  return order.total;
}

import { findById, save } from "../repository/orderRepository.js";

export function placeOrder(id, total) {
  if (total <= 0) {
    throw new Error("total must be positive");
  }
  const order = { id, total };
  save(order);
  return order;
}

export function orderTotal(id) {
  const order = findById(id);
  if (order === undefined) {
    throw new Error(`unknown order ${id}`);
  }
  return order.total;
}

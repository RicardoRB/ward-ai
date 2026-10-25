import { orderTotal, placeOrder } from "../service/orderService.js";

export function postOrder(id, total) {
  return `placed ${placeOrder(id, total).id}`;
}

export function getTotal(id) {
  return `total ${orderTotal(id)}`;
}

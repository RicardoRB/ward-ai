const orders = new Map();

export function save(order) {
  orders.set(order.id, order);
}

export function findById(id) {
  return orders.get(id);
}

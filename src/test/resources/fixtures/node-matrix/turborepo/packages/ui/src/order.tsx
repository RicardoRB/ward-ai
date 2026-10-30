export interface Order {
  readonly id: string;
  readonly total: number;
}

const orders = new Map<string, Order>();

export function save(order: Order): void {
  orders.set(order.id, order);
}

export function placeOrder(id: string, total: number): Order {
  if (total <= 0) {
    throw new Error("total must be positive");
  }
  const order = { id, total };
  save(order);
  return order;
}

export function orderTotal(id: string): number {
  const order = orders.get(id);
  if (order === undefined) {
    throw new Error(`unknown order ${id}`);
  }
  return order.total;
}

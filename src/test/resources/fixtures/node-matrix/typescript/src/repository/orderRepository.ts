export interface Order {
  readonly id: string;
  readonly total: number;
}

const orders = new Map<string, Order>();

export function save(order: Order): void {
  orders.set(order.id, order);
}

export function findById(id: string): Order | undefined {
  return orders.get(id);
}

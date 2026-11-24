export interface Repo<
  TKey extends string,
  TValue = {},
> {
  get(key: TKey): TValue;
}
class Box<
  A,
  B = A,
> {}
function pick<
  T,
  K extends keyof T,
>(value: T, key: K): T[K] {
  return value[key];
}
class Holder {
  read<
    Item,
  >(item: Item): Item {
    return item;
  }
}

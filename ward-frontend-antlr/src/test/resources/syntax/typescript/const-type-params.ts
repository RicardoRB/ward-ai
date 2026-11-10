function first<const T extends readonly unknown[]>(xs: T): T[0] {
  return xs[0];
}

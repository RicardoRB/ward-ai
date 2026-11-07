function f(a: unknown): number {
  if (typeof a === "string") {
    return 1;
  }
  if ("object" !== typeof a) {
    return 2;
  }
  return typeof a === "undefined" ? 3 : 4;
}

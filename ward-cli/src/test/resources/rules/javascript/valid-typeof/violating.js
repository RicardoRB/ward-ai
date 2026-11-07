function f(a) {
  if (typeof a === "strnig") {
    return 1;
  }
  if ("nubmer" !== typeof a) {
    return 2;
  }
  return typeof a === "string" ? 3 : 4;
}

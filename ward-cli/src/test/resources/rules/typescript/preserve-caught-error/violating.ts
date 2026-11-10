try {
  f();
} catch (e) {
  throw new Error("x");
}
try {
  f();
} catch ({ m }) {
  throw new Error("y");
}
try {
  f();
} catch (e) {
  throw new TypeError("z", { cause: 1 });
}

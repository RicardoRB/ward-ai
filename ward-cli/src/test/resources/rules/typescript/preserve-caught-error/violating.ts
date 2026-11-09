try {
  f();
} catch (e) {
  throw new Error("x");
}
try {
  f();
} catch (e) {
  throw new TypeError("z", { cause: 1 });
}

try {
  f();
} catch (e) {
  throw new Error("x", { cause: e });
}
try {
  f();
} catch (e) {
  throw e;
}
try {
  f();
} catch (e) {
  throw new AggregateError([e], "m", { cause: e });
}

outer: for (const x of xs) {
  run(x);
}
inner: for (const y of ys) {
  break inner;
}

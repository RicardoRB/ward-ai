function run(a) {
  if (a) {
    throw new Error("bad");
  }
  done();
}

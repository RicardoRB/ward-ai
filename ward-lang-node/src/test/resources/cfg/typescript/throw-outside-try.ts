function run(a: boolean): void {
  if (a) {
    throw new Error("bad");
  }
  done();
}

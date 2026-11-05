function run(obj: Record<string, number>): void {
  for (const key in obj) {
    work(key);
  }
  done();
}

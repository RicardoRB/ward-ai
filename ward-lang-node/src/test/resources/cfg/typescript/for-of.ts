function run(items: string[]): void {
  for (const item of items) {
    work(item);
  }
  done();
}

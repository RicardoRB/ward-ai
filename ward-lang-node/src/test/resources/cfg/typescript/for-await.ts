async function run(items: AsyncIterable<number>): Promise<void> {
  for await (const item of items) {
    work(item);
  }
  done();
}

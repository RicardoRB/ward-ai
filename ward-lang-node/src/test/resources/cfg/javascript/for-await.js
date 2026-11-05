async function run(items) {
  for await (const item of items) {
    work(item);
  }
  done();
}

function run(obj) {
  for (const key in obj) {
    work(key);
  }
  done();
}

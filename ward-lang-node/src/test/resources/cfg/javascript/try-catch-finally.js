function run() {
  try {
    risky();
  } catch (e) {
    recover();
  } finally {
    cleanup();
  }
  done();
}

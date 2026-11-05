function run(): void {
  try {
    risky();
  } catch (e) {
    recover();
  } finally {
    cleanup();
  }
  done();
}

function run(a) {
  try {
    if (a) {
      return 1;
    }
    work();
  } finally {
    cleanup();
  }
  return 2;
}

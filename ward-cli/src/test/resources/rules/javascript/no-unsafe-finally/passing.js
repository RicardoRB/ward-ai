function f() {
  try {
    return 1;
  } finally {
    cleanup();
  }
}
function cleanup() {}

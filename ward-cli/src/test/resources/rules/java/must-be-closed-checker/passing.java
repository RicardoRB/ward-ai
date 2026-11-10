final class T {
  Res run(Api api) {
    // ok
    try (Res r = api.open()) {
      return null;
    }
  }

  Res other(Api api) {
    return api.open();
  }
}
